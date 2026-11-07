package com.videoplayer.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.videoplayer.app.ui.components.GlassThemePalette
import com.videoplayer.app.ui.components.GlassThemePresets
import com.videoplayer.app.ui.components.LocalGlassPalette

@Composable
fun VideoPlayerTheme(
    palette: GlassThemePalette = GlassThemePresets.first(),
    content: @Composable () -> Unit
) {
    val scheme = darkColorScheme(
        primary = palette.primaryGlow,
        onPrimary = Color(0xFF001F29),
        primaryContainer = palette.deepBgMid,
        onPrimaryContainer = Color.White,
        secondary = palette.secondaryGlow,
        onSecondary = Color.White,
        background = palette.deepBgTop,
        onBackground = Color(0xFFF4F8FF),
        surface = palette.deepBgMid,
        onSurface = Color(0xFFF4F8FF),
        onSurfaceVariant = Color(0xFFB0C4DE),
        error = Color(0xFFFF5C8A)
    )

    CompositionLocalProvider(LocalGlassPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            content = content
        )
    }
}