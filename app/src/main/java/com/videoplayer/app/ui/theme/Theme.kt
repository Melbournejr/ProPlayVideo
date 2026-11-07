package com.videoplayer.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

@Composable
fun VideoPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFF6200EE),
            primaryContainer = Color(0xFFEADDFF),
            secondary = Color(0xFF03DAC6),
            surface = Color(0xFF000000),
            background = Color(0xFF000000),
            error = Color(0xFFB00020),
            onPrimary = Color(0xFFFFFFFF),
            onBackground = Color(0xFFFFFFFF),
            onSurface = Color(0xFFFFFFFF),
            onError = Color(0xFFFFFFFF)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF6200EE),
            primaryContainer = Color(0xFFEADDFF),
            secondary = Color(0xFF03DAC6),
            surface = Color(0xFFFFFFFF),
            background = Color(0xFFFFFFFF),
            error = Color(0xFFB00020),
            onPrimary = Color(0xFFFFFFFF),
            onBackground = Color(0xFF000000),
            onSurface = Color(0xFF000000),
            onError = Color(0xFFFFFFFF)
        )
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(
            body1 = TextStyle(fontSize = 16.sp),
            labelLarge = TextStyle(fontSize = 14.sp)
        ),
        content = content
    )
}
