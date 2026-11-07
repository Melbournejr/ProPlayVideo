package com.videoplayer.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object LiquidGlassColors {
    val DeepSpace = Color(0xFF070B19)
    valCyanGlow = Color(0xFF00F0FF)
    val VioletGlow = Color(0xFF8A2BE2)
    val BlueGlow = Color(0xFF0066FF)
    val TextPrimary = Color(0xFFF5F9FF)
    val TextSecondary = Color(0xFFA6B9D8)
}

/**
 * Nền không gian đa chiều với các quầng sáng lỏng (Ambient Liquid Orbs)
 */
@Composable
fun LiquidAmbientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF060913),
                        Color(0xFF0B1124),
                        Color(0xFF070A16)
                    )
                )
            )
    ) {
        // Quầng sáng Cyan góc trên trái
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF00E5FF).copy(alpha = 0.20f),
                            Color(0xFF0066FF).copy(alpha = 0.06f),
                            Color.Transparent
                        ),
                        center = Offset(180f, 220f),
                        radius = 750f
                    )
                )
        )
        // Quầng sáng Tím Violet góc giữa phải
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF9D4EDD).copy(alpha = 0.18f),
                            Color(0xFF5A189A).copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        center = Offset(900f, 950f),
                        radius = 850f
                    )
                )
        )
        // Quầng sáng Ngọc bích dưới đáy
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF00BBF9).copy(alpha = 0.14f),
                            Color.Transparent
                        ),
                        center = Offset(300f, 1800f),
                        radius = 700f
                    )
                )
        )
        content()
    }
}

/**
 * Thẻ Kính Lỏng (Liquid Glass Card) với bề mặt khúc xạ ánh sáng và viền Specular 3D
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    tintColor: Color = Color.White,
    surfaceAlpha: Float = 0.10f,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val glassFill = Brush.linearGradient(
        colors = listOf(
            tintColor.copy(alpha = (surfaceAlpha + 0.08f).coerceAtMost(0.4f)),
            tintColor.copy(alpha = surfaceAlpha * 0.5f),
            Color(0xFF0D162C).copy(alpha = 0.55f)
        ),
        start = Offset(0f, 0f),
        end = Offset(700f, 500f)
    )
    // Viền khúc xạ: sáng lấp lánh ở mép trên-trái, mờ dần ở giữa và phản quang Cyan ở góc dưới-phải
    val specularBorder = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.55f),
            Color.White.copy(alpha = 0.12f),
            Color(0xFF00E5FF).copy(alpha = 0.35f)
        ),
        start = Offset(0f, 0f),
        end = Offset(600f, 600f)
    )

    val clickMod = if (onClick != null) Modifier.clickable { onClick() } else Modifier

    Column(
        modifier = modifier
            .clip(shape)
            .background(glassFill, shape)
            .border(BorderStroke(1.1.dp, specularBorder), shape)
            .then(clickMod),
        content = content
    )
}

/**
 * Viên nang Kính Lỏng (Liquid Glass Pill) dùng cho các nút bấm, nhãn và HUD
 */
@Composable
fun LiquidGlassPill(
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val shape = CircleShape
    val fillBrush = if (active) {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF00E5FF).copy(alpha = 0.35f),
                Color(0xFF7B2CBF).copy(alpha = 0.35f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.16f),
                Color.White.copy(alpha = 0.06f)
            )
        )
    }

    val borderBrush = if (active) {
        Brush.horizontalGradient(
            colors = listOf(Color(0xFF00F0FF), Color(0xFFE0AAFF))
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.45f),
                Color.White.copy(alpha = 0.10f)
            )
        )
    }

    val clickMod = if (onClick != null) Modifier.clickable { onClick() } else Modifier

    Row(
        modifier = modifier
            .clip(shape)
            .background(fillBrush, shape)
            .border(BorderStroke(1.dp, borderBrush), shape)
            .then(clickMod)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content
    )
}