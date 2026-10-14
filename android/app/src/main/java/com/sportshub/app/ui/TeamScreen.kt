package com.sportshub.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sportshub.app.data.Fixture
import com.sportshub.app.data.FollowedTeam
import com.sportshub.app.data.MatchInsight
import com.sportshub.app.data.Player
import com.sportshub.app.data.Predictions
import com.sportshub.app.data.Squad
import com.sportshub.app.data.TeamOverview
import kotlinx.coroutines.launch

data class TeamPage(val overview: TeamOverview, val leagueName: String)

@Composable
fun TeamScreen(
    leagueId: String,
    teamId: String,
    onBack: () -> Unit,
    onOpenLeague: () -> Unit,
    onOpenSquad: () -> Unit,
    onOpenMatch: (Fixture, FollowedTeam?) -> Unit,
) {
    val vm = loadViewModel("team:$leagueId:$teamId") { repo ->
        TeamPage(repo.overview(leagueId, teamId), repo.league(leagueId)?.name ?: leagueId)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            is Load.Ready -> TeamContent(s.data, leagueId, refreshing, vm::reload, onOpenLeague, onOpenSquad, onOpenMatch)
            is Load.Error -> ErrorBox(s.message, vm::reload)
            Load.Loading -> TeamSkeleton()
        }
        Box(
            Modifier.align(Alignment.TopStart).statusBarsPadding().padding(10.dp).size(42.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.32f)),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }
        }
    }
}

@Composable
private fun TeamSkeleton() {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(290.dp).shimmer(MaterialTheme.shapes.large))
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth().height(150.dp).shimmer(MaterialTheme.shapes.large))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f).height(190.dp).shimmer(MaterialTheme.shapes.large))
                Box(Modifier.weight(1f).height(190.dp).shimmer(MaterialTheme.shapes.large))
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
    onOpenLeague: () -> Unit,
    onOpenSquad: () -> Unit,
    onOpenMatch: (Fixture, FollowedTeam?) -> Unit,
) {
    val o = page.overview
    val team = o.team
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val askPermission = rememberNotificationPermission()
    val follows by repo.follows.teams.collectAsStateWithLifecycle(initialValue = emptyList())
    val key = "$leagueId:${team.id}"
    val following = follows.any { it.key == key }
    val followed = FollowedTeam(leagueId, page.leagueName, team.id, team.name, team.logo, team.color)

    val next: Fixture? = o.live.firstOrNull() ?: o.upcoming.firstOrNull()
    val insight = next?.let { rememberInsight(it) }
    val squadVm = loadViewModel("squad:$leagueId:${team.id}") { it.squad(leagueId, team.id) }
    val squadState by squadVm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    TeamTheme(parseHex(team.color)) {
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 110.dp)) {
                item(key = "hero") {
                    TeamHero(
                        page, following, listState,
                        onFollow = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch {
                                if (following) repo.unfollow(key) else {
                                    repo.follow(team, page.leagueName)
                                    askPermission()
                                }
                            }
                        },
                        onOpenLeague = onOpenLeague,
                    )
                }
                if (next != null) item(key = "next") {
                    NextMatchTile(next, team.id) { onOpenMatch(next, followed) }
                }
                item(key = "bento") {
                    BentoRow(o, next, insight, team.id, team.name) { next?.let { onOpenMatch(it, followed) } }
                }
                item(key = "squad") {
                    SquadTile((squadState as? Load.Ready)?.data, squadState is Load.Loading, onOpenSquad)
                }
                if (next != null) item(key = "watch") { WatchTile(insight, next) { onOpenMatch(next, followed) } }

                if (o.upcoming.size > 1 || (next != null && o.live.isNotEmpty() && o.upcoming.isNotEmpty())) {
                    item(key = "h-up") { SectionHeader("Fixtures", Modifier.padding(top = 8.dp)) }
                    items((o.live + o.upcoming).distinctBy { it.id }.drop(if (next != null) 1 else 0), key = { "u-${it.id}" }) { f ->
                        TimelineCard(f, null, MaterialTheme.colorScheme.primary, { onOpenMatch(f, followed) }, Modifier.entrance(1), teamId = team.id)
                    }
                }
                if (o.recent.isNotEmpty()) {
                    item(key = "h-recent") { SectionHeader("Recent results", Modifier.padding(top = 8.dp)) }
                    items(o.recent, key = { "r-${it.id}" }) { f ->
                        TimelineCard(f, null, MaterialTheme.colorScheme.primary, { onOpenMatch(f, followed) }, Modifier.entrance(1), teamId = team.id)
                    }
                }
                if (o.news.isNotEmpty()) {
                    item(key = "h-news") { SectionHeader("News", Modifier.padding(top = 8.dp)) }
                    items(o.news, key = { "n-${it.id}" }) { NewsTile(it) }
                }
            }
        }
    }
}

@Composable
private fun TeamHero(
    page: TeamPage,
    following: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onFollow: () -> Unit,
    onOpenLeague: () -> Unit,
) {
    val team = page.overview.team
    // The hero keeps the true brand colour; only text and small accents use the lightened, legible variant.
    val color = parseHex(team.color) ?: MaterialTheme.colorScheme.primary
    val ink = onColor(color)
    val background = Brush.verticalGradient(listOf(color, lerp(color, Color.Black, 0.6f)))
    Box(Modifier.fillMaxWidth().background(background)) {
        Crest(
            team.logo, team.name, Modifier.align(Alignment.TopEnd).graphicsLayer { alpha = 0.1f; translationX = 70.dp.toPx(); translationY = 30.dp.toPx() },
            size = 260.dp,
        )
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 22.dp, end = 22.dp, top = 62.dp, bottom = 26.dp).graphicsLayer {
                // Parallax: the content drifts up slower than the scroll and fades as it leaves.
                val offset = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 2000f
                translationY = offset * 0.45f
                alpha = (1f - offset / 520f).coerceIn(0f, 1f)
            },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.96f)).padding(6.dp)) {
                    Crest(team.logo, team.name, Modifier.sharedElementIfAvailable(crestKey(team.leagueId, team.id)), size = 88.dp)
                }
                Column(Modifier.weight(1f)) {
                    Text(team.name, style = MaterialTheme.typography.headlineLarge, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(page.leagueName, style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = 0.85f))
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                page.overview.standing?.let { row ->
                    Row(
                        Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.25f)).clickable(onClick = onOpenLeague).padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${Predictions.ordinal(row.rank)} · ${page.overview.standingGroup ?: "Table"}", style = MaterialTheme.typography.labelLarge, color = Color.White)
                    }
                }
                Button(
                    onClick = onFollow,
                    colors = ButtonDefaults.buttonColors(containerColor = ink, contentColor = color),
                ) { Text(if (following) "Following" else "Follow", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun NextMatchTile(f: Fixture, teamId: String, onClick: () -> Unit) {
    val live = f.status == "live"
    GlassCard(Modifier.padding(16.dp).fillMaxWidth().entrance(0), onClick = onClick) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (live) { LiveDot(); Spacer(Modifier.width(6.dp)) }
                Text(if (live) "LIVE" else "NEXT MATCH", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (live) MaterialTheme.extra.live else MaterialTheme.colorScheme.primary)
                if (!live) Countdown(f.date, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(14.dp))
            if (f.home != null && f.away != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Crest(f.home.logo, f.home.name, size = 52.dp)
                        Text(f.home.name, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, fontWeight = if (f.home.id == teamId) FontWeight.Bold else FontWeight.Medium)
                    }
                    Text(if (live) "${f.homeScore ?: 0} – ${f.awayScore ?: 0}" else formatTime(f.date), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Crest(f.away.logo, f.away.name, size = 52.dp)
                        Text(f.away.name, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, fontWeight = if (f.away.id == teamId) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            } else {
                Text(f.title, style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(Modifier.height(12.dp))
            Text(formatKickoff(f.date) + (f.venue?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BentoRow(o: TeamOverview, next: Fixture?, insight: MatchInsight?, teamId: String, teamName: String, onOpenForecast: () -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(214.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ForecastTile(next, insight, teamId, Modifier.weight(1f).fillMaxHeight().entrance(1), onOpenForecast)
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FormTile(o, teamId, teamName, Modifier.weight(1f).fillMaxWidth().entrance(2))
            PositionTile(o, Modifier.weight(1f).fillMaxWidth().entrance(3))
        }
    }
}

@Composable
private fun ForecastTile(next: Fixture?, insight: MatchInsight?, teamId: String, modifier: Modifier, onClick: () -> Unit) {
    GlassCard(modifier, onClick = onClick) {
        Column(Modifier.fillMaxSize().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("WIN FORECAST", Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            val odds = if (next != null && insight != null) insight.prediction.forTeam(next, teamId) else null
            when {
                next == null -> Text("No upcoming match", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                insight == null -> Box(Modifier.size(112.dp).shimmer(CircleShape))
                odds != null -> {
                    val c = MaterialTheme.colorScheme
                    ProbabilityRing(
                        segments = listOfNotNull(odds.win to c.primary, odds.draw?.let { it to MaterialTheme.extra.draw }, odds.loss to c.onSurfaceVariant.copy(alpha = 0.5f)),
                        size = 112.dp, stroke = 12.dp,
                    ) { CountUpText(odds.win * 100f, suffix = "%", style = MaterialTheme.typography.headlineMedium) }
                    Spacer(Modifier.height(8.dp))
                    Text("Tap for why", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                else -> {
                    Text("No expert forecast", style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
                    Text(insight.prediction.unavailableReason ?: "", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 5, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun FormTile(o: TeamOverview, teamId: String, teamName: String, modifier: Modifier) {
    GlassCard(modifier) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text("FORM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val form = o.recent.mapNotNull { formOf(it, teamId, teamName) }.take(5).reversed()
            when {
                form.isNotEmpty() -> FormChips(form, chip = 21.dp, spacing = 4.dp)
                o.recent.firstOrNull()?.summary != null -> Text(o.recent.first().summary!!, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                else -> Text("No recent results", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PositionTile(o: TeamOverview, modifier: Modifier) {
    GlassCard(modifier) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text("POSITION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val row = o.standing
            if (row == null) {
                Text("No table", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CountUpText(row.rank.toFloat(), style = MaterialTheme.typography.displaySmall)
                    Text(row.points?.let { "${it.toInt()} pts" } ?: "", Modifier.padding(bottom = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SquadTile(squad: Squad?, loading: Boolean, onClick: () -> Unit) {
    GlassCard(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth().entrance(4), onClick = onClick) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("SQUAD", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                when {
                    loading -> Box(Modifier.width(150.dp).height(36.dp).shimmer(CircleShape))
                    squad == null || squad.players.isEmpty() -> Text(squad?.note ?: "Squad unavailable", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> Row(verticalAlignment = Alignment.CenterVertically) {
                        squad.players.take(5).forEachIndexed { i, p -> PlayerAvatar(p, 36.dp, Modifier.graphicsLayer { translationX = -10.dp.toPx() * i }) }
                        Text("${squad.players.size} players", Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text("View ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun WatchTile(insight: MatchInsight?, next: Fixture, onClick: () -> Unit) {
    GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth().entrance(5), onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            Text("WHERE TO WATCH", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            when {
                insight == null -> Box(Modifier.width(200.dp).height(30.dp).shimmer(CircleShape))
                insight.watch.options.isEmpty() -> Text(
                    "No confirmed broadcaster for ${next.title} yet. Tap to search.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { insight.watch.options.take(3).forEach { WatchChip(it) } }
            }
        }
    }
}

@Composable
fun PlayerAvatar(player: Player, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (player.headshot != null) {
            coil.compose.AsyncImage(player.headshot, player.name, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        } else {
            Text(player.jersey ?: initials(player.name), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}
