package com.sportshub.app.notify

import com.sportshub.app.data.Fixture

enum class AlertKind { Kickoff, Score, FullTime }

data class Alert(val kind: AlertKind, val fixture: Fixture, val title: String, val text: String)

/**
 * Decides what is worth a notification by comparing what we saw last time with what we see now.
 * Pure functions, so the rules are unit-tested without Android.
 */
object MatchWatcher {
    private const val MAX_STATES = 600

    fun stateOf(f: Fixture) = "${f.status}|${f.homeScore.orEmpty()}-${f.awayScore.orEmpty()}"

    private fun status(state: String) = state.substringBefore('|')

    /** Football-style scoreboards have plain integer scores; cricket's "186/5" and F1's podiums do not. */
    private fun intScores(f: Fixture) = f.homeScore?.toIntOrNull() != null && f.awayScore?.toIntOrNull() != null

    private fun teams(f: Fixture) = if (f.home != null && f.away != null) "${f.home.name} v ${f.away.name}" else f.title

    /**
     * Returns alerts for transitions since [previous] and the updated state map. A fixture seen for the
     * first time never alerts (so following a team doesn't dump old results on you).
     */
    fun diff(previous: Map<String, String>, fixtures: List<Fixture>): Pair<List<Alert>, Map<String, String>> {
        val alerts = mutableListOf<Alert>()
        for (f in fixtures) {
            val before = previous[f.id] ?: continue
            val now = stateOf(f)
            if (before == now) continue
            val was = status(before)
            when {
                was == "scheduled" && f.status == "live" ->
                    alerts += Alert(AlertKind.Kickoff, f, if (f.home != null) "Kickoff: ${teams(f)}" else "${f.title} is under way", f.leagueName)
                (was == "scheduled" || was == "live") && f.status == "final" ->
                    alerts += Alert(AlertKind.FullTime, f, "Full time: ${teams(f)}", resultText(f))
                was == "live" && f.status == "live" && intScores(f) ->
                    alerts += Alert(AlertKind.Score, f, "Goal! ${f.home?.name} ${f.homeScore}–${f.awayScore} ${f.away?.name}", f.statusDetail ?: f.leagueName)
            }
        }
        val merged = LinkedHashMap(previous)
        fixtures.forEach { merged[it.id] = stateOf(it) }
        // Keep the map from growing forever: drop the oldest entries first.
        val trimmed = if (merged.size > MAX_STATES) merged.entries.toList().takeLast(MAX_STATES).associate { it.key to it.value } else merged
        return alerts to trimmed
    }

    private fun resultText(f: Fixture): String = when {
        intScores(f) -> "${f.home?.name} ${f.homeScore}–${f.awayScore} ${f.away?.name}"
        f.summary != null -> f.summary
        else -> f.statusDetail ?: f.leagueName
    }
}

/** When to remind about upcoming kickoffs. */
object Reminders {
    const val HORIZON_MS = 7L * 24 * 3600 * 1000

    /** Returns (fixture, delay in ms from now) for each scheduled fixture whose reminder time is still ahead. */
    fun due(fixtures: List<Fixture>, nowMs: Long, leadMinutes: Int): List<Pair<Fixture, Long>> =
        fixtures.filter { it.status == "scheduled" }.mapNotNull { f ->
            val kickoff = runCatching { java.time.Instant.parse(f.date).toEpochMilli() }.getOrNull() ?: return@mapNotNull null
            val delay = kickoff - leadMinutes * 60_000L - nowMs
            if (delay > 0 && kickoff - nowMs <= HORIZON_MS) f to delay else null
        }
}
