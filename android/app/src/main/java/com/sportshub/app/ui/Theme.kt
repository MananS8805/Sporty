package com.sportshub.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

@Composable
fun SportsTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFF8AB4F8))
        else -> lightColorScheme(primary = Color(0xFF0B3D91))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Re-themes a subtree in a team's colours (Ferrari red, CSK yellow, ...). */
@Composable
fun TeamTheme(color: Color?, content: @Composable () -> Unit) {
    if (color == null) return content()
    val base = MaterialTheme.colorScheme
    MaterialTheme(
        colorScheme = base.copy(
            primary = color,
            onPrimary = if (color.luminance() > 0.5f) Color.Black else Color.White,
            primaryContainer = color.copy(alpha = 0.16f).compositeOver(base.surface),
            onPrimaryContainer = base.onSurface,
        ),
        content = content,
    )
}
