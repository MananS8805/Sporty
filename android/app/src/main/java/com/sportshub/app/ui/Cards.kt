package com.sportshub.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sportshub.app.data.Fixture
import com.sportshub.app.data.MatchInsight
import com.sportshub.app.data.NewsItem
import com.sportshub.app.data.TeamRef
import com.sportshub.app.data.WatchOption

fun initials(name: String): String = name.split(' ', '-').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }

@Composable
fun Crest(url: String?, name: String, modifier: Modifier = Modifier, size: Dp = 40.dp, ring: Color? = null) {
    Box(
        modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(if (ring != null) Modifier.border(2.dp, ring, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(url, name, Modifier.fillMaxSize().padding(size * 0.16f), contentScale = ContentScale.Fit)
        } else {
            Text(initials(name), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.large,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = containerColor, border = border) { Column(content = content) }
    } else {
        Surface(modifier = modifier, shape = shape, color = containerColor, border = border) { Column(content = content) }
    }
}

/** A small TV glyph, drawn so we don't need the large extended icon set. */
@Composable
fun TvIcon(modifier: Modifier = Modifier, size: Dp = 16.dp, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Canvas(modifier.size(size)) {
        val s = Stroke(width = this.size.minDimension * 0.1f, cap = StrokeCap.Round)
        drawRoundRect(tint, Offset(0f, this.size.height * 0.1f), Size(this.size.width, this.size.height * 0.62f), CornerRadius(this.size.width * 0.14f), style = s)
        drawLine(tint, Offset(this.size.width * 0.3f, this.size.height * 0.92f), Offset(this.size.width * 0.7f, this.size.height * 0.92f), strokeWidth = s.width, cap = StrokeCap.Round)
    }
}

@Composable
fun WatchChip(option: WatchOption, modifier: Modifier = Modifier) {
    val tint = if (option.confirmed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier.clip(CircleShape).background(tint.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TvIcon(size = 14.dp, tint = tint)
        Text(option.provider + if (option.confirmed) "" else "?", style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

@Composable
private fun TeamLine(team: TeamRef, score: String?, emphasise: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Crest(team.logo, team.name, size = 26.dp)
        Text(
            team.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
            fontWeight = if (emphasise) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (score != null) Text(score, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

/** One row of the schedule: a time rail on the left, then a card with teams, an optional forecast bar and watch chips. */
@Composable
fun TimelineCard(
    fixture: Fixture,
    insight: MatchInsight?,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    teamId: String? = null,
) {
    val live = fixture.status == "live"
    val liveColor = MaterialTheme.extra.live
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 16.dp)) {
        Column(Modifier.width(54.dp).padding(top = 14.dp)) {
            Text(
                if (fixture.status == "final") "FT" else formatTime(fixture.date),
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                color = if (live) liveColor else MaterialTheme.colorScheme.onSurface,
            )
            if (live) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LiveDot(size = 6.dp)
                Text("LIVE", style = MaterialTheme.typography.labelSmall, color = liveColor, fontWeight = FontWeight.Bold)
            }
        }
        Box(Modifier.width(22.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.padding(top = 22.dp).width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
            Box(Modifier.padding(top = 17.dp).size(11.dp).clip(CircleShape).background(if (live) liveColor else accent))
        }
        GlassCard(Modifier.weight(1f).padding(bottom = 12.dp), onClick = onClick) {
            Column(Modifier.padding(14.dp)) {
                Text(fixture.leagueName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                if (fixture.home != null && fixture.away != null) {
                    val finished = fixture.status != "scheduled"
                    TeamLine(fixture.home, fixture.homeScore.takeIf { finished }, emphasise = fixture.home.id == teamId)
                    Spacer(Modifier.height(8.dp))
                    TeamLine(fixture.away, fixture.awayScore.takeIf { finished }, emphasise = fixture.away.id == teamId)
                } else {
                    Text(fixture.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    fixture.venue?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    fixture.summary?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }
                if (fixture.status != "final" && insight != null) {
                    PredictionStrip(insight, fixture, teamId, accent)
                }
            }
        }
    }
}

@Composable
private fun PredictionStrip(insight: MatchInsight, fixture: Fixture, teamId: String?, accent: Color) {
    val p = insight.prediction
    val away = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    if (p.available && fixture.home != null && fixture.away != null) {
        Spacer(Modifier.height(12.dp))
        ProbabilityBar(p.home!!.toFloat(), p.draw?.toFloat(), p.away!!.toFloat(), accent, away, height = 6.dp)
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${(p.home * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            p.draw?.let { Text("Draw ${(it * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("${(p.away * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val watch = insight.watch.options.take(2)
    if (watch.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { watch.forEach { WatchChip(it) } }
    }
}

@Composable
fun NewsTile(item: NewsItem, modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    GlassCard(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), onClick = item.url?.let { url -> { runCatching { uri.openUri(url) } } }) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.headline, style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                item.published?.let {
                    Text(formatShortDay(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
            }
            item.image?.let {
                Spacer(Modifier.width(12.dp))
                AsyncImage(it, null, Modifier.size(84.dp).clip(RoundedCornerShape(16.dp)).shimmer(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
            }
        }
    }
}

/** Skeleton for a timeline card while data loads. */
@Composable
fun TimelineSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Box(Modifier.fillMaxWidth().height(110.dp).shimmer(MaterialTheme.shapes.large))
    }
}
