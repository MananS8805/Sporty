package com.sportshub.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sportshub.app.MainActivity
import com.sportshub.app.R
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** User choices for notifications, kept in plain SharedPreferences so background workers can read them. */
class NotificationPrefs(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("notify", Context.MODE_PRIVATE)
    private val stateSerializer = MapSerializer(String.serializer(), String.serializer())

    var kickoff: Boolean
        get() = prefs.getBoolean("kickoff", true)
        set(v) = prefs.edit().putBoolean("kickoff", v).apply()
    var scores: Boolean
        get() = prefs.getBoolean("scores", true)
        set(v) = prefs.edit().putBoolean("scores", v).apply()
    var results: Boolean
        get() = prefs.getBoolean("results", true)
        set(v) = prefs.edit().putBoolean("results", v).apply()
    var reminderMinutes: Int
        get() = prefs.getInt("reminder_minutes", 15)
        set(v) = prefs.edit().putInt("reminder_minutes", v).apply()

    val anyEnabled get() = kickoff || scores || results

    /** Followed-team keys the user has silenced. */
    var muted: Set<String>
        get() = prefs.getStringSet("muted", emptySet()).orEmpty()
        set(v) = prefs.edit().putStringSet("muted", v).apply()

    fun state(): Map<String, String> =
        prefs.getString("state", null)?.let { runCatching { Json.decodeFromString(stateSerializer, it) }.getOrNull() } ?: emptyMap()

    fun saveState(state: Map<String, String>) = prefs.edit().putString("state", Json.encodeToString(stateSerializer, state)).apply()

    fun allows(kind: AlertKind) = when (kind) {
        AlertKind.Kickoff -> kickoff
        AlertKind.Score -> scores
        AlertKind.FullTime -> results
    }
}

object Notifier {
    const val EXTRA_LEAGUE = "open_league"
    const val EXTRA_TEAM = "open_team"
    private const val CH_REMINDER = "kickoff"
    private const val CH_LIVE = "live"
    private const val CH_RESULTS = "results"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_REMINDER, "Kickoff reminders", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "A heads-up shortly before your teams play" })
        nm.createNotificationChannel(NotificationChannel(CH_LIVE, "Live scores", NotificationManager.IMPORTANCE_HIGH)
            .apply { description = "Kickoff and goal updates for your teams" })
        nm.createNotificationChannel(NotificationChannel(CH_RESULTS, "Full-time results", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "Final scores when your teams finish" })
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun channelFor(kind: AlertKind) = when (kind) {
        AlertKind.Kickoff, AlertKind.Score -> CH_LIVE
        AlertKind.FullTime -> CH_RESULTS
    }

    fun postAlert(context: Context, alert: Alert, leagueId: String, teamId: String) =
        post(context, channelFor(alert.kind), alert.fixture.id.hashCode() * 10 + alert.kind.ordinal, alert.title, alert.text, leagueId, teamId)

    fun postReminder(context: Context, fixtureId: String, title: String, text: String, leagueId: String, teamId: String) =
        post(context, CH_REMINDER, fixtureId.hashCode() * 10 + 9, title, text, leagueId, teamId)

    private fun post(context: Context, channel: String, id: Int, title: String, text: String, leagueId: String, teamId: String) {
        if (!canPost(context)) return
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_LEAGUE, leagueId).putExtra(EXTRA_TEAM, teamId)
        val pending = PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_star)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(if (channel == CH_LIVE) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) } // permission can be revoked at any time
    }
}
