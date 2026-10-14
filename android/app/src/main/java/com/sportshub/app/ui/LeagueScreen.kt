package com.sportshub.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sportshub.app.data.League
import com.sportshub.app.data.StandingGroup
import com.sportshub.app.data.StandingRow
import com.sportshub.app.data.Team
import kotlinx.coroutines.launch

@Composable
fun LeagueScreen(leagueId: String, onBack: () -> Unit, onOpenTeam: (String) -> Unit) {
    val repo = rememberRepo()
    val league by produceState<League?>(null, leagueId) { value = repo.league(leagueId) }
    var tab by rememberSaveable { mutableIntStateOf(if (leagueId.startsWith("national:")) 1 else 0) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(start = 10.dp, end = 20.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            }
            Text(league?.name ?: "", Modifier.padding(start = 12.dp).weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            league?.let { Crest(it.logo, it.name, size = 40.dp) }
        }
        Segmented(listOf("Table", "Teams"), tab, { tab = it }, Modifier.padding(horizontal = 20.dp, vertical = 14.dp))
        AnimatedContent(tab, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "leagueTab") { selected ->
            if (selected == 0) StandingsTab(leagueId, onOpenTeam) else TeamsTab(leagueId, league?.name ?: "", onOpenTeam)
        }
    }
}

/** Two-option switch whose highlight slides between the options. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth().height(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer)) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(segment * selected, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow), label = "segmentOffset")
        Box(Modifier.offset(x = offset).width(segment).fillMaxHeight().padding(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer))
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                Box(Modifier.weight(1f).fillMaxHeight().clickable { onSelect(i) }, contentAlignment = Alignment.Center) {
                    Text(
                        label, style = MaterialTheme.typography.labelLarge,
                        color = if (i == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StandingsTab(leagueId: String, onOpenTeam: (String) -> Unit) {
    val vm = loadViewModel("standings:$leagueId") { it.standings(leagueId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val repo = rememberRepo()
    val follows by repo.follows.teams.collectAsStateWithLifecycle(initialValue = emptyList())
    val followedIds = remember(follows) { follows.filter { it.leagueId == leagueId }.map { it.teamId }.toSet() }

    when (val s = state) {
        Load.Loading -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(8) { Box(Modifier.fillMaxWidth().height(48.dp).shimmer(MaterialTheme.shapes.medium)) }
        }
        is Load.Error -> ErrorBox(s.message, vm::reload)
        is Load.Ready ->
            if (s.data.isEmpty()) EmptyState("No table here", "This competition has no standings. Open the Teams tab to follow a team.")
            else StandingsTable(leagueId, s.data, followedIds, onOpenTeam)
    }
}

private class Col(val label: String, val width: Int = 28, val value: (StandingRow) -> String?)

private fun num(d: Double?): String? = d?.let { if (it % 1.0 == 0.0) it.toInt().toString() else "%.2f".format(it) }

private fun columnsFor(rows: List<StandingRow>, cricket: Boolean): List<Col> {
    val all = listOf(
        Col("P") { it.played?.toString() },
        Col("W") { it.wins?.toString() },
        Col(if (cricket) "NR" else "D") { it.draws?.toString() },
        Col("L") { it.losses?.toString() },
        Col("GD", 36) { r -> r.goalDiff?.let { (if (it > 0) "+" else "") + num(it) } },
        Col("NRR", 44) { r -> r.extra["netRunRate"]?.let { "%.2f".format(it) } },
        Col("Pts", 34) { num(it.points) },
    )
    return all.filter { col -> rows.any { col.value(it) != null } }
}

@Composable
private fun StandingsTable(leagueId: String, groups: List<StandingGroup>, followedIds: Set<String>, onOpenTeam: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
        groups.forEach { group ->
            val cols = columnsFor(group.rows, leagueId.startsWith("cricket:"))
            // F1's driver table has driver ids, which have no team page.
            val clickable = !(leagueId == "f1:f1" && group.name == "Drivers")
            item(key = "h-${group.name}") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(group.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    cols.forEach { Text(it.label, Modifier.width(it.width.dp), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            items(group.rows, key = { "${group.name}-${it.team.id}" }) { row ->
                val followed = row.team.id in followedIds && clickable
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp).clip(MaterialTheme.shapes.small)
                        .background(if (followed) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable(enabled = clickable) { onOpenTeam(row.team.id) }
                        .padding(horizontal = 8.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${row.rank}", Modifier.width(24.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (clickable) Crest(row.team.logo, row.team.name, Modifier.padding(end = 10.dp), size = 28.dp)
                    Column(Modifier.weight(1f)) {
                        Text(row.team.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = if (followed) FontWeight.Bold else FontWeight.Medium)
                        row.subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    cols.forEach {
                        Text(
                            it.value(row) ?: "-", Modifier.width(it.width.dp), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, maxLines = 1,
                            fontWeight = if (it.label == "Pts") FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamsTab(leagueId: String, leagueName: String, onOpenTeam: (String) -> Unit) {
    val vm = loadViewModel("teams:$leagueId") { it.teams(leagueId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val repo = rememberRepo()
    val follows by repo.follows.teams.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val askPermission = rememberNotificationPermission()

    when (val s = state) {
        Load.Loading -> LazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(9) { Box(Modifier.fillMaxWidth().height(130.dp).shimmer(MaterialTheme.shapes.large)) }
        }
        is Load.Error -> ErrorBox(s.message, vm::reload)
        is Load.Ready -> LazyVerticalGrid(
            GridCells.Fixed(3), Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(s.data.sortedBy { it.name }, key = { it.id }) { team: Team ->
                val following = follows.any { it.leagueId == leagueId && it.teamId == team.id }
                TeamGridCard(team, following, { onOpenTeam(team.id) }) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch {
                        if (following) repo.unfollow("$leagueId:${team.id}") else {
                            repo.follow(team, leagueName)
                            askPermission()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamGridCard(team: Team, following: Boolean, onOpen: () -> Unit, onToggle: () -> Unit) {
    val scale by animateFloatAsState(if (following) 1.18f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "star")
    GlassCard(Modifier.fillMaxWidth().height(132.dp), onClick = onOpen) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Crest(team.logo, team.name, size = 54.dp)
                Text(team.name, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onToggle, Modifier.align(Alignment.TopEnd).size(38.dp)) {
                Icon(
                    Icons.Default.Star, contentDescription = if (following) "Unfollow ${team.name}" else "Follow ${team.name}",
                    Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
                    tint = if (following) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}
