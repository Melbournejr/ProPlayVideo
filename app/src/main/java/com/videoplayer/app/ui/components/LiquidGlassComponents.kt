package com.videoplayer.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class GlassThemePalette(
    val id: String,
    val name: String,
    val primaryGlow: Color,
    val secondaryGlow: Color,
    val tertiaryGlow: Color,
    val deepBgTop: Color = Color(0xFF050814),
    val deepBgMid: Color = Color(0xFF0C1428)
)

data class GlassTintOption(
    val id: String,
    val name: String,
    val surfaceColor: Color,
    val rimTint: Color
)

data class GlassStyleConfig(
    val palette: GlassThemePalette = GlassThemePresets.first(),
    val tint: GlassTintOption = GlassTintPresets.first(),
    val frostAlpha: Float = 0.16f
)

val GlassThemePresets = listOf(
    GlassThemePalette(
        id = "apple_ice",
        name = " Apple Ice Crystal (visionOS Chuẩn)",
        primaryGlow = Color(0xFF5AC8FA),
        secondaryGlow = Color(0xFFBF5AF2),
        tertiaryGlow = Color(0xFF0A84FF),
        deepBgTop = Color(0xFF070B16),
        deepBgMid = Color(0xFF121C36)
    ),
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
        deepBgMid = Color(0xFF22111B)
    ),
    GlassThemePalette(
        id = "emerald_aurora",
        name = "🌿 Emerald Aurora (Lục Bảo - Bạc Hà)",
        primaryGlow = Color(0xFF00F5D4),
        secondaryGlow = Color(0xFF10B981),
        tertiaryGlow = Color(0xFF00BBF9),
        deepBgTop = Color(0xFF04120E),
        deepBgMid = Color(0xFF09221C)
    ),
    GlassThemePalette(
        id = "crimson_ruby",
        name = "🌹 Crimson Ruby (Đỏ Ruby - Hồng Tím)",
        primaryGlow = Color(0xFFFF2A85),
        secondaryGlow = Color(0xFF9D4EDD),
        tertiaryGlow = Color(0xFFFF0055),
        deepBgTop = Color(0xFF14050C),
        deepBgMid = Color(0xFF240B1A)
    ),
    GlassThemePalette(
        id = "prisma_rainbow",
        name = "🌈 Prisma Gradient (Đa Sắc Cầu Vồng)",
        primaryGlow = Color(0xFF00F5D4),
        secondaryGlow = Color(0xFFFFBE0B),
        tertiaryGlow = Color(0xFFFF006E),
        deepBgTop = Color(0xFF080816),
        deepBgMid = Color(0xFF15112B)
    )
)

val GlassTintPresets = listOf(
    GlassTintOption(
        id = "crystal_white",
        name = "💎 Pha lê Trắng (Apple Frost)",
        surfaceColor = Color(0xFFFFFFFF),
        rimTint = Color(0xFFFFFFFF)
    ),
    GlassTintOption(
        id = "ice_cyan",
        name = "🧊 Xanh Băng Giá (Glacier Cyan)",
        surfaceColor = Color(0xFF64D2FF),
        rimTint = Color(0xFF90E0EF)
    ),
    GlassTintOption(
        id = "amethyst_violet",
        name = "🔮 Tím Thạch Anh (Amethyst Glass)",
        surfaceColor = Color(0xFFC77DFF),
        rimTint = Color(0xFFE0AAFF)
    ),
    GlassTintOption(
        id = "rose_champagne",
        name = "🥂 Vàng Hồng (Rose Champagne)",
        surfaceColor = Color(0xFFFFB5A7),
        rimTint = Color(0xFFFFD6A5)
    ),
    GlassTintOption(
        id = "emerald_jade",
        name = "翡翠 Ngọc Lục Bảo (Emerald Jade)",
        surfaceColor = Color(0xFF52B788),
        rimTint = Color(0xFF95D5B2)
    ),
    GlassTintOption(
        id = "ruby_crimson",
        name = "🍷 Đỏ Hồng Ngọc (Ruby Wine)",
        surfaceColor = Color(0xFFFF4D6D),
        rimTint = Color(0xFFFF8FA3)
    ),
    GlassTintOption(
        id = "smoky_obsidian",
        name = "🖤 Kính Khói Đen (Smoky Obsidian)",
        surfaceColor = Color(0xFF1E293B),
        rimTint = Color(0xFF94A3B8)
    )
)

fun getGlassPaletteById(id: String): GlassThemePalette {
    return GlassThemePresets.find { it.id == id } ?: GlassThemePresets.first()
}

fun getGlassTintById(id: String): GlassTintOption {
    return GlassTintPresets.find { it.id == id } ?: GlassTintPresets.first()
}

val LocalGlassPalette = staticCompositionLocalOf { GlassThemePresets.first() }
val LocalGlassStyle = staticCompositionLocalOf { GlassStyleConfig() }

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
            .drawWithCache {
                val w = size.width
                val h = size.height
                val orb1 = Brush.radialGradient(
                    colors = listOf(
                        palette.primaryGlow.copy(alpha = 0.34f),
                        palette.tertiaryGlow.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.15f, h * 0.12f),
                    radius = w * 0.85f
                )
                val orb2 = Brush.radialGradient(
                    colors = listOf(
                        palette.secondaryGlow.copy(alpha = 0.28f),
                        palette.primaryGlow.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.88f, h * 0.45f),
                    radius = w * 0.90f
                )
                val orb3 = Brush.radialGradient(
                    colors = listOf(
                        palette.tertiaryGlow.copy(alpha = 0.25f),
                        palette.secondaryGlow.copy(alpha = 0.06f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.25f, h * 0.85f),
                    radius = w * 0.80f
                )
                onDrawBehind {
                    drawRect(orb1)
                    drawRect(orb2)
                    drawRect(orb3)
                }
            }
    ) {
        content()
    }
}

@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 26.dp,
    tintColor: Color? = null,
    surfaceAlpha: Float? = null,
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val style = LocalGlassStyle.current
    val palette = style.palette
    val glassTint = style.tint
    val baseAlpha = (surfaceAlpha ?: style.frostAlpha).coerceIn(0.04f, 0.50f)

    val shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "glass_scale"
    )

    val clickMod = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    } else Modifier

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (highlighted) 22.dp else 14.dp,
                shape = shape,
                ambientColor = if (highlighted) palette.primaryGlow.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.6f),
                spotColor = if (highlighted) palette.primaryGlow.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.7f)
            )
            .clip(shape)
            .drawWithCache {
                val cr = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())
                val w = size.width
                val h = size.height
                val activeSurfaceTint = tintColor ?: glassTint.surfaceColor

                // 1. Lớp nền kính mờ theo màu kính và độ đục người dùng chọn
                val baseFill = Brush.linearGradient(
                    colors = listOf(
                        activeSurfaceTint.copy(alpha = (baseAlpha + if (isPressed) 0.10f else if (highlighted) 0.08f else 0.04f).coerceAtMost(0.65f)),
                        glassTint.surfaceColor.copy(alpha = baseAlpha * 0.65f),
                        palette.deepBgMid.copy(alpha = (baseAlpha + 0.25f).coerceAtMost(0.75f)),
                        activeSurfaceTint.copy(alpha = baseAlpha * 0.45f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                )

                // 2. Vòm phản quang mặt kính trên (Top Curved Glass Sheen)
                val topSheen = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isPressed) 0.30f else 0.20f),
                        glassTint.rimTint.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = h * 0.52f
                )

                // 3. Viền khúc xạ lăng kính ngoài (Outer Specular Rim)
                val outerRim = if (highlighted) {
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.95f),
                            palette.primaryGlow.copy(alpha = 0.85f),
                            palette.secondaryGlow.copy(alpha = 0.70f),
                            glassTint.rimTint.copy(alpha = 0.65f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(w, h)
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            glassTint.rimTint.copy(alpha = 0.82f),
                            glassTint.rimTint.copy(alpha = 0.22f),
                            palette.primaryGlow.copy(alpha = 0.22f),
                            glassTint.rimTint.copy(alpha = 0.48f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(w, h)
                    )
                }

                // 4. Đường vát sáng 3D mép đỉnh trong
                val innerHighlight = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        glassTint.rimTint.copy(alpha = 0.68f),
                        Color.White.copy(alpha = 0.90f),
                        glassTint.rimTint.copy(alpha = 0.68f),
                        Color.Transparent
                    )
                )

                onDrawBehind {
                    drawRoundRect(brush = baseFill, cornerRadius = cr)
                    drawRoundRect(brush = topSheen, cornerRadius = cr)
                    drawRoundRect(
                        brush = outerRim,
                        cornerRadius = cr,
                        style = Stroke(width = if (highlighted) 1.8.dp.toPx() else 1.2.dp.toPx())
                    )
                    drawLine(
                        brush = innerHighlight,
                        start = Offset(w * 0.12f, 1.5.dp.toPx()),
                        end = Offset(w * 0.88f, 1.5.dp.toPx()),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
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
    val style = LocalGlassStyle.current
    val palette = style.palette
    val glassTint = style.tint
    val shape = CircleShape
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "pill_scale"
    )

    val clickMod = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    } else Modifier

    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (active) 12.dp else 6.dp,
                shape = shape,
                ambientColor = if (active) palette.primaryGlow.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.4f),
                spotColor = if (active) palette.primaryGlow.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.4f)
            )
            .clip(shape)
            .drawWithCache {
                val cr = CornerRadius(size.height / 2f, size.height / 2f)
                val fillBrush = if (active) {
                    Brush.linearGradient(
                        colors = listOf(
                            glassTint.surfaceColor.copy(alpha = 0.35f),
                            palette.primaryGlow.copy(alpha = 0.50f),
                            palette.secondaryGlow.copy(alpha = 0.45f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height)
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            glassTint.surfaceColor.copy(alpha = (style.frostAlpha + if (isPressed) 0.14f else 0.06f).coerceAtMost(0.55f)),
                            glassTint.surfaceColor.copy(alpha = style.frostAlpha * 0.5f)
                        )
                    )
                }

                val rimBrush = if (active) {
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.95f),
                            palette.primaryGlow,
                            glassTint.rimTint.copy(alpha = 0.65f)
                        )
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            glassTint.rimTint.copy(alpha = 0.72f),
                            glassTint.rimTint.copy(alpha = 0.18f),
                            glassTint.rimTint.copy(alpha = 0.42f)
                        )
                    )
                }

                onDrawBehind {
                    drawRoundRect(brush = fillBrush, cornerRadius = cr)
                    drawRoundRect(
                        brush = rimBrush,
                        cornerRadius = cr,
                        style = Stroke(width = 1.15.dp.toPx())
                    )
                }
            }
            .then(clickMod)
            .padding(horizontal = 15.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content
    )
}