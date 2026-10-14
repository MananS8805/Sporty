package com.sportshub.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sportshub.app.data.Player
import com.sportshub.app.data.Team

/** Section a player belongs to: football positions, cricket roles, or F1 drivers. */
fun squadGroup(p: Player, cricket: Boolean): String {
    val pos = p.position?.lowercase().orEmpty()
    if (cricket) return when {
        p.keeper || "keeper" in pos -> "Wicketkeepers"
        "all" in pos -> "All-rounders"
        "bowl" in pos || "spin" in pos || "pace" in pos -> "Bowlers"
        "bat" in pos -> "Batters"
        else -> "Players"
    }
    return when {
        "goalkeeper" in pos -> "Goalkeepers"
        "defender" in pos -> "Defenders"
        "midfield" in pos -> "Midfielders"
        "forward" in pos || "attacker" in pos || "striker" in pos -> "Forwards"
        "driver" in pos -> "Drivers"
        else -> p.position?.replaceFirstChar { it.uppercase() } ?: "Players"
    }
}

private val GROUP_ORDER = listOf("Goalkeepers", "Defenders", "Midfielders", "Forwards", "Drivers", "Batters", "Wicketkeepers", "All-rounders", "Bowlers")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SquadScreen(leagueId: String, teamId: String, onBack: () -> Unit) {
    val teamVm = loadViewModel("teamhead:$leagueId:$teamId") { repo -> repo.teams(leagueId).firstOrNull { it.id == teamId } }
    val squadVm = loadViewModel("squad:$leagueId:$teamId") { it.squad(leagueId, teamId) }
    val team: Team? = (teamVm.state.collectAsStateWithLifecycle().value as? Load.Ready)?.data
    val state by squadVm.state.collectAsStateWithLifecycle()
    val cricket = leagueId.startsWith("cricket:") || leagueId == "national:cricket"

    TeamTheme(parseHex(team?.color)) {
        Box(Modifier.fillMaxSize()) {
            when (val s = state) {
                Load.Loading -> Column(Modifier.statusBarsPadding().padding(top = 70.dp, start = 16.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(7) { Box(Modifier.fillMaxWidth().height(68.dp).shimmer(MaterialTheme.shapes.large)) }
                }
                is Load.Error -> ErrorBox(s.message, squadVm::reload)
                is Load.Ready -> {
                    val squad = s.data
                    val groups = squad.players.groupBy { squadGroup(it, cricket) }
                        .toList().sortedBy { (name, _) -> GROUP_ORDER.indexOf(name).let { if (it < 0) 99 else it } }
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
                        item(key = "title") {
                            Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 74.dp, bottom = 8.dp)) {
                                Text(team?.name ?: "Squad", style = MaterialTheme.typography.displaySmall)
                                Text(
                                    if (squad.players.isEmpty()) "Squad" else "${squad.players.size} players",
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                squad.note?.let {
                                    Text(it, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (squad.players.isEmpty()) item(key = "empty") {
                            Text("Nothing to show yet.", Modifier.padding(20.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        var rank = 0
                        groups.forEach { (name, players) ->
                            stickyHeader(key = "g-$name") {
                                Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                                    Text("$name · ${players.size}", Modifier.padding(horizontal = 20.dp, vertical = 10.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            items(players, key = { "p-${it.id}" }) { player -> PlayerCard(player, Modifier.entrance(rank++)) }
                        }
                    }
                }
            }
            Box(
                Modifier.align(Alignment.TopStart).statusBarsPadding().padding(10.dp).size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
        }
    }
}

@Composable
private fun PlayerCard(p: Player, modifier: Modifier = Modifier) {
    GlassCard(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PlayerAvatar(p, 46.dp)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (p.captain) Badge("C")
                    if (p.keeper) Badge("WK")
                }
                val meta = listOfNotNull(p.position, p.country, p.age?.let { "$it yrs" }).joinToString(" · ")
                if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val trailing = p.detail ?: p.jersey?.let { "#$it" }
            if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Badge(text: String) {
    Box(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary).padding(horizontal = 7.dp, vertical = 2.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
    }
}
