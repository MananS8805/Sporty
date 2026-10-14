package com.sportshub.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sportshub.app.data.FollowedTeam

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(onAddTeam: () -> Unit, onOpenTeam: (FollowedTeam) -> Unit) {
    val vm = feedViewModel()
    val ui by vm.ui.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Sports Hub") }) }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val follows = ui.follows
            val feed = ui.feed
            when {
                follows == null -> LoadingBox()
                follows.isEmpty() -> EmptyState(
                    title = "Follow your teams",
                    body = "Pick the teams you support, from the Premier League to the IPL to Ferrari, and see all their matches and news here.",
                    action = "Add a team",
                    onAction = onAddTeam,
                )
                else -> PullToRefreshBox(isRefreshing = ui.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                    if (feed == null) {
                        if (ui.error != null) ErrorBox(ui.error!!, vm::refresh) else LoadingBox()
                    } else LazyColumn(Modifier.fillMaxSize()) {
                        item(key = "teams") {
                            LazyRow(
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                items(follows, key = { it.key }) { t ->
                                    Column(
                                        Modifier.width(64.dp).clickable { onOpenTeam(t) },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Logo(t.logo, t.name, 44.dp)
                                        Text(
                                            t.name, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            }
                        }
                        if (ui.error != null) item(key = "stale") {
                            Text(
                                "Couldn't refresh. Showing the last data.", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        if (feed.errors.isNotEmpty()) item(key = "partial") {
                            Text(
                                "Some teams couldn't be loaded right now.", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        if (feed.live.isNotEmpty()) {
                            item(key = "h-live") { SectionTitle("Live now") }
                            items(feed.live, key = { "l-${it.id}" }) { FixtureCard(it) }
                        }
                        item(key = "h-up") { SectionTitle("Coming up") }
                        if (feed.upcoming.isEmpty()) item(key = "no-up") {
                            Text(
                                "No upcoming fixtures right now (off-season?).", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        items(feed.upcoming, key = { "u-${it.id}" }) { FixtureCard(it) }
                        if (feed.recent.isNotEmpty()) {
                            item(key = "h-recent") { SectionTitle("Latest results") }
                            items(feed.recent, key = { "r-${it.id}" }) { FixtureCard(it) }
                        }
                        if (feed.news.isNotEmpty()) {
                            item(key = "h-news") { SectionTitle("News") }
                            items(feed.news, key = { "n-${it.id}" }) { NewsCard(it) }
                        }
                        item(key = "end") { androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp)) }
                    }
                }
            }
        }
    }
}
