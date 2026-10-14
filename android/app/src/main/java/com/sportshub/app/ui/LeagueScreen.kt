package com.sportshub.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sportshub.app.data.StandingGroup
import com.sportshub.app.data.StandingRow
import com.sportshub.app.data.Team
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeagueScreen(leagueId: String, onBack: () -> Unit, onOpenTeam: (String) -> Unit) {
    val repo = rememberRepo()
    val title by produceState(leagueId, leagueId) { value = repo.league(leagueId)?.name ?: leagueId }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            },
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Table") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Teams") })
            }
            if (tab == 0) StandingsTab(leagueId, onOpenTeam) else TeamsTab(leagueId, title, onOpenTeam)
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
        Load.Loading -> LoadingBox()
        is Load.Error -> ErrorBox(s.message, vm::reload)
        is Load.Ready ->
            if (s.data.isEmpty()) EmptyState("No table", "This competition has no standings (it may be a knockout cup).")
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
private fun StandingsTable(
    leagueId: String,
    groups: List<StandingGroup>,
    followedIds: Set<String>,
    onOpenTeam: (String) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        groups.forEach { group ->
            val cols = columnsFor(group.rows, leagueId.startsWith("cricket:"))
            // F1's driver table has driver ids, which have no team page.
            val clickable = !(leagueId == "f1:f1" && group.name == "Drivers")
            item(key = "h-${group.name}") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(group.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    cols.forEach {
                        Text(
                            it.label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(it.width.dp),
                        )
                    }
                }
            }
            items(group.rows, key = { "${group.name}-${it.team.id}" }) { row ->
                val followed = row.team.id in followedIds && clickable
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (followed) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable(enabled = clickable) { onOpenTeam(row.team.id) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${row.rank}", modifier = Modifier.width(22.dp), style = MaterialTheme.typography.bodyMedium)
                    if (clickable) Logo(row.team.logo, row.team.name, 24.dp, Modifier.padding(end = 8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            row.team.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (followed) FontWeight.Bold else FontWeight.Normal,
                        )
                        row.subtitle?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    cols.forEach {
                        val bold = it.label == "Pts"
                        Text(
                            it.value(row) ?: "-", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End,
                            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.width(it.width.dp), maxLines = 1,
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

    when (val s = state) {
        Load.Loading -> LoadingBox()
        is Load.Error -> ErrorBox(s.message, vm::reload)
        is Load.Ready -> LazyColumn(Modifier.fillMaxSize()) {
            items(s.data.sortedBy { it.name }, key = { it.id }) { team: Team ->
                val following = follows.any { it.leagueId == leagueId && it.teamId == team.id }
                ListItem(
                    headlineContent = { Text(team.name) },
                    leadingContent = { Logo(team.logo, team.name, 36.dp) },
                    trailingContent = {
                        IconButton(onClick = {
                            scope.launch {
                                if (following) repo.unfollow("$leagueId:${team.id}") else repo.follow(team, leagueName)
                            }
                        }) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = if (following) "Unfollow ${team.name}" else "Follow ${team.name}",
                                tint = if (following) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    modifier = Modifier.clickable { onOpenTeam(team.id) },
                )
            }
        }
    }
}
