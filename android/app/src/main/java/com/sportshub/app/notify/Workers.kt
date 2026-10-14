package com.sportshub.app.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sportshub.app.SportsApp
import com.sportshub.app.data.Fixture
import com.sportshub.app.data.FollowedTeam
import com.sportshub.app.data.Region
import com.sportshub.app.data.WatchRights
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.util.concurrent.TimeUnit

private val ONLINE = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

object Background {
    /** Android won't run periodic work more often than every 15 minutes, so that is the resolution of score alerts. */
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<MatchCheckWorker>(15, TimeUnit.MINUTES).setConstraints(ONLINE).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("match-check", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Run a check right away, e.g. after following a team, so its reminders are scheduled without waiting. */
    fun checkNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<MatchCheckWorker>().setConstraints(ONLINE).build()
        WorkManager.getInstance(context).enqueueUniqueWork("match-check-now", ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelAll(context: Context) = WorkManager.getInstance(context).cancelAllWork()
}

/** Looks at every followed team's fixtures, sends alerts for what changed, and schedules kickoff reminders. */
class MatchCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as SportsApp
        val prefs = NotificationPrefs(applicationContext)
        if (!prefs.anyEnabled || !Notifier.canPost(applicationContext)) return Result.success()
        val follows = app.repo.follows.teams.first().filter { it.key !in prefs.muted }
        if (follows.isEmpty()) return Result.success()

        var state = prefs.state()
        val sent = HashSet<String>()
        var failures = 0
        val now = System.currentTimeMillis()
        for (team in follows) {
            val fixtures = try {
                app.repo.fixtures(team.leagueId, team.teamId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures++
                continue
            }
            val window = fixtures.filter { inWindow(it, now) }
            val (alerts, newState) = MatchWatcher.diff(state, window)
            state = newState
            for (alert in alerts) {
                // Two followed teams in the same match must not produce two notifications.
                if (prefs.allows(alert.kind) && sent.add("${alert.fixture.id}:${alert.kind}")) {
                    Notifier.postAlert(applicationContext, alert, team.leagueId, team.teamId)
                }
            }
            if (prefs.kickoff) {
                for ((fixture, delay) in Reminders.due(window, now, prefs.reminderMinutes)) {
                    KickoffScheduler.schedule(applicationContext, team, fixture, delay, prefs.reminderMinutes)
                }
            }
        }
        prefs.saveState(state)
        return if (failures == follows.size) Result.retry() else Result.success()
    }

    private fun inWindow(f: Fixture, nowMs: Long): Boolean {
        val t = runCatching { Instant.parse(f.date).toEpochMilli() }.getOrNull() ?: return false
        return t in (nowMs - 2 * 24 * 3600_000L)..(nowMs + Reminders.HORIZON_MS)
    }
}

object KickoffScheduler {
    fun schedule(context: Context, team: FollowedTeam, fixture: Fixture, delayMs: Long, leadMinutes: Int) {
        val data = Data.Builder()
            .putString("fixtureId", fixture.id)
            .putString("title", fixture.home?.let { h -> fixture.away?.let { a -> "${h.name} v ${a.name}" } } ?: fixture.title)
            .putString("leagueId", fixture.leagueId)
            .putString("leagueName", fixture.leagueName)
            .putString("followLeague", team.leagueId)
            .putString("followTeam", team.teamId)
            .putInt("lead", leadMinutes)
            .build()
        val request = OneTimeWorkRequestBuilder<KickoffWorker>().setInitialDelay(delayMs, TimeUnit.MILLISECONDS).setInputData(data).build()
        // REPLACE keeps the reminder correct if a fixture is rescheduled between checks.
        WorkManager.getInstance(context).enqueueUniqueWork("kickoff-${fixture.id}-${team.key}", ExistingWorkPolicy.REPLACE, request)
    }
}

class KickoffWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val d = inputData
        val prefs = NotificationPrefs(applicationContext)
        val followKey = "${d.getString("followLeague")}:${d.getString("followTeam")}"
        if (!prefs.kickoff || followKey in prefs.muted) return Result.success()

        val fixture = Fixture(
            id = d.getString("fixtureId").orEmpty(), leagueId = d.getString("leagueId").orEmpty(),
            leagueName = d.getString("leagueName").orEmpty(), date = "", status = "scheduled", title = d.getString("title").orEmpty(),
        )
        val region = Region.current(applicationContext)
        val providers = WatchRights.forRegion(region, fixture, emptyList(), Region.countryName(region))
            .options.filter { it.confirmed }.map { it.provider }
        val lead = d.getInt("lead", 15)
        val text = buildString {
            append(fixture.leagueName)
            if (providers.isNotEmpty()) append(" · Watch on ${providers.joinToString(" / ")}")
        }
        Notifier.postReminder(
            applicationContext, fixture.id, "${fixture.title} starts in $lead min", text,
            d.getString("followLeague").orEmpty(), d.getString("followTeam").orEmpty(),
        )
        return Result.success()
    }
}
