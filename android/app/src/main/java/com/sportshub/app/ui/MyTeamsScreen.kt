package com.sportshub.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sportshub.app.data.FollowedTeam
import com.sportshub.app.notify.NotificationPrefs
import kotlinx.coroutines.launch

@Composable
fun MyTeamsScreen(onAddTeam: () -> Unit, onOpenTeam: (FollowedTeam) -> Unit) {
    val repo = rememberRepo()
    val follows by repo.follows.teams.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current.applicationContext
    val prefs = remember { NotificationPrefs(context) }
    var muted by remember { mutableStateOf(prefs.muted) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("My teams", Modifier.weight(1f), style = MaterialTheme.typography.displaySmall)
            TextButton(onClick = onAddTeam) { Text("Add") }
        }
        if (follows.isEmpty()) {
            EmptyState("No teams yet", "Find a league and tap the star next to your team.", "Browse leagues", onAddTeam)
        } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 14.dp, bottom = 120.dp)) {
            items(follows, key = { it.key }) { t ->
                val color = parseHex(t.color) ?: MaterialTheme.colorScheme.primary
                GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).animateItem(), onClick = { onOpenTeam(t) }) {
                    Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Crest(t.logo, t.name, Modifier.sharedElementIfAvailable(crestKey(t.leagueId, t.teamId)), size = 48.dp, ring = color)
                        Column(Modifier.weight(1f)) {
                            Text(t.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(t.leagueName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Switch(
                                checked = t.key !in muted,
                                onCheckedChange = { on ->
                                    muted = if (on) muted - t.key else muted + t.key
                                    prefs.muted = muted
                                },
                            )
                            Text("Alerts", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { scope.launch { repo.unfollow(t.key) } }) {
                            Icon(Icons.Default.Close, contentDescription = "Unfollow ${t.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
