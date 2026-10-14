package com.sportshub.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

/** A moving highlight over a placeholder shape, shown while content loads. */
fun Modifier.shimmer(shape: Shape = RoundedCornerShape(14.dp)): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "shimmerProgress")
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    this.clip(shape).drawWithCache {
        val w = size.width
        onDrawBehind {
            val start = -w + progress * 3f * w
            drawRect(Brush.horizontalGradient(listOf(base, highlight, base), startX = start, endX = start + w))
        }
    }
}

/** Fades and slides an item in once when it first appears, staggered by [index]. */
fun Modifier.entrance(index: Int = 0, offset: Dp = 28.dp): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(minOf(index, 8) * 55L)
        progress.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * offset.toPx()
    }
}

@Composable
fun LiveDot(modifier: Modifier = Modifier, color: Color = MaterialTheme.extra.live, size: Dp = 8.dp) {
    val transition = rememberInfiniteTransition(label = "live")
    val alpha by transition.animateFloat(
        1f, 0.2f, infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "liveAlpha",
    )
    Box(modifier.size(size).graphicsLayer { this.alpha = alpha }.background(color, CircleShape))
}

/** Number that counts up from zero the first time it is shown, and animates between later values. */
@Composable
fun rememberCountUp(target: Float, durationMs: Int = 900): Float {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    return animateFloatAsState(if (started) target else 0f, tween(durationMs, easing = FastOutSlowInEasing), label = "countUp").value
}

@Composable
fun CountUpText(target: Float, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.displaySmall, suffix: String = "", color: Color = Color.Unspecified) {
    Text("${rememberCountUp(target).toInt()}$suffix", modifier, style = style, color = color)
}

/** Ticks once a second while it is on screen. */
@Composable
fun rememberNow(): Instant = produceState(Instant.now()) {
    while (true) {
        delay(1000)
        value = Instant.now()
    }
}.value

fun countdownText(kickoff: Instant, now: Instant): String {
    val d = Duration.between(now, kickoff)
    return when {
        d.isNegative -> "Started"
        d.toDays() >= 2 -> "${d.toDays()}d ${d.toHours() % 24}h"
        d.toHours() >= 1 -> "${d.toHours()}h ${d.toMinutes() % 60}m"
        else -> "%02d:%02d".format(d.toMinutes(), d.seconds % 60)
    }
}

@Composable
fun Countdown(iso: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.titleLarge, color: Color = Color.Unspecified) {
    val kickoff = remember(iso) { runCatching { Instant.parse(iso) }.getOrNull() }
    if (kickoff == null) return
    Text(countdownText(kickoff, rememberNow()), modifier, style = style, color = color)
}
