package com.sportshub.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sportshub.app.data.Feed
import com.sportshub.app.data.Fixture
import com.sportshub.app.data.FollowedTeam
import com.sportshub.app.data.MatchInsight
import java.time.LocalDate
import kotlin.math.absoluteValue

/** A followed team together with the match that matters most for it right now. */
data class Hero(val team: FollowedTeam, val fixture: Fixture)

private fun sportOf(leagueId: String) = when (leagueId) {
    "national:football" -> "soccer"
    "national:cricket" -> "cricket"
    else -> leagueId.substringBefore(':')
}

fun fixtureBelongs(f: Fixture, t: FollowedTeam): Boolean {
    if (sportOf(f.leagueId) != sportOf(t.leagueId)) return false
    return f.home?.id == t.teamId || f.away?.id == t.teamId || (f.home == null && f.away == null && f.leagueId == t.leagueId)
}

/** The live or next match for each followed team, soonest first; a match between two followed teams shows once. */
fun heroesFor(follows: List<FollowedTeam>, feed: Feed): List<Hero> {
    val seen = HashSet<String>()
    return follows.mapNotNull { team ->
        val next = (feed.live + feed.upcoming).filter { fixtureBelongs(it, team) }.minByOrNull { it.date } ?: return@mapNotNull null
        if (seen.add(next.id)) Hero(team, next) else null
    }.sortedBy { it.fixture.date }
}

@Composable
fun rememberInsight(fixture: Fixture): MatchInsight? {
    val vm = loadViewModel("insight:${fixture.id}") { it.insight(fixture) }
    val state by vm.state.collectAsStateWithLifecycle()
    return (state as? Load.Ready)?.data
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onAddTeam: () -> Unit,
    onOpenTeam: (FollowedTeam) -> Unit,
    onOpenMatch: (Fixture, FollowedTeam?) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val vm = feedViewModel()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val follows = ui.follows
    val feed = ui.feed

    Box(Modifier.fillMaxSize()) {
        when {
            follows == null -> HomeSkeleton()
            follows.isEmpty() -> WelcomeEmpty(onAddTeam, onOpenSettings)
            else -> PullToRefreshBox(isRefreshing = ui.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                if (feed == null) {
                    if (ui.error != null) ErrorBox(ui.error!!, vm::refresh) else HomeSkeleton()
                } else {
                    val heroes = remember(follows, feed) { heroesFor(follows, feed) }
                    val liveTeams = remember(follows, feed) { follows.filter { t -> feed.live.any { fixtureBelongs(it, t) } }.map { it.key }.toSet() }
                    val teamFor: (Fixture) -> FollowedTeam? = { f -> follows.firstOrNull { fixtureBelongs(f, it) } }

                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
                        item(key = "header") { HomeHeader(onOpenSettings) }
                        item(key = "stories") { StoriesRow(follows, liveTeams, onOpenTeam, onAddTeam) }
                        if (ui.error != null) item(key = "stale") {
                            Text("Couldn't refresh. Showing the last data.", Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                        }
                        if (heroes.isNotEmpty()) item(key = "heroes") {
                            HeroPager(heroes, onOpenMatch, Modifier.padding(top = 8.dp))
                        }
                        timeline(feed, teamFor, onOpenMatch)
                        if (feed.news.isNotEmpty()) {
                            item(key = "h-news") { SectionHeader("News", Modifier.padding(top = 12.dp)) }
                            items(feed.news.take(12), key = { "n-${it.id}" }) { NewsTile(it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(onOpenSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM", java.util.Locale.getDefault())).uppercase(),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Your teams", style = MaterialTheme.typography.displaySmall)
        }
        IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
    }
}

@Composable
private fun StoriesRow(follows: List<FollowedTeam>, live: Set<String>, onOpen: (FollowedTeam) -> Unit, onAdd: () -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(follows, key = { it.key }) { team -> StoryAvatar(team, team.key in live) { onOpen(team) } }
        item(key = "add") {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(68.dp)) {
                Box(
                    Modifier.size(68.dp).clip(CircleShape).border(2.dp, MaterialTheme.colorScheme.outline, CircleShape).clickable(onClick = onAdd),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Add, contentDescription = "Add a team", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(Modifier.height(6.dp))
                Text("Add", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StoryAvatar(team: FollowedTeam, live: Boolean, onClick: () -> Unit) {
    val color = parseHex(team.color) ?: MaterialTheme.colorScheme.primary
    val liveColor = MaterialTheme.extra.live
    val pulse by rememberInfiniteTransition(label = "storyPulse").animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse",
    )
    Column(Modifier.width(68.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(68.dp).drawBehind {
                val stroke = (if (live) 4.dp else 3.dp).toPx()
                if (live) {
                    drawCircle(liveColor.copy(alpha = pulse), radius = size.minDimension / 2f - stroke / 2f, style = Stroke(stroke))
                } else {
                    drawCircle(
                        Brush.sweepGradient(listOf(color, color.copy(alpha = 0.3f), color), center = Offset(size.width / 2f, size.height / 2f)),
                        radius = size.minDimension / 2f - stroke / 2f, style = Stroke(stroke),
                    )
                }
            },
            contentAlignment = Alignment.Center,
        ) {
            Crest(team.logo, team.name, Modifier.sharedElementIfAvailable(crestKey(team.leagueId, team.teamId)), size = 56.dp)
        }
        Spacer(Modifier.height(6.dp))
        Text(team.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun HeroPager(heroes: List<Hero>, onOpenMatch: (Fixture, FollowedTeam?) -> Unit, modifier: Modifier = Modifier) {
    val pager = rememberPagerState { heroes.size }
    Column(modifier) {
        HorizontalPager(pager, contentPadding = PaddingValues(horizontal = 20.dp), pageSpacing = 12.dp, key = { heroes[it].fixture.id }) { page ->
            val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
            HeroCard(
                heroes[page],
                Modifier.graphicsLayer {
                    val s = lerp(1f, 0.93f, offset)
                    scaleX = s; scaleY = s
                    alpha = lerp(1f, 0.55f, offset)
                },
                onClick = { onOpenMatch(heroes[page].fixture, heroes[page].team) },
            )
        }
        if (heroes.size > 1) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
                repeat(heroes.size) { i ->
                    val active = pager.currentPage == i
                    Box(
                        Modifier.padding(horizontal = 3.dp).height(6.dp).width(if (active) 18.dp else 6.dp).clip(CircleShape)
                            .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
        }
    }
}

@Composable
fun HeroCard(hero: Hero, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val f = hero.fixture
    val team = parseHex(hero.team.color) ?: MaterialTheme.colorScheme.primary
    val ink = onColor(team)
    val live = f.status == "live"
    val insight = rememberInsight(f)
    val background = Brush.linearGradient(listOf(team, lerpColor(team, Color.Black, 0.55f)))

    Box(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(background).clickable(onClick = onClick),
    ) {
        // A large, faint crest as texture.
        Crest(hero.team.logo, hero.team.name, Modifier.align(Alignment.TopEnd).offset(x = 48.dp, y = (-32).dp).graphicsLayer { alpha = 0.12f }, size = 220.dp)
        Column(Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(f.leagueName.uppercase(), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (live) {
                    Row(
                        Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.3f)).padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LiveDot(size = 7.dp)
                        Text("LIVE", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            if (f.home != null && f.away != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Crest(f.home.logo, f.home.name, size = 52.dp)
                    Text(if (live) "${f.homeScore ?: 0} – ${f.awayScore ?: 0}" else "v", style = MaterialTheme.typography.headlineMedium, color = ink, fontWeight = FontWeight.Bold)
                    Crest(f.away.logo, f.away.name, size = 52.dp)
                }
                Spacer(Modifier.height(12.dp))
                Text("${f.home.name} v ${f.away.name}", style = MaterialTheme.typography.titleLarge, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            } else {
                Text(f.title, style = MaterialTheme.typography.headlineMedium, color = ink)
                f.venue?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = 0.8f)) }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(if (live) "In play" else "Kicks off in", style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = 0.75f))
                    if (live) Text(f.statusDetail ?: "Live", style = MaterialTheme.typography.displaySmall, color = ink)
                    else Countdown(f.date, style = MaterialTheme.typography.displayMedium, color = ink)
                }
                Text(formatKickoff(f.date), style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.85f))
            }
            Spacer(Modifier.height(16.dp))
            HeroForecast(insight, f, ink)
        }
    }
}

@Composable
private fun HeroForecast(insight: MatchInsight?, f: Fixture, ink: Color) {
    if (insight == null) {
        Box(Modifier.fillMaxWidth().height(10.dp).shimmer(CircleShape))
        return
    }
    val p = insight.prediction
    if (p.available && f.home != null && f.away != null) {
        ProbabilityBar(p.home!!.toFloat(), p.draw?.toFloat(), p.away!!.toFloat(), ink, ink.copy(alpha = 0.35f), drawColor = ink.copy(alpha = 0.6f))
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${f.home.name} ${(p.home * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = ink)
            p.draw?.let { Text("Draw ${(it * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.8f)) }
            Text("${(p.away * 100).toInt()}% ${f.away.name}", style = MaterialTheme.typography.labelMedium, color = ink)
        }
    }
    val watch = insight.watch.options.firstOrNull()
    if (watch != null) {
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.28f)).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TvIcon(size = 15.dp, tint = Color.White)
            Text("Watch on ${watch.provider}", style = MaterialTheme.typography.labelMedium, color = Color.White)
        }
    }
}

/** Upcoming matches grouped under sticky day headers, then recent results. */
@OptIn(ExperimentalFoundationApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.timeline(
    feed: Feed,
    teamFor: (Fixture) -> FollowedTeam?,
    onOpenMatch: (Fixture, FollowedTeam?) -> Unit,
) {
    val upcoming = (feed.live + feed.upcoming).distinctBy { it.id }
    val grouped = upcoming.groupBy { localDateOf(it.date) ?: LocalDate.MAX }.toSortedMap()
    var rank = 0
    grouped.forEach { (day, matches) ->
        stickyHeader(key = "day-$day") { DayHeader(formatDayHeader(matches.first().date)) }
        items(matches, key = { "t-${it.id}" }) { fixture ->
            val team = teamFor(fixture)
            val index = rank++
            TimelineItem(fixture, team, index, withInsight = index < 8, onOpenMatch)
        }
    }
    if (feed.recent.isNotEmpty()) {
        stickyHeader(key = "day-results") { DayHeader("Latest results") }
        items(feed.recent.take(8), key = { "r-${it.id}" }) { fixture ->
            TimelineItem(fixture, teamFor(fixture), rank++, withInsight = false, onOpenMatch)
        }
    }
}

@Composable
private fun TimelineItem(fixture: Fixture, team: FollowedTeam?, index: Int, withInsight: Boolean, onOpenMatch: (Fixture, FollowedTeam?) -> Unit) {
    val accent = parseHex(team?.color) ?: MaterialTheme.colorScheme.primary
    val insight = if (withInsight) rememberInsight(fixture) else null
    TimelineCard(fixture, insight, accent, { onOpenMatch(fixture, team) }, Modifier.entrance(index), teamId = team?.teamId)
}

@Composable
private fun DayHeader(label: String) {
    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Text(label, Modifier.padding(horizontal = 20.dp, vertical = 10.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HomeSkeleton() {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 12.dp)) {
        Box(Modifier.padding(horizontal = 20.dp).width(180.dp).height(34.dp).shimmer(MaterialTheme.shapes.small))
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(4) { Box(Modifier.size(68.dp).shimmer(CircleShape)) }
        }
        Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(300.dp).shimmer(MaterialTheme.shapes.extraLarge))
        repeat(2) { TimelineSkeleton() }
    }
}

@Composable
private fun WelcomeEmpty(onAdd: () -> Unit, onSettings: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "welcome").animateFloat(
        0.92f, 1.08f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "scale",
    )
    Box(Modifier.fillMaxSize()) {
        IconButton(onSettings, Modifier.align(Alignment.TopEnd).statusBarsPadding()) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
        Column(Modifier.fillMaxSize().padding(36.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(120.dp).graphicsLayer { scaleX = pulse; scaleY = pulse }.clip(CircleShape)
                    .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)))),
                contentAlignment = Alignment.Center,
            ) { Text("★", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onPrimary) }
            Spacer(Modifier.height(28.dp))
            Text("Follow your teams", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(
                "Pick the clubs and countries you support, from the Premier League to the IPL to Ferrari. Fixtures, forecasts, squads and where to watch land here.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(26.dp))
            Button(onClick = onAdd) { Text("Add a team") }
        }
    }
}
