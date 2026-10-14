package com.sportshub.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sportshub.app.data.Region
import com.sportshub.app.data.WatchRights
import com.sportshub.app.notify.Background
import com.sportshub.app.notify.Notifier
import com.sportshub.app.notify.NotificationPrefs

private val REGIONS = listOf("IN", "US", "GB", "AU", "CA", "AE", "SG", "DE", "NZ", "ZA")
private val LEADS = listOf(5, 15, 30, 60)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val prefs = remember { NotificationPrefs(context) }
    var kickoff by remember { mutableStateOf(prefs.kickoff) }
    var scores by remember { mutableStateOf(prefs.scores) }
    var results by remember { mutableStateOf(prefs.results) }
    var lead by remember { mutableIntStateOf(prefs.reminderMinutes) }
    var region by remember { mutableStateOf(Region.current(context)) }
    var override by remember { mutableStateOf(context.getSharedPreferences("settings", 0).getString("region_override", null) != null) }
    val askPermission = rememberNotificationPermission()
    var canPost by remember { mutableStateOf(Notifier.canPost(context)) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(start = 10.dp, end = 20.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            }
            Text("Settings", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.headlineSmall)
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Section("Notifications") {
                    if (!canPost) {
                        Text("Notifications are off for this app.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { askPermission(); canPost = Notifier.canPost(context) }) { Text("Allow notifications") }
                        HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    SettingSwitch("Kickoff reminders", "A heads-up before your teams play", kickoff) { kickoff = it; prefs.kickoff = it; Background.checkNow(context) }
                    SettingSwitch("Goal updates", "When a goal goes in during a live match", scores) { scores = it; prefs.scores = it }
                    SettingSwitch("Full-time results", "The final score when a match ends", results) { results = it; prefs.results = it }
                    Text("Remind me", Modifier.padding(top = 12.dp, bottom = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LEADS.forEach { m ->
                            FilterChip(selected = lead == m, onClick = { lead = m; prefs.reminderMinutes = m; Background.checkNow(context) }, label = { Text("$m min before") }, shape = CircleShape)
                        }
                    }
                    Text(
                        "Android checks for updates about every 15 minutes, so goal alerts can arrive a little late. Use the Alerts switch on My teams to mute a team.",
                        Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Section("Where to watch") {
                    Text(
                        "Showing broadcasters for ${Region.countryName(region)}" + if (override) "" else " (from your phone)",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = !override, shape = CircleShape, label = { Text("Auto") },
                            onClick = { Region.override(context, null); override = false; region = Region.current(context) },
                        )
                        REGIONS.forEach { code ->
                            FilterChip(
                                selected = override && region == code, shape = CircleShape, label = { Text(code) },
                                onClick = { Region.override(context, code); override = true; region = code },
                            )
                        }
                    }
                    Text(
                        "Detailed broadcaster lists exist for India only. Other regions fall back to what ESPN lists, plus a search link.",
                        Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Section("About the data") {
                    Note("Fixtures, tables, squads and news come from ESPN's public (unofficial) feeds, Jolpica for Formula 1 and TheSportsDB as a fallback.")
                    Note("Win forecasts are bookmaker odds (via ESPN) with the bookmaker's margin removed. Cricket and F1 have no free expert forecast, so none is shown.")
                    Note("India broadcast rights were last checked on ${WatchRights.VERIFIED_ON} and can change each season.")
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title.uppercase(), Modifier.padding(start = 6.dp, bottom = 8.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        GlassCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { content() } }
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Note(text: String) =
    Text(text, Modifier.padding(vertical = 5.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
