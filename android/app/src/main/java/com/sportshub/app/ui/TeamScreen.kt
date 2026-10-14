package com.sportshub.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sportshub.app.data.TeamOverview
import kotlinx.coroutines.launch

data class TeamPage(val overview: TeamOverview, val leagueName: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamScreen(leagueId: String, teamId: String, onBack: () -> Unit, onOpenLeague: () -> Unit) {
    val vm = loadViewModel("team:$leagueId:$teamId") { repo ->
        TeamPage(repo.overview(leagueId, teamId), repo.league(leagueId)?.name ?: leagueId)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()

    when (val s = state) {
        is Load.Ready -> TeamContent(s.data, leagueId, refreshing, vm::reload, onBack, onOpenLeague)
        else -> Scaffold(topBar = {
            TopAppBar(title = {}, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            })
        }) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                if (s is Load.Error) ErrorBox(s.message, vm::reload) else LoadingBox()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeamContent(
    page: TeamPage,
    leagueId: String,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onOpenLeague: () -> Unit,
) {
    val o = page.overview
    val repo = rememberRepo()
    val follows by repo.follows.teams.collectAsStateWithLifecycle(initialValue = emptyList())
    val following = follows.any { it.leagueId == leagueId && it.teamId == o.team.id }
    val scope = rememberCoroutineScope()

    TeamTheme(parseHex(o.team.color)) {
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = WindowInsets.navigationBars.asPaddingValues(),
            ) {
                item(key = "header") {
                    val onPrimary = if (MaterialTheme.colorScheme.onPrimary == Color.Black) Color.Black else Color.White
                    Column(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).statusBarsPadding().padding(16.dp),
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = onPrimary)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.clip(CircleShape).background(Color.White).padding(8.dp)) {
                                Logo(o.team.logo, o.team.name, 56.dp)
                            }
                            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                                Text(
                                    o.team.name, style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold, color = onPrimary,
                                )
                                Text(page.leagueName, style = MaterialTheme.typography.bodyMedium, color = onPrimary)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    if (following) repo.unfollow("$leagueId:${o.team.id}") else repo.follow(o.team, page.leagueName)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = onPrimary, contentColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) { Text(if (following) "Following" else "Follow") }
                    }
                }

                o.standing?.let { row ->
                    item(key = "standing") {
                        Card(
                            Modifier.fillMaxWidth().padding(16.dp).clickable(onClick = onOpenLeague),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        ) {
                            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("Position ${row.rank}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Text(o.standingGroup ?: "Table", style = MaterialTheme.typography.bodyMedium)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    row.points?.let {
                                        Text("${if (it % 1.0 == 0.0) it.toInt() else it} pts", style = MaterialTheme.typography.titleLarge)
                                    }
                                    row.played?.let { Text("$it played", style = MaterialTheme.typography.bodyMedium) }
                                }
                            }
                        }
                    }
                }

                if (o.live.isNotEmpty()) {
                    item(key = "h-live") { SectionTitle("Live now") }
                    items(o.live, key = { "l-${it.id}" }) { FixtureCard(it) }
                }
                item(key = "h-up") { SectionTitle("Next up") }
                if (o.upcoming.isEmpty()) item(key = "no-up") {
                    Text(
                        "No upcoming fixtures (off-season?).", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                items(o.upcoming, key = { "u-${it.id}" }) { FixtureCard(it) }
                if (o.recent.isNotEmpty()) {
                    item(key = "h-recent") { SectionTitle("Recent results") }
                    items(o.recent, key = { "r-${it.id}" }) { FixtureCard(it) }
                }
                if (o.news.isNotEmpty()) {
                    item(key = "h-news") { SectionTitle("News") }
                    items(o.news, key = { "n-${it.id}" }) { NewsCard(it) }
                }
                item(key = "end") { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}
