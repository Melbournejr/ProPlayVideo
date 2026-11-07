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
import androidx.compose.ui.geometry.Size
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

fun getGlassPaletteById(id: String): GlassThemePalette {
    return GlassThemePresets.find { it.id == id } ?: GlassThemePresets.first()
}

val LocalGlassPalette = staticCompositionLocalOf { GlassThemePresets.first() }

/**
 * Nền không gian Apple Mesh (Organic Liquid Mesh) làm nổi bật độ trong suốt của lớp kính phía trên
 */
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

/**
 * Thẻ Kính Lỏng chuẩn Apple (visionOS / iOS 26 Liquid Glass):
 * - Có hiệu ứng đàn hồi vật lý khi nhấn (Spring Scale)
 * - Bề mặt sương mờ quang học (Frosted Milk Layer)
 * - Khúc xạ vòm kính phía trên (Top Specular Sheen)
 * - Viền lăng kính 3D kép (Outer Specular Rim + Inner Bevel Highlight)
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 26.dp,
    tintColor: Color? = null,
    surfaceAlpha: Float = 0.14f,
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalGlassPalette.current
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

                // 1. Lớp nền kính mờ (Frosted Milk + Ambient Tint)
                val baseFill = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isPressed) 0.24f else if (highlighted) 0.20f else surfaceAlpha + 0.05f),
                        (tintColor ?: palette.primaryGlow).copy(alpha = if (highlighted) 0.16f else 0.06f),
                        Color(0xFF10192E).copy(alpha = 0.42f),
                        Color.White.copy(alpha = 0.08f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                )

                // 2. Vòm phản quang mặt kính trên (Top Curved Glass Sheen)
                val topSheen = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isPressed) 0.28f else 0.18f),
                        Color.White.copy(alpha = 0.03f),
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
                            Color.White.copy(alpha = 0.55f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(w, h)
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.78f),
                            Color.White.copy(alpha = 0.20f),
                            palette.primaryGlow.copy(alpha = 0.18f),
                            Color.White.copy(alpha = 0.42f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(w, h)
                    )
                }

                // 4. Đường vát sáng 3D mép đỉnh trong (Inner Bevel Highlight)
                val innerHighlight = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.65f),
                        Color.White.copy(alpha = 0.85f),
                        Color.White.copy(alpha = 0.65f),
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
                    // Vẽ đường sáng mảnh dọc mép trên tạo cảm giác kính dày
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

/**
 * Viên thuốc Kính Lỏng Apple (Capsule Glass Pill)
 */
@Composable
fun LiquidGlassPill(
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val palette = LocalGlassPalette.current
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
                            Color.White.copy(alpha = 0.32f),
                            palette.primaryGlow.copy(alpha = 0.48f),
                            palette.secondaryGlow.copy(alpha = 0.42f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height)
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isPressed) 0.30f else 0.22f),
                            Color.White.copy(alpha = 0.08f)
                        )
                    )
                }

                val rimBrush = if (active) {
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.95f),
                            palette.primaryGlow,
                            Color.White.copy(alpha = 0.60f)
                        )
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.70f),
                            Color.White.copy(alpha = 0.15f),
                            Color.White.copy(alpha = 0.38f)
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