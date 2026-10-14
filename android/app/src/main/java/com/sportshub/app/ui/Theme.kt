package com.sportshub.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Colours Material3 has no slot for. */
data class ExtraColors(val live: Color, val win: Color, val loss: Color, val draw: Color)

private val LocalExtraColors = staticCompositionLocalOf { ExtraColors(Color.Red, Color.Green, Color.Red, Color.Gray) }

val MaterialTheme.extra: ExtraColors
    @Composable @ReadOnlyComposable get() = LocalExtraColors.current

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7C9BFF), onPrimary = Color(0xFF0B1330),
    primaryContainer = Color(0xFF1E2A55), onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = Color(0xFF9AA3B5), onSecondary = Color(0xFF0A0C10),
    background = Color(0xFF0A0C10), onBackground = Color(0xFFF2F4F8),
    surface = Color(0xFF0A0C10), onSurface = Color(0xFFF2F4F8),
    surfaceVariant = Color(0xFF1C2230), onSurfaceVariant = Color(0xFF9AA3B5),
    surfaceContainerLowest = Color(0xFF07080B), surfaceContainerLow = Color(0xFF10131A),
    surfaceContainer = Color(0xFF151922), surfaceContainerHigh = Color(0xFF1C2230), surfaceContainerHighest = Color(0xFF252C3D),
    outline = Color(0xFF3A4358), outlineVariant = Color(0xFF262D3D), error = Color(0xFFFF5C6C),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF3558E6), onPrimary = Color.White,
    primaryContainer = Color(0xFFDFE5FF), onPrimaryContainer = Color(0xFF0B1330),
    secondary = Color(0xFF5B6478), onSecondary = Color.White,
    background = Color(0xFFF4F5F8), onBackground = Color(0xFF10131A),
    surface = Color(0xFFF4F5F8), onSurface = Color(0xFF10131A),
    surfaceVariant = Color(0xFFEBEEF4), onSurfaceVariant = Color(0xFF5B6478),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFAFBFD),
    surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFEEF0F5), surfaceContainerHighest = Color(0xFFE3E7EF),
    outline = Color(0xFFB6BDCC), outlineVariant = Color(0xFFDDE1EA), error = Color(0xFFD7263D),
)

private val DarkExtra = ExtraColors(live = Color(0xFFFF4D5E), win = Color(0xFF3DDC97), loss = Color(0xFFFF6B7A), draw = Color(0xFF8A93A6))
private val LightExtra = ExtraColors(live = Color(0xFFE0243A), win = Color(0xFF138A55), loss = Color(0xFFD7263D), draw = Color(0xFF7C8598))

private val Sans = FontFamily.SansSerif

private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.ExtraBold, fontSize = 52.sp, lineHeight = 56.sp, letterSpacing = (-1.8).sp),
    displayMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.ExtraBold, fontSize = 38.sp, lineHeight = 42.sp, letterSpacing = (-1.2).sp),
    displaySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.8).sp),
    headlineLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.5.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp), extraLarge = RoundedCornerShape(34.dp),
)

@Composable
fun SportsTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalExtraColors provides if (dark) DarkExtra else LightExtra) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

/** Readable text colour for a given background. */
fun onColor(background: Color): Color = if (background.luminance() > 0.45f) Color(0xFF10131A) else Color.White

/** Nudges a team colour so it stays readable as text or an accent: lighter on dark surfaces, darker on light ones. */
@Composable
fun legibleAccent(color: Color): Color {
    val dark = isSystemInDarkTheme()
    val l = color.luminance()
    return when {
        dark && l < 0.28f -> lerp(color, Color.White, 0.42f)
        !dark && l > 0.6f -> lerp(color, Color.Black, 0.42f)
        else -> color
    }
}

/** Re-themes a subtree in a team's colours (Ferrari red, CSK yellow, ...), fading between teams. */
@Composable
fun TeamTheme(color: Color?, content: @Composable () -> Unit) {
    val base = MaterialTheme.colorScheme
    val target = color?.let { legibleAccent(it) } ?: base.primary
    val primary = animateColorAsState(target, tween(450), label = "teamPrimary").value
    val scheme: ColorScheme = base.copy(
        primary = primary,
        onPrimary = onColor(primary),
        primaryContainer = primary.copy(alpha = 0.18f).compositeOver(base.surfaceContainer),
        onPrimaryContainer = base.onSurface,
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
