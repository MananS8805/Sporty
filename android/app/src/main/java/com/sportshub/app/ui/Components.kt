package com.sportshub.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sportshub.app.data.Fixture
import com.sportshub.app.data.NewsItem
import com.sportshub.app.data.TeamRef

@Composable
fun Logo(url: String?, name: String, size: Dp = 32.dp, modifier: Modifier = Modifier) {
    if (url != null) {
        AsyncImage(model = url, contentDescription = name, modifier = modifier.size(size), contentScale = ContentScale.Fit)
    } else {
        // No logo upstream (F1 constructors): show an initial on a tinted circle.
        Box(
            modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
fun LoadingBox() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit) = Column(
    Modifier.fillMaxSize().padding(32.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(16.dp))
    Button(onClick = onRetry) { Text("Retry") }
}

@Composable
fun EmptyState(title: String, body: String, action: String? = null, onAction: () -> Unit = {}) = Column(
    Modifier.fillMaxSize().padding(32.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(
        body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (action != null) {
        Spacer(Modifier.height(20.dp))
        Button(onClick = onAction) { Text(action) }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) = Text(
    text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
    modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
)

@Composable
private fun TeamCell(team: TeamRef?, modifier: Modifier = Modifier) = Column(
    modifier, horizontalAlignment = Alignment.CenterHorizontally,
) {
    Logo(team?.logo, team?.name ?: "?", 36.dp)
    Spacer(Modifier.height(4.dp))
    Text(
        team?.name ?: "TBD", style = MaterialTheme.typography.labelMedium, maxLines = 2,
        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
    )
}

@Composable
fun FixtureCard(fixture: Fixture, modifier: Modifier = Modifier) {
    Card(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    fixture.leagueName, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                StatusLabel(fixture)
            }
            Spacer(Modifier.height(8.dp))
            if (fixture.home != null && fixture.away != null) HeadToHead(fixture) else EventBody(fixture)
        }
    }
}

@Composable
private fun StatusLabel(f: Fixture) {
    val (text, color) = when (f.status) {
        "live" -> "LIVE" to MaterialTheme.colorScheme.error
        "final" -> formatShortDay(f.date) + " · FT" to MaterialTheme.colorScheme.onSurfaceVariant
        "postponed" -> "Postponed" to MaterialTheme.colorScheme.error
        else -> formatDay(f.date) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
}

@Composable
private fun HeadToHead(f: Fixture) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TeamCell(f.home, Modifier.weight(1f))
        Column(Modifier.width(92.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val centre = when (f.status) {
                "scheduled" -> formatTime(f.date)
                "postponed" -> "–"
                else -> "${f.homeScore ?: "-"}  –  ${f.awayScore ?: "-"}"
            }
            Text(
                centre, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            if (f.status == "live") f.statusDetail?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
        TeamCell(f.away, Modifier.weight(1f))
    }
    // Cricket-style results ("RCB won by 5 wickets") arrive as free text.
    val result = f.statusDetail
    if (f.status == "final" && result != null && result.length > 8) {
        Spacer(Modifier.height(8.dp))
        Text(
            result, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
        )
    }
}

/** Events without two sides, i.e. a Grand Prix weekend. */
@Composable
private fun EventBody(f: Fixture) {
    Text(f.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    f.venue?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    f.summary?.let {
        Spacer(Modifier.height(6.dp))
        Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
    if (f.status != "final" && f.sessions.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        f.sessions.forEach {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(it.name, style = MaterialTheme.typography.bodySmall)
                Text(
                    formatKickoff(it.date), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Race", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            Text(formatKickoff(f.date), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
    } else if (f.status != "final") {
        Text(formatKickoff(f.date), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun NewsCard(item: NewsItem, modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    Card(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(enabled = item.url != null) { item.url?.let { runCatching { uri.openUri(it) } } },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.headline, style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                item.published?.let {
                    Text(
                        formatShortDay(it), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            item.image?.let {
                Spacer(Modifier.width(12.dp))
                AsyncImage(
                    model = it, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)),
                )
            }
        }
    }
}
