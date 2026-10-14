package com.sportshub.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sportshub.app.data.Fixture
import com.sportshub.app.data.Prediction
import kotlinx.coroutines.delay

/** A forecast seen from one team's side: its win chance, the draw chance (football only), and its loss chance. */
data class TeamOdds(val win: Float, val draw: Float?, val loss: Float)

/** Orients a home/draw/away forecast for [teamId]. For non-head-to-head events there is no side to pick. */
fun Prediction.forTeam(fixture: Fixture, teamId: String?): TeamOdds? {
    val h = home?.toFloat() ?: return null
    val a = away?.toFloat() ?: return null
    val d = draw?.toFloat()
    return if (fixture.away?.id == teamId) TeamOdds(a, d, h) else TeamOdds(h, d, a)
}

@Composable
fun ProbabilityRing(
    segments: List<Pair<Float, Color>>,
    modifier: Modifier = Modifier,
    size: Dp = 128.dp,
    stroke: Dp = 14.dp,
    center: @Composable () -> Unit = {},
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(segments) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
    }
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val arcSize = Size(this.size.width - sw, this.size.height - sw)
            val topLeft = Offset(sw / 2f, sw / 2f)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(sw))
            var start = -90f
            val gap = if (segments.size > 1) 6f else 0f
            for ((fraction, color) in segments) {
                val full = fraction * 360f
                val sweep = maxOf(full - gap, 0f) * progress.value
                if (sweep > 0f) drawArc(color, start + gap / 2f, sweep, false, topLeft, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
                start += full
            }
        }
        center()
    }
}

/** Horizontal home/draw/away bar whose segments grow in when first shown. */
@Composable
fun ProbabilityBar(
    home: Float,
    draw: Float?,
    away: Float,
    homeColor: Color,
    awayColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    drawColor: Color = MaterialTheme.extra.draw,
) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val spec = tween<Float>(1000, easing = FastOutSlowInEasing)
    val h by animateFloatAsState(if (started) home else 0f, spec, label = "homeShare")
    val d by animateFloatAsState(if (started) draw ?: 0f else 0f, spec, label = "drawShare")
    val a by animateFloatAsState(if (started) away else 0f, spec, label = "awayShare")
    Row(modifier.fillMaxWidth().height(height).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        if (h > 0.001f) Box(Modifier.weight(h).fillMaxHeight().background(homeColor))
        if (d > 0.001f) Box(Modifier.weight(d).fillMaxHeight().background(drawColor))
        if (a > 0.001f) Box(Modifier.weight(a).fillMaxHeight().background(awayColor))
        val rest = 1f - (h + d + a)
        if (rest > 0.001f) Spacer(Modifier.weight(rest))
    }
}

/** W/D/L for [teamId] in a finished fixture, or null when scores aren't plain numbers (cricket) or it isn't head-to-head. */
fun formOf(f: Fixture, teamId: String, teamName: String? = null): Char? {
    val h = f.homeScore?.toIntOrNull()
    val a = f.awayScore?.toIntOrNull()
    if (h == null || a == null) return cricketForm(f, teamName)
    val mine = when (teamId) { f.home?.id -> h to a; f.away?.id -> a to h; else -> return null }
    return when {
        mine.first > mine.second -> 'W'
        mine.first < mine.second -> 'L'
        else -> 'D'
    }
}

/** Cricket results are text ("India won by 4 wickets"), so read the winner from that. */
private fun cricketForm(f: Fixture, teamName: String?): Char? {
    val detail = f.statusDetail ?: return null
    if (teamName == null || f.status != "final") return null
    return when {
        detail.contains("won", ignoreCase = true) -> if (detail.contains(teamName, ignoreCase = true)) 'W' else 'L'
        detail.contains("tie", ignoreCase = true) || detail.contains("draw", ignoreCase = true) -> 'D'
        else -> null
    }
}

@Composable
fun FormChips(results: List<Char>, modifier: Modifier = Modifier, chip: Dp = 28.dp, spacing: Dp = 6.dp) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(spacing)) {
        results.forEachIndexed { index, result ->
            val scale = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                delay(index * 90L)
                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
            }
            val color = when (result) {
                'W' -> MaterialTheme.extra.win
                'L' -> MaterialTheme.extra.loss
                else -> MaterialTheme.extra.draw
            }
            Box(
                Modifier.size(chip).graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                    .clip(CircleShape).background(color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(result.toString(), style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.Bold)
            }
        }
    }
}
