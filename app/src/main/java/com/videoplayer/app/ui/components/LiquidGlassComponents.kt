package com.videoplayer.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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

enum class LiquidGlassVariant {
    REGULAR,
    CLEAR
}

/**
 * 4 Bo Theme doi toan dien phong cach hien thi (UI Skin & Layout Engine):
 * 1. LIQUID_GLASS: Apple Liquid Glass (Ho tro ca Dark Midnight & Light Daylight)
 * 2. CINEMA_SHOWCASE: Phong cach Rap phim Netflix / Apple TV+ (Hero Banner + Poster Cards + Frameless HUD)
 * 3. PRO_MONITOR: Phong cach Walkman Hi-Res / Dung phim (Vuong vuc 5dp + Van kim loai + Font Monospace + Ruler SeekBar)
 * 4. PURE_AMOLED: Phong cach Nothing OS / Swiss Minimal (Den #000000 + Khong khung Card + Duong ke manh 0.5dp)
 */
enum class ThemeEngineId(val id: String, val title: String, val subtitle: String, val badge: String) {
    LIQUID_GLASS(
        "liquid_glass",
        "Apple Liquid Glass",
        "K\u00EDnh quang h\u1ECDc 5 l\u1EDBp, h\u1ED7 tr\u1EE3 ch\u1EBF \u0111\u1ED9 T\u1ED1i & S\u00E1ng",
        "Glass"
    ),
    CINEMA_SHOWCASE(
        "cinema_showcase",
        "Cinema Showcase",
        "Hero Banner tr\u00E0n vi\u1EC1n, th\u1EBB \u1EA3nh l\u1EDBn & HUD r\u1EA1p phim",
        "Netflix"
    ),
    PRO_MONITOR(
        "pro_monitor",
        "Pro Monitor Hi-Res",
        "Kim lo\u1EA1i phay x\u01B0\u1EDBc vu\u00F4ng v\u1EE9c, font k\u1EF9 thu\u1EADt & th\u01B0\u1EDBc chia",
        "Walkman"
    ),
    PURE_AMOLED(
        "pure_amoled",
        "Pure AMOLED Minimal",
        "\u0110en tuy\u1EC7t \u0111\u1ED1i #000000, kh\u00F4ng khung card, \u0111\u01B0\u1EDDng k\u1EBB m\u1EA3nh",
        "OLED"
    )
}

data class ThemeSubVariant(
    val id: String,
    val engineId: String,
    val name: String,
    val primaryAccent: Color,
    val secondaryAccent: Color,
    val bgTop: Color,
    val bgMid: Color,
    val bgBottom: Color,
    val cardSurface: Color
)

data class GlassPalette(
    val id: String,
    val name: String,
    val primaryGlow: Color,
    val secondaryGlow: Color,
    val tertiaryGlow: Color,
    val deepBgTop: Color,
    val deepBgMid: Color,
    val deepBgBottom: Color
)

data class GlassTintOption(
    val id: String,
    val name: String,
    val surfaceColor: Color,
    val rimTint: Color
)

data class GlassStyleConfig(
    val engineId: String = ThemeEngineId.LIQUID_GLASS.id,
    val subVariantId: String = "cinema_imax",
    val palette: GlassPalette = GlassThemePresets.first(),
    val tint: GlassTintOption = GlassTintPresets.first(),
    val frostAlpha: Float = 0.20f,
    val reduceTransparency: Boolean = false,
    val isLightMode: Boolean = false
) {
    val isCinema: Boolean get() = engineId == ThemeEngineId.CINEMA_SHOWCASE.id
    val isProMonitor: Boolean get() = engineId == ThemeEngineId.PRO_MONITOR.id
    val isPureAmoled: Boolean get() = engineId == ThemeEngineId.PURE_AMOLED.id
    val isLiquidGlass: Boolean get() = engineId == ThemeEngineId.LIQUID_GLASS.id

    val textPrimary: Color get() = if (isLightMode) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    val textSecondary: Color get() = if (isLightMode) Color(0xFF475569) else Color(0xFF94A3B8)
    val cardSurfaceColor: Color
        get() = when {
            isLightMode -> Color.White.copy(alpha = 0.92f)
            isPureAmoled -> Color.Black
            isProMonitor -> tint.surfaceColor
            isCinema -> Color(0xFF141118)
            else -> Color(0xFF121722).copy(alpha = 0.88f)
        }
    val cardBorderColor: Color
        get() = when {
            isLightMode -> Color(0xFF0F172A).copy(alpha = 0.08f)
            isPureAmoled -> Color.White.copy(alpha = 0.14f)
            isProMonitor -> palette.primaryGlow.copy(alpha = 0.48f)
            isCinema -> Color.White.copy(alpha = 0.10f)
            else -> Color.White.copy(alpha = 0.08f)
        }
    val thumbCorner: Dp
        get() = when {
            isProMonitor -> 4.dp
            isPureAmoled -> 6.dp
            isCinema -> 16.dp
            else -> 12.dp
        }
    val cardCorner: Dp
        get() = when {
            isProMonitor -> 6.dp
            isPureAmoled -> 0.dp
            isCinema -> 18.dp
            else -> 20.dp
        }
    val useMonoMetrics: Boolean get() = isProMonitor
}

val GlassTintPresets = listOf(
    GlassTintOption("crystal_white", "Pha l\u00EA Trong Su\u1ED1t (Optical Clear)", Color(0xFF1A2230), Color(0xFFFFFFFF)),
    GlassTintOption("glacier_cyan", "B\u0103ng Lam Kh\u00FAc X\u1EA1 (Glacier)", Color(0xFF142834), Color(0xFFB8F2E6)),
    GlassTintOption("amethyst_purple", "Th\u1EA1ch Anh T\u00EDm (Amethyst)", Color(0xFF211A30), Color(0xFFE5D4ED)),
    GlassTintOption("rose_gold", "V\u00E0ng H\u1ED3ng Champagne", Color(0xFF281F1E), Color(0xFFFFDFD3)),
    GlassTintOption("emerald_jade", "Ng\u1ECDc L\u1EE5c B\u1EA3o (Emerald)", Color(0xFF142620), Color(0xFFD8F3DC)),
    GlassTintOption("obsidian_smoke", "K\u00EDnh Kh\u00F3i Titan (Graphite)", Color(0xFF12151B), Color(0xFFCFD8DC))
)

val GlassThemePresets = listOf(
    GlassPalette(
        id = "apple_ice",
        name = "Apple System Blue",
        primaryGlow = Color(0xFF0EA5E9),
        secondaryGlow = Color(0xFF6366F1),
        tertiaryGlow = Color(0xFFA855F7),
        deepBgTop = Color(0xFF070A10),
        deepBgMid = Color(0xFF0C111C),
        deepBgBottom = Color(0xFF05070C)
    ),
    GlassPalette(
        id = "aurora_emerald",
        name = "Apple Mint & Teal",
        primaryGlow = Color(0xFF10B981),
        secondaryGlow = Color(0xFF14B8A6),
        tertiaryGlow = Color(0xFF0EA5E9),
        deepBgTop = Color(0xFF060E0D),
        deepBgMid = Color(0xFF0A1715),
        deepBgBottom = Color(0xFF040908)
    ),
    GlassPalette(
        id = "sunset_magenta",
        name = "Apple Pink & Indigo",
        primaryGlow = Color(0xFFEC4899),
        secondaryGlow = Color(0xFF8B5CF6),
        tertiaryGlow = Color(0xFFF43F5E),
        deepBgTop = Color(0xFF0E0812),
        deepBgMid = Color(0xFF160D1E),
        deepBgBottom = Color(0xFF08040B)
    ),
    GlassPalette(
        id = "gold_champagne",
        name = "Apple Starlight Amber",
        primaryGlow = Color(0xFFF59E0B),
        secondaryGlow = Color(0xFFD97706),
        tertiaryGlow = Color(0xFF0EA5E9),
        deepBgTop = Color(0xFF0F0C07),
        deepBgMid = Color(0xFF18140C),
        deepBgBottom = Color(0xFF090704)
    )
)

val ThemeSubVariants = listOf(
    // 1. Cinema Showcase
    ThemeSubVariant("cinema_netflix", "cinema_showcase", "R\u1EA1p \u0110\u1ECF Netflix (Crimson Cinema)", Color(0xFFE50914), Color(0xFFFF5252), Color(0xFF140708), Color(0xFF0F090A), Color(0xFF080506), Color(0xFF1A1113)),
    ThemeSubVariant("cinema_imax", "cinema_showcase", "Ph\u00F2ng Chi\u1EBFu IMAX (Midnight Gold)", Color(0xFFFFC107), Color(0xFF60A5FA), Color(0xFF080E18), Color(0xFF0C1422), Color(0xFF05080F), Color(0xFF121C2E)),
    ThemeSubVariant("cinema_velvet", "cinema_showcase", "Nhung T\u00EDm VIP (Velvet Lounge)", Color(0xFFE879F9), Color(0xFFFBBF24), Color(0xFF120818), Color(0xFF1A0D24), Color(0xFF09040D), Color(0xFF221230)),

    // 2. Pro Monitor Hi-Res
    ThemeSubVariant("pro_gold", "pro_monitor", "V\u00E0ng \u0110\u1ED3ng Signature (Walkman WM1Z)", Color(0xFFD4AF37), Color(0xFFFFDF73), Color(0xFF131316), Color(0xFF1B1C20), Color(0xFF0B0B0D), Color(0xFF212228)),
    ThemeSubVariant("pro_titanium", "pro_monitor", "X\u00E1m Titan Phay X\u01B0\u1EDBc (Studio Pro)", Color(0xFF38BDF8), Color(0xFFCBD5E1), Color(0xFF111418), Color(0xFF191E24), Color(0xFF090B0E), Color(0xFF1F252C)),
    ThemeSubVariant("pro_copper", "pro_monitor", "\u0110\u1ED3ng \u0110\u1ECF & Carbon (Broadcast Monitor)", Color(0xFFFF7043), Color(0xFFFFAB91), Color(0xFF14100E), Color(0xFF1D1714), Color(0xFF0A0807), Color(0xFF241D1A)),

    // 3. Pure AMOLED Minimal
    ThemeSubVariant("amoled_crimson", "pure_amoled", "Nothing Red (\u0110en & \u0110\u1ECF S\u1EAFc)", Color(0xFFFF2E4C), Color(0xFFFF6B81), Color(0xFF000000), Color(0xFF000000), Color(0xFF000000), Color(0xFF000000)),
    ThemeSubVariant("amoled_white", "pure_amoled", "Swiss Mono (\u0110en & Tr\u1EAFng Tinh)", Color(0xFFFFFFFF), Color(0xFF94A3B8), Color(0xFF000000), Color(0xFF000000), Color(0xFF000000), Color(0xFF000000)),
    ThemeSubVariant("amoled_emerald", "pure_amoled", "OLED Matrix (\u0110en & Ng\u1ECDc L\u1EE5c B\u1EA3o)", Color(0xFF00E676), Color(0xFF69F0AE), Color(0xFF000000), Color(0xFF000000), Color(0xFF000000), Color(0xFF000000))
)

fun getGlassPaletteById(id: String): GlassPalette = GlassThemePresets.find { it.id == id } ?: GlassThemePresets.first()
fun getGlassTintById(id: String): GlassTintOption = GlassTintPresets.find { it.id == id } ?: GlassTintPresets.first()

private fun normalizeEngineId(rawId: String): String = when (rawId) {
    "brushed_metal" -> ThemeEngineId.PRO_MONITOR.id
    "amoled_black" -> ThemeEngineId.PURE_AMOLED.id
    "velvet_cinema" -> ThemeEngineId.CINEMA_SHOWCASE.id
    "liquid_glass", "cinema_showcase", "pro_monitor", "pure_amoled" -> rawId
    else -> ThemeEngineId.LIQUID_GLASS.id
}

fun getSubVariantById(engineId: String, variantId: String): ThemeSubVariant {
    val normalized = normalizeEngineId(engineId)
    val forEngine = ThemeSubVariants.filter { it.engineId == normalized }
    return forEngine.find { it.id == variantId } ?: forEngine.firstOrNull() ?: ThemeSubVariants.first()
}

fun buildEffectiveStyleConfig(
    engineId: String,
    subVariantId: String,
    glassThemeId: String,
    glassTintId: String,
    frostAlpha: Float,
    reduceTransparency: Boolean = false,
    isLightMode: Boolean = false
): GlassStyleConfig {
    val normEngine = normalizeEngineId(engineId)
    return if (normEngine == ThemeEngineId.LIQUID_GLASS.id) {
        val basePal = getGlassPaletteById(glassThemeId)
        val effectivePal = if (isLightMode) {
            basePal.copy(
                deepBgTop = Color(0xFFF3F6FA),
                deepBgMid = Color(0xFFEAF0F7),
                deepBgBottom = Color(0xFFE2E8F0)
            )
        } else basePal
        val baseTint = getGlassTintById(glassTintId)
        val effectiveTint = if (isLightMode) {
            baseTint.copy(surfaceColor = Color(0xFFFFFFFF), rimTint = Color(0xFFFFFFFF))
        } else baseTint

        GlassStyleConfig(
            engineId = normEngine,
            subVariantId = subVariantId,
            palette = effectivePal,
            tint = effectiveTint,
            frostAlpha = frostAlpha,
            reduceTransparency = reduceTransparency,
            isLightMode = isLightMode
        )
    } else {
        val v = getSubVariantById(normEngine, subVariantId)
        val mappedPalette = GlassPalette(
            id = v.id,
            name = v.name,
            primaryGlow = v.primaryAccent,
            secondaryGlow = v.secondaryAccent,
            tertiaryGlow = v.primaryAccent,
            deepBgTop = v.bgTop,
            deepBgMid = v.bgMid,
            deepBgBottom = v.bgBottom
        )
        val mappedTint = GlassTintOption(
            id = v.id,
            name = v.name,
            surfaceColor = v.cardSurface,
            rimTint = v.primaryAccent
        )
        GlassStyleConfig(
            engineId = normEngine,
            subVariantId = v.id,
            palette = mappedPalette,
            tint = mappedTint,
            frostAlpha = frostAlpha,
            reduceTransparency = reduceTransparency,
            isLightMode = false
        )
    }
}

val LocalGlassStyle = staticCompositionLocalOf { GlassStyleConfig() }
val LocalGlassPalette = staticCompositionLocalOf { GlassThemePresets.first() }

@Composable
fun LiquidAmbientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val style = LocalGlassStyle.current
    val palette = style.palette

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val w = size.width
                val h = size.height
                onDrawBehind {
                    when {
                        style.isPureAmoled -> {
                            drawRect(color = Color.Black)
                        }
                        style.isProMonitor -> {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(palette.deepBgTop, palette.deepBgMid, palette.deepBgBottom)
                                )
                            )
                            // Van kim loai phay xuoc ngang dac trung cua Pro Monitor
                            val stepPx = 3.5.dp.toPx().coerceAtLeast(2f)
                            var y = 0f
                            while (y < h) {
                                drawLine(
                                    color = Color.White.copy(alpha = 0.03f),
                                    start = Offset(0f, y),
                                    end = Offset(w, y),
                                    strokeWidth = 1f
                                )
                                y += stepPx
                            }
                        }
                        style.isCinema -> {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(palette.deepBgTop, palette.deepBgMid, palette.deepBgBottom)
                                )
                            )
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(palette.primaryGlow.copy(alpha = 0.16f), Color.Transparent),
                                    center = Offset(w * 0.5f, 0f),
                                    radius = w * 0.9f
                                ),
                                center = Offset(w * 0.5f, 0f),
                                radius = w * 0.9f
                            )
                        }
                        style.isLightMode -> {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(palette.deepBgTop, palette.deepBgMid, palette.deepBgBottom)
                                )
                            )
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(palette.primaryGlow.copy(alpha = 0.12f), Color.Transparent),
                                    center = Offset(w * 0.85f, h * 0.08f),
                                    radius = w * 0.8f
                                ),
                                center = Offset(w * 0.85f, h * 0.08f),
                                radius = w * 0.8f
                            )
                        }
                        else -> {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(palette.deepBgTop, palette.deepBgMid, palette.deepBgBottom)
                                )
                            )
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(palette.primaryGlow.copy(alpha = 0.10f), Color.Transparent),
                                    center = Offset(w * 0.5f, 0f),
                                    radius = w * 0.95f
                                ),
                                center = Offset(w * 0.5f, 0f),
                                radius = w * 0.95f
                            )
                        }
                    }
                }
            },
        content = content
    )
}

fun Modifier.appleLiquidGlassSurface(
    style: GlassStyleConfig,
    variant: LiquidGlassVariant = LiquidGlassVariant.REGULAR,
    cornerRadius: Dp = 26.dp,
    highlighted: Boolean = false
): Modifier = this.drawWithCache {
    val effCorner = when {
        style.isProMonitor -> 6.dp
        style.isPureAmoled -> 12.dp
        else -> cornerRadius
    }
    val rPx = effCorner.toPx()
    val cr = CornerRadius(rPx, rPx)
    val insetPx = 1.2.dp.toPx()
    val innerCr = CornerRadius((rPx - insetPx).coerceAtLeast(2f), (rPx - insetPx).coerceAtLeast(2f))
    val palette = style.palette
    val tint = style.tint

    onDrawBehind {
        when {
            style.isPureAmoled -> {
                drawRoundRect(color = Color(0xEE000000), cornerRadius = cr)
                drawRoundRect(
                    color = if (highlighted) palette.primaryGlow else Color.White.copy(alpha = 0.25f),
                    cornerRadius = cr,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            style.isProMonitor -> {
                val metalBrush = Brush.verticalGradient(
                    colors = listOf(tint.surfaceColor, palette.deepBgMid)
                )
                drawRoundRect(brush = metalBrush, cornerRadius = cr)
                val chamferRim = Brush.linearGradient(
                    colors = listOf(
                        palette.primaryGlow.copy(alpha = if (highlighted) 0.95f else 0.65f),
                        Color.White.copy(alpha = 0.20f),
                        Color.Black.copy(alpha = 0.60f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height)
                )
                drawRoundRect(brush = chamferRim, cornerRadius = cr, style = Stroke(width = 1.2.dp.toPx()))
            }
            style.isLightMode -> {
                val alpha = if (style.reduceTransparency) 0.96f else if (variant == LiquidGlassVariant.CLEAR) 0.65f else 0.84f
                drawRoundRect(color = Color.White.copy(alpha = alpha), cornerRadius = cr)
                val rim = Brush.linearGradient(
                    colors = listOf(Color.White, palette.primaryGlow.copy(alpha = 0.35f), Color(0xFFCBD5E1)),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height)
                )
                drawRoundRect(brush = rim, cornerRadius = cr, style = Stroke(width = 1.dp.toPx()))
            }
            else -> {
                val topAlpha = when {
                    style.reduceTransparency -> 0.94f
                    variant == LiquidGlassVariant.CLEAR -> (0.20f + style.frostAlpha * 0.45f).coerceIn(0.22f, 0.36f)
                    else -> (0.38f + style.frostAlpha * 0.60f).coerceIn(0.40f, 0.64f)
                }
                val bottomAlpha = (topAlpha + 0.12f).coerceAtMost(0.96f)
                val baseGlassBrush = Brush.verticalGradient(
                    colors = listOf(
                        tint.surfaceColor.copy(alpha = topAlpha),
                        palette.deepBgMid.copy(alpha = bottomAlpha)
                    )
                )
                drawRoundRect(brush = baseGlassBrush, cornerRadius = cr)

                if (!style.reduceTransparency) {
                    val causticBrush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.14f),
                            palette.primaryGlow.copy(alpha = if (highlighted) 0.10f else 0.04f),
                            Color.Transparent,
                            palette.secondaryGlow.copy(alpha = if (highlighted) 0.08f else 0.03f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(size.width * 0.85f, size.height)
                    )
                    drawRoundRect(brush = causticBrush, cornerRadius = cr)

                    val topSpecularBrush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (highlighted) 0.26f else 0.18f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = (size.height * 0.38f).coerceAtLeast(12f)
                    )
                    drawRoundRect(brush = topSpecularBrush, cornerRadius = cr)

                    if (size.width > insetPx * 4 && size.height > insetPx * 4) {
                        val innerBevelBrush = Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = 0.20f), Color.Transparent, Color.Black.copy(alpha = 0.26f))
                        )
                        drawRoundRect(
                            brush = innerBevelBrush,
                            topLeft = Offset(insetPx, insetPx),
                            size = Size(size.width - insetPx * 2, size.height - insetPx * 2),
                            cornerRadius = innerCr,
                            style = Stroke(width = 0.75.dp.toPx())
                        )
                    }
                }

                val outerRimBrush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (highlighted) 0.85f else 0.58f),
                        tint.rimTint.copy(alpha = 0.26f),
                        if (highlighted) palette.primaryGlow.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.10f),
                        Color.White.copy(alpha = 0.35f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height)
                )
                drawRoundRect(
                    brush = outerRimBrush,
                    cornerRadius = cr,
                    style = Stroke(width = if (highlighted) 1.1.dp.toPx() else 0.85.dp.toPx())
                )
            }
        }
    }
}

@Composable
fun LiquidGlassBar(
    modifier: Modifier = Modifier,
    variant: LiquidGlassVariant = LiquidGlassVariant.REGULAR,
    cornerRadius: Dp = 28.dp,
    shadowElevation: Dp = 18.dp,
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val style = LocalGlassStyle.current
    val effCorner = when {
        style.isProMonitor -> 6.dp
        style.isPureAmoled -> 12.dp
        else -> cornerRadius
    }
    val effShadow = if (style.isPureAmoled) 0.dp else if (style.isLightMode) 10.dp else shadowElevation
    val shape = RoundedCornerShape(effCorner)

    Column(
        modifier = modifier
            .shadow(
                elevation = effShadow,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = if (style.isLightMode) 0.18f else 0.65f),
                spotColor = Color.Black.copy(alpha = if (style.isLightMode) 0.22f else 0.75f)
            )
            .clip(shape)
            .appleLiquidGlassSurface(
                style = style,
                variant = variant,
                cornerRadius = effCorner,
                highlighted = highlighted
            ),
        content = content
    )
}

@Composable
fun LiquidGlassModalSheet(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 30.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val style = LocalGlassStyle.current
    val effCorner = when {
        style.isProMonitor -> 8.dp
        style.isPureAmoled -> 14.dp
        else -> cornerRadius
    }
    val shape = RoundedCornerShape(effCorner)

    Column(
        modifier = modifier
            .shadow(
                elevation = if (style.isPureAmoled) 0.dp else 24.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.75f),
                spotColor = style.palette.primaryGlow.copy(alpha = 0.30f)
            )
            .clip(shape)
            .appleLiquidGlassSurface(
                style = style,
                variant = LiquidGlassVariant.REGULAR,
                cornerRadius = effCorner,
                highlighted = true
            ),
        content = content
    )
}

@Composable
fun LiquidGlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    active: Boolean = false,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val style = LocalGlassStyle.current
    val palette = style.palette
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.88f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "icon_btn_morph"
    )
    val btnShape = if (style.isProMonitor) RoundedCornerShape(6.dp) else CircleShape

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.35f
            }
            .clip(btnShape)
            .then(
                if (enabled) {
                    Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                } else Modifier
            )
            .drawWithCache {
                val cr = if (style.isProMonitor) CornerRadius(6.dp.toPx(), 6.dp.toPx()) else CornerRadius(this.size.width / 2f, this.size.width / 2f)
                onDrawBehind {
                    val fillColor = when {
                        active -> palette.primaryGlow.copy(alpha = if (style.isLightMode) 0.20f else 0.32f)
                        isPressed -> style.textPrimary.copy(alpha = 0.20f)
                        style.isLightMode -> Color(0xFF0F172A).copy(alpha = 0.06f)
                        style.isPureAmoled -> Color.Transparent
                        else -> Color.White.copy(alpha = 0.12f)
                    }
                    val rimColor = when {
                        active -> palette.primaryGlow.copy(alpha = 0.80f)
                        style.isLightMode -> Color(0xFF0F172A).copy(alpha = 0.12f)
                        style.isProMonitor -> palette.primaryGlow.copy(alpha = 0.45f)
                        else -> Color.White.copy(alpha = 0.25f)
                    }
                    drawRoundRect(color = fillColor, cornerRadius = cr)
                    drawRoundRect(color = rimColor, cornerRadius = cr, style = Stroke(width = 0.85.dp.toPx()))
                }
            },
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    tintColor: Color = Color.Unspecified,
    surfaceAlpha: Float = -1f,
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val style = LocalGlassStyle.current
    val effectiveCorner = when {
        style.isProMonitor -> 6.dp
        style.isPureAmoled -> 0.dp
        style.isCinema -> 18.dp
        else -> cornerRadius
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val morphScale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.975f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "card_morph"
    )

    val shape = RoundedCornerShape(effectiveCorner)
    val clickMod = if (onClick != null) {
        Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    } else Modifier

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = morphScale
                scaleY = morphScale
            }
            .clip(shape)
            .then(clickMod)
            .then(
                if (highlighted && !style.isPureAmoled) {
                    Modifier.appleLiquidGlassSurface(
                        style = style,
                        variant = LiquidGlassVariant.REGULAR,
                        cornerRadius = effectiveCorner,
                        highlighted = true
                    )
                } else {
                    Modifier.drawWithCache {
                        val cr = CornerRadius(effectiveCorner.toPx(), effectiveCorner.toPx())
                        onDrawBehind {
                            if (style.isPureAmoled) {
                                // Pure AMOLED Minimal: khong khung hop, chi to highlight nhe khi duoc chon
                                if (highlighted) {
                                    drawRect(color = style.palette.primaryGlow.copy(alpha = 0.12f))
                                }
                            } else {
                                drawRoundRect(color = style.cardSurfaceColor, cornerRadius = cr)
                                drawRoundRect(
                                    color = if (highlighted) style.palette.primaryGlow else style.cardBorderColor,
                                    cornerRadius = cr,
                                    style = Stroke(width = if (style.isProMonitor) 1.dp.toPx() else 0.75.dp.toPx())
                                )
                            }
                        }
                    }
                }
            ),
        content = content
    )
}

@Composable
fun LiquidGlassPill(
    modifier: Modifier = Modifier,
    active: Boolean = false,
    variant: LiquidGlassVariant = LiquidGlassVariant.CLEAR,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val style = LocalGlassStyle.current
    val palette = style.palette
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val morphScale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pill_morph"
    )

    val pillRadius = if (style.isProMonitor) 6.dp else 50.dp
    val shape = RoundedCornerShape(pillRadius)
    val clickMod = if (onClick != null) {
        Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    } else Modifier

    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = morphScale
                scaleY = morphScale
            }
            .clip(shape)
            .then(clickMod)
            .drawWithCache {
                val cr = CornerRadius(pillRadius.toPx(), pillRadius.toPx())
                onDrawBehind {
                    val fillColor = when {
                        active && style.isLightMode -> palette.primaryGlow
                        active -> palette.primaryGlow.copy(alpha = 0.34f)
                        isPressed -> style.textPrimary.copy(alpha = 0.16f)
                        style.isLightMode -> Color(0xFF0F172A).copy(alpha = 0.08f)
                        style.isPureAmoled -> Color.Transparent
                        style.isProMonitor -> style.tint.surfaceColor
                        else -> Color.White.copy(alpha = 0.12f)
                    }
                    val rimColor = when {
                        active -> palette.primaryGlow
                        style.isLightMode -> Color(0xFF0F172A).copy(alpha = 0.12f)
                        style.isProMonitor -> palette.primaryGlow.copy(alpha = 0.55f)
                        else -> Color.White.copy(alpha = 0.28f)
                    }
                    drawRoundRect(color = fillColor, cornerRadius = cr)
                    drawRoundRect(
                        color = rimColor,
                        cornerRadius = cr,
                        style = Stroke(width = if (active) 1.05.dp.toPx() else 0.75.dp.toPx())
                    )
                }
            }
            .padding(horizontal = 13.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content
    )
}