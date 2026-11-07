package com.videoplayer.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LiquidGlassColorScheme = darkColorScheme(
    primary = Color(0xFF00E5FF),
    onPrimary = Color(0xFF002633),
    primaryContainer = Color(0xFF0F2942),
    onPrimaryContainer = Color(0xFFB8F4FF),
    secondary = Color(0xFFC77DFF),
    onSecondary = Color(0xFF240046),
    background = Color(0xFF070B19),
    onBackground = Color(0xFFF4F8FF),
    surface = Color(0xFF101930),
    onSurface = Color(0xFFF4F8FF),
    onSurfaceVariant = Color(0xFFA7B8D8),
    error = Color(0xFFFF5C8A)
)

@Composable
fun VideoPlayerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LiquidGlassColorScheme,
        content = content
    )
}