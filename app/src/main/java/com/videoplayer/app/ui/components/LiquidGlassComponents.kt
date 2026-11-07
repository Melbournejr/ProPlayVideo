package com.videoplayer.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class GlassThemePalette(
    val id: String,
    val name: String,
    val primaryGlow: Color,
    val secondaryGlow: Color,
    val tertiaryGlow: Color,
    val deepBgTop: Color = Color(0xFF060913),
    val deepBgMid: Color = Color(0xFF0B1124)
)

val GlassThemePresets = listOf(
    GlassThemePalette(
        id = "cyan_nebula",
        name = "🌌 Cyan Nebula (Xanh Ngọc - Tím)",
        primaryGlow = Color(0xFF00F0FF),
        secondaryGlow = Color(0xFF8A2BE2),
        tertiaryGlow = Color(0xFF0066FF)
    ),
    GlassThemePalette(
        id = "sunset_gold",
        name = "🌅 Sunset Gold (Vàng Kim - Đỏ Cam)",
        primaryGlow = Color(0xFFFFB703),
        secondaryGlow = Color(0xFFFB5607),
        tertiaryGlow = Color(0xFFFF006E),
        deepBgTop = Color(0xFF14080E),
        deepBgMid = Color(0xFF1F1018)
    ),
    GlassThemePalette(
        id = "emerald_aurora",
        name = "🌿 Emerald Aurora (Lục Bảo - Bạc Hà)",
        primaryGlow = Color(0xFF00F5D4),
        secondaryGlow = Color(0xFF10B981),
        tertiaryGlow = Color(0xFF00BBF9),
        deepBgTop = Color(0xFF04120E),
        deepBgMid = Color(0xFF09201B)
    ),
    GlassThemePalette(
        id = "crimson_ruby",
        name = "🌹 Crimson Ruby (Đỏ Ruby - Hồng Tím)",
        primaryGlow = Color(0xFFFF2A85),
        secondaryGlow = Color(0xFF9D4EDD),
        tertiaryGlow = Color(0xFFFF0055),
        deepBgTop = Color(0xFF14050C),
        deepBgMid = Color(0xFF220A18)
    ),
    GlassThemePalette(
        id = "sakura_cyber",
        name = "🌸 Sakura Cyber (Hồng Neon - Xanh Băng)",
        primaryGlow = Color(0xFFFF70A6),
        secondaryGlow = Color(0xFF00E5FF),
        tertiaryGlow = Color(0xFFC77DFF),
        deepBgTop = Color(0xFF0F0818),
        deepBgMid = Color(0xFF19102C)
    ),
    GlassThemePalette(
        id = "prisma_rainbow",
        name = "🌈 Prisma Gradient (Đa Sắc Cầu Vồng)",
        primaryGlow = Color(0xFF00F5D4),
        secondaryGlow = Color(0xFFFFBE0B),
        tertiaryGlow = Color(0xFFFF006E),
        deepBgTop = Color(0xFF080816),
        deepBgMid = Color(0xFF141028)
    )
)

fun getGlassPaletteById(id: String): GlassThemePalette {
    return GlassThemePresets.find { it.id == id } ?: GlassThemePresets.first()
}

val LocalGlassPalette = staticCompositionLocalOf { GlassThemePresets.first() }

@Composable
fun LiquidAmbientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = LocalGlassPalette.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        palette.deepBgTop,
                        palette.deepBgMid,
                        palette.deepBgTop
                    )
                )
            )
    ) {
        // Quầng sáng 1 (Góc trên trái)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            palette.primaryGlow.copy(alpha = 0.22f),
                            palette.tertiaryGlow.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = Offset(180f, 220f),
                        radius = 780f
                    )
                )
        )
        // Quầng sáng 2 (Góc giữa phải)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            palette.secondaryGlow.copy(alpha = 0.20f),
                            palette.primaryGlow.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        center = Offset(900f, 950f),
                        radius = 880f
                    )
                )
        )
        // Quầng sáng 3 (Dưới đáy)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            palette.tertiaryGlow.copy(alpha = 0.16f),
                            Color.Transparent
                        ),
                        center = Offset(300f, 1800f),
                        radius = 720f
                    )
                )
        )
        content()
    }
}

@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    tintColor: Color? = null,
    surfaceAlpha: Float = 0.10f,
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalGlassPalette.current
    val effectiveTint = tintColor ?: Color.White
    val shape = RoundedCornerShape(cornerRadius)

    val glassFill = Brush.linearGradient(
        colors = listOf(
            effectiveTint.copy(alpha = (surfaceAlpha + 0.08f).coerceAtMost(0.45f)),
            palette.primaryGlow.copy(alpha = if (highlighted) 0.14f else 0.04f),
            palette.deepBgMid.copy(alpha = 0.60f)
        ),
        start = Offset(0f, 0f),
        end = Offset(700f, 500f)
    )

    val specularBorder = if (highlighted) {
        Brush.linearGradient(
            colors = listOf(
                palette.primaryGlow,
                palette.secondaryGlow,
                palette.tertiaryGlow
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.55f),
                palette.secondaryGlow.copy(alpha = 0.22f),
                palette.primaryGlow.copy(alpha = 0.42f)
            ),
            start = Offset(0f, 0f),
            end = Offset(600f, 600f)
        )
    }

    val clickMod = if (onClick != null) Modifier.clickable { onClick() } else Modifier

    Column(
        modifier = modifier
            .clip(shape)
            .background(glassFill, shape)
            .border(BorderStroke(if (highlighted) 1.6.dp else 1.1.dp, specularBorder), shape)
            .then(clickMod),
        content = content
    )
}

@Composable
fun LiquidGlassPill(
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val palette = LocalGlassPalette.current
    val shape = CircleShape
    val fillBrush = if (active) {
        Brush.horizontalGradient(
            colors = listOf(
                palette.primaryGlow.copy(alpha = 0.40f),
                palette.secondaryGlow.copy(alpha = 0.40f),
                palette.tertiaryGlow.copy(alpha = 0.35f)
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
            colors = listOf(palette.primaryGlow, palette.secondaryGlow)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.45f),
                palette.primaryGlow.copy(alpha = 0.20f)
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