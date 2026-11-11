package com.videoplayer.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ThemeEngineId(val id: String, val title: String, val subtitle: String, val badge: String) {
    LIQUID_GLASS(
        "liquid_glass",
        "\u2728 Apple Liquid Glass",
        "K\u00EDnh l\u1ECFng kh\u00FAc x\u1EA1 \u00E1nh s\u00E1ng \u0111a l\u1EDBp chu\u1EA9n visionOS",
        "M\u1EB7c \u0111\u1ECBnh"
    ),
    BRUSHED_METAL(
        "brushed_metal",
        "\uD83C\uDFA7 Brushed Metal Pro",
        "Kim lo\u1EA1i phay x\u01B0\u1EDBc Hi-Res & vi\u1EC1n v\u00E1t kim c\u01B0\u01A1ng",
        "Walkman"
    ),
    AMOLED_BLACK(
        "amoled_black",
        "\uD83C\uDF11 Pitch Black AMOLED",
        "\u0110en tuy\u1EC7t \u0111\u1ED1i #000000 ti\u1EBFt ki\u1EC7m pin & vi\u1EC1n m\u1EA3nh",
        "OLED"
    ),
    CYBERPUNK_NEON(
        "cyberpunk_neon",
        "\u26A1 Cyberpunk Neon",
        "HUD t\u01B0\u01A1ng lai, l\u01B0\u1EDBi k\u1EF9 thu\u1EADt s\u1ED1 & vi\u1EC1n ph\u00E1t s\u00E1ng",
        "Sci-Fi"
    ),
    VELVET_CINEMA(
        "velvet_cinema",
        "\uD83C\uDFAC Velvet Cinema",
        "Nhung m\u1EDD ph\u00F2ng chi\u1EBFu IMAX \u1EA5m c\u00FAng, d\u1ECBu m\u1EAFt",
        "Cinema"
    ),
    NORDIC_MATTE(
        "nordic_matte",
        "\u2601\uFE0F Soft Nordic Matte",
        "Thi\u1EBFt k\u1EBF ph\u1EB3ng B\u1EAFc \u00C2u t\u1ED1i gi\u1EA3n, bo tr\u00F2n m\u1EC1m m\u1EA1i",
        "Minimal"
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
    val subVariantId: String = "gold_signature",
    val palette: GlassPalette = GlassThemePresets.first(),
    val tint: GlassTintOption = GlassTintPresets.first(),
    val frostAlpha: Float = 0.16f
)

val GlassTintPresets = listOf(
    GlassTintOption("crystal_white", "\uD83D\uDC8E Pha l\u00EA Tr\u1EAFng (Apple Frost)", Color(0xFFFFFFFF), Color(0xFFFFFFFF)),
    GlassTintOption("glacier_cyan", "\uD83E\uDDCA Xanh B\u0103ng Gi\u00E1 (Glacier Cyan)", Color(0xFF64DFDF), Color(0xFF80FFDB)),
    GlassTintOption("amethyst_purple", "\uD83D\uDD2E T\u00EDm Th\u1EA1ch Anh (Amethyst Glass)", Color(0xFFB388FF), Color(0xFFEA80FC)),
    GlassTintOption("rose_gold", "\uD83E\uDD42 V\u00E0ng H\u1ED3ng (Rose Champagne)", Color(0xFFFFAB91), Color(0xFFFFCCBC)),
    GlassTintOption("emerald_jade", "\uD83D\uDC9A Ng\u1ECDc L\u1EE5c B\u1EA3o (Emerald Jade)", Color(0xFF69F0AE), Color(0xFFB9F6CA)),
    GlassTintOption("ruby_crimson", "\uD83C\uDF77 \u0110\u1ECF H\u1ED3ng Ng\u1ECDc (Ruby Wine)", Color(0xFFFF5252), Color(0xFFFF8A80)),
    GlassTintOption("obsidian_smoke", "\uD83D\uDDA4 K\u00EDnh Kh\u00F3i \u0110en (Smoky Obsidian)", Color(0xFF263238), Color(0xFF90A4AE))
)

val GlassThemePresets = listOf(
    GlassPalette(
        id = "apple_ice",
        name = "\uD83E\uDDCA Apple Ice Crystal (visionOS Chu\u1EA9n)",
        primaryGlow = Color(0xFF00F0FF),
        secondaryGlow = Color(0xFF7B2CBF),
        tertiaryGlow = Color(0xFFFF2A85),
        deepBgTop = Color(0xFF050B16),
        deepBgMid = Color(0xFF0A1428),
        deepBgBottom = Color(0xFF03060C)
    ),
    GlassPalette(
        id = "aurora_emerald",
        name = "\uD83C\uDF0C C\u1EF1c Quang Ng\u1ECDc L\u1EE5c B\u1EA3o",
        primaryGlow = Color(0xFF00F5D4),
        secondaryGlow = Color(0xFF00BBF9),
        tertiaryGlow = Color(0xFF9B5DE5),
        deepBgTop = Color(0xFF031412),
        deepBgMid = Color(0xFF062320),
        deepBgBottom = Color(0xFF020B0A)
    ),
    GlassPalette(
        id = "sunset_magenta",
        name = "\uD83C\uDF07 Ho\u00E0ng H\u00F4n H\u1ED3ng Ng\u1ECDc",
        primaryGlow = Color(0xFFFF4D6D),
        secondaryGlow = Color(0xFFFF9E00),
        tertiaryGlow = Color(0xFF7209B7),
        deepBgTop = Color(0xFF1A0612),
        deepBgMid = Color(0xFF280B1C),
        deepBgBottom = Color(0xFF0D0208)
    ),
    GlassPalette(
        id = "gold_champagne",
        name = "\u2728 V\u00E0ng Champagne Ho\u00E0ng Gia",
        primaryGlow = Color(0xFFFFD166),
        secondaryGlow = Color(0xFFEF476F),
        tertiaryGlow = Color(0xFF118AB2),
        deepBgTop = Color(0xFF171105),
        deepBgMid = Color(0xFF241B08),
        deepBgBottom = Color(0xFF0B0802)
    ),
    GlassPalette(
        id = "deep_ocean",
        name = "\uD83C\uDF0A \u0110\u1EA1i D\u01B0\u01A1ng S\u00E2u Th\u1EB3m",
        primaryGlow = Color(0xFF4CC9F0),
        secondaryGlow = Color(0xFF4361EE),
        tertiaryGlow = Color(0xFF3A0CA3),
        deepBgTop = Color(0xFF03071E),
        deepBgMid = Color(0xFF081238),
        deepBgBottom = Color(0xFF01030D)
    )
)

val ThemeSubVariants = listOf(
    ThemeSubVariant("gold_signature", "brushed_metal", "\uD83C\uDFC6 V\u00E0ng \u0110\u1ED3ng Signature (WM1ZM2 Gold)", Color(0xFFD4AF37), Color(0xFFFFDF73), Color(0xFF141416), Color(0xFF1E1F23), Color(0xFF0C0C0E), Color(0xFF222328)),
    ThemeSubVariant("titanium_silver", "brushed_metal", "\u2699\uFE0F X\u00E1m Titan Phay X\u01B0\u1EDBc (Titanium Pro)", Color(0xFF90CAF9), Color(0xFFCFD8DC), Color(0xFF121519), Color(0xFF1B2026), Color(0xFF0B0D10), Color(0xFF20252C)),
    ThemeSubVariant("carbon_copper", "brushed_metal", "\uD83D\uDD25 \u0110en Carbon & \u0110\u1ED3ng \u0110\u1ECF (Copper Edition)", Color(0xFFFF8A65), Color(0xFFD84315), Color(0xFF14100E), Color(0xFF1F1815), Color(0xFF0A0807), Color(0xFF241C19)),

    ThemeSubVariant("amoled_emerald", "amoled_black", "\uD83D\uDC9A \u0110en Tuy\u1EC7t \u0110\u1ED1i & Vi\u1EC1n Ng\u1ECDc L\u1EE5c B\u1EA3o", Color(0xFF00E676), Color(0xFF69F0AE), Color(0xFF000000), Color(0xFF080808), Color(0xFF000000), Color(0xFF0A0A0A)),
    ThemeSubVariant("amoled_white", "amoled_black", "\uD83E\uDD0D \u0110en Tuy\u1EC7t \u0110\u1ED1i & Vi\u1EC1n Tr\u1EAFng Tinh Khi\u1EBFt", Color(0xFFFFFFFF), Color(0xFFB0BEC5), Color(0xFF000000), Color(0xFF080808), Color(0xFF000000), Color(0xFF0A0A0A)),
    ThemeSubVariant("amoled_crimson", "amoled_black", "\u2764\uFE0F \u0110en Tuy\u1EC7t \u0110\u1ED1i & Vi\u1EC1n \u0110\u1ECF Cinema", Color(0xFFFF5252), Color(0xFFFF8A80), Color(0xFF000000), Color(0xFF080808), Color(0xFF000000), Color(0xFF0A0A0A)),
    ThemeSubVariant("amoled_amber", "amoled_black", "\uD83E\uDDE1 \u0110en Tuy\u1EC7t \u0110\u1ED1i & Vi\u1EC1n Cam H\u1ED5 Ph\u00E1ch", Color(0xFFFFAB00), Color(0xFFFFD740), Color(0xFF000000), Color(0xFF080808), Color(0xFF000000), Color(0xFF0A0A0A)),

    ThemeSubVariant("cyber_yellow", "cyberpunk_neon", "\u26A1 V\u00E0ng Cyber & Xanh \u0110i\u1EC7n T\u1EED (Night City)", Color(0xFFFCEE0A), Color(0xFF00F0FF), Color(0xFF090414), Color(0xFF120826), Color(0xFF05020A), Color(0xFF160B2E)),
    ThemeSubVariant("cyber_synthwave", "cyberpunk_neon", "\uD83D\uDD2E H\u1ED3ng Laser & T\u00EDm Neon (Synthwave)", Color(0xFFFF007F), Color(0xFF00F5D4), Color(0xFF14031A), Color(0xFF21072B), Color(0xFF09010D), Color(0xFF280936)),
    ThemeSubVariant("cyber_matrix", "cyberpunk_neon", "\uD83D\uDCBB Xanh Ma Tr\u1EADn K\u1EF9 Thu\u1EADt S\u1ED1 (Matrix)", Color(0xFF00FF66), Color(0xFF00B8D4), Color(0xFF020F06), Color(0xFF051C0C), Color(0xFF010803), Color(0xFF072410)),

    ThemeSubVariant("cinema_burgundy", "velvet_cinema", "\uD83C\uDF77 \u0110\u1ECF Nhung R\u1EA1p H\u00E1t C\u1ED5 \u0110i\u1EC3n (Royal Theatre)", Color(0xFFFFD54F), Color(0xFFFF8A65), Color(0xFF1C070B), Color(0xFF2B0B11), Color(0xFF110406), Color(0xFF330E15)),
    ThemeSubVariant("cinema_imax", "velvet_cinema", "\uD83C\uDFAC Xanh \u0110\u00EAm Ph\u00F2ng Chi\u1EBFu IMAX (Midnight IMAX)", Color(0xFF64B5F6), Color(0xFFFFD54F), Color(0xFF07101E), Color(0xFF0D1B30), Color(0xFF040912), Color(0xFF13243E)),
    ThemeSubVariant("cinema_vip", "velvet_cinema", "\uD83D\uDC51 T\u00EDm Nhung Ph\u00F2ng VIP (Velvet Lounge)", Color(0xFFE1BEE7), Color(0xFFFFD54F), Color(0xFF14081C), Color(0xFF210E2E), Color(0xFF0B0410), Color(0xFF2A133B)),

    ThemeSubVariant("nordic_slate", "nordic_matte", "\uD83C\uDF2B\uFE0F X\u00E1m \u0110\u00E1 Phi\u1EBFn B\u1EAFc \u00C2u (Nordic Slate)", Color(0xFF80CBC4), Color(0xFF90CAF9), Color(0xFF181C20), Color(0xFF22272E), Color(0xFF121518), Color(0xFF282E37)),
    ThemeSubVariant("nordic_sage", "nordic_matte", "\uD83C\uDF3F Xanh R\u00EAu M\u1EDD T\u1EF1 Nhi\u00EAn (Sage Matte)", Color(0xFFA5D6A7), Color(0xFFFFE082), Color(0xFF151C18), Color(0xFF1E2822), Color(0xFF0F1411), Color(0xFF25322A)),
    ThemeSubVariant("nordic_espresso", "nordic_matte", "\u2615 N\u00E2u C\u00E0 Ph\u00EA \u1EA4m (Warm Espresso)", Color(0xFFFFCC80), Color(0xFFBCAAA4), Color(0xFF1C1614), Color(0xFF28201D), Color(0xFF120E0C), Color(0xFF322824))
)

fun getGlassPaletteById(id: String): GlassPalette {
    return GlassThemePresets.find { it.id == id } ?: GlassThemePresets.first()
}

fun getGlassTintById(id: String): GlassTintOption {
    return GlassTintPresets.find { it.id == id } ?: GlassTintPresets.first()
}

fun getSubVariantById(engineId: String, variantId: String): ThemeSubVariant {
    val forEngine = ThemeSubVariants.filter { it.engineId == engineId }
    return forEngine.find { it.id == variantId } ?: forEngine.firstOrNull() ?: ThemeSubVariants.first()
}

fun buildEffectiveStyleConfig(
    engineId: String,
    subVariantId: String,
    glassThemeId: String,
    glassTintId: String,
    frostAlpha: Float
): GlassStyleConfig {
    return if (engineId == ThemeEngineId.LIQUID_GLASS.id) {
        GlassStyleConfig(
            engineId = engineId,
            subVariantId = subVariantId,
            palette = getGlassPaletteById(glassThemeId),
            tint = getGlassTintById(glassTintId),
            frostAlpha = frostAlpha
        )
    } else {
        val v = getSubVariantById(engineId, subVariantId)
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
            engineId = engineId,
            subVariantId = v.id,
            palette = mappedPalette,
            tint = mappedTint,
            frostAlpha = frostAlpha
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
                    when (style.engineId) {
                        ThemeEngineId.AMOLED_BLACK.id -> {
                            drawRect(color = Color.Black)
                        }
                        ThemeEngineId.BRUSHED_METAL.id -> {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(palette.deepBgTop, palette.deepBgMid, palette.deepBgBottom)
                                )
                            )
                            val stepPx = 4.dp.toPx().coerceAtLeast(2f)
                            var y = 0f
                            while (y < h) {
                                drawLine(
                                    color = Color.White.copy(alpha = 0.022f),
                                    start = Offset(0f, y),
                                    end = Offset(w, y),
                                    strokeWidth = 1f
                                )
                                y += stepPx
                            }
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(palette.primaryGlow.copy(alpha = 0.10f), Color.Transparent),
                                    center = Offset(w * 0.85f, h * 0.08f),
                                    radius = w * 0.7f
                                ),
                                center = Offset(w * 0.85f, h * 0.08f),
                                radius = w * 0.7f
                            )
                        }
                        ThemeEngineId.CYBERPUNK_NEON.id -> {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(palette.deepBgTop, palette.deepBgMid, palette.deepBgBottom)
                                )
                            )
                            val gridStep = 36.dp.toPx().coerceAtLeast(20f)
                            var gx = 0f
                            while (gx < w) {
                                drawLine(
                                    color = palette.secondaryGlow.copy(alpha = 0.05f),
                                    start = Offset(gx, 0f),
                                    end = Offset(gx, h),
                                    strokeWidth = 1f
                                )
                                gx += gridStep
                            }
                            var gy = 0f
                            while (gy < h) {
                                drawLine(
                                    color = palette.primaryGlow.copy(alpha = 0.04f),
                                    start = Offset(0f, gy),
                                    end = Offset(w, gy),
                                    strokeWidth = 1f
                                )
                                gy += gridStep
                            }
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(palette.primaryGlow.copy(alpha = 0.22f), Color.Transparent),
                                    center = Offset(w * 0.1f, h * 0.1f),
                                    radius = w * 0.65f
                                ),
                                center = Offset(w * 0.1f, h * 0.1f),
                                radius = w * 0.65f
                            )
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(palette.secondaryGlow.copy(alpha = 0.20f), Color.Transparent),
                                    center = Offset(w * 0.9f, h * 0.85f),
                                    radius = w * 0.75f
                                ),
                                center = Offset(w * 0.9f, h * 0.85f),
                                radius = w * 0.75f
                            )
                        }
                        ThemeEngineId.VELVET_CINEMA.id -> {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(palette.deepBgTop, palette.deepBgMid, palette.deepBgBottom)
                                )
                            )
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(palette.primaryGlow.copy(alpha = 0.14f), Color.Transparent),
                                    center = Offset(w * 0.5f, 0f),
                                    radius = w * 0.95f
                                ),
                                center = Offset(w * 0.5f, 0f),
                                radius = w * 0.95f
                            )
                        }
                        ThemeEngineId.NORDIC_MATTE.id -> {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(palette.deepBgTop, palette.deepBgMid, palette.deepBgBottom)
                                )
                            )
                        }
                        else -> {
                            val bgBrush = Brush.verticalGradient(
                                colors = listOf(palette.deepBgTop, palette.deepBgMid, palette.deepBgBottom)
                            )
                            val orb1 = Brush.radialGradient(
                                colors = listOf(
                                    palette.primaryGlow.copy(alpha = 0.28f),
                                    palette.primaryGlow.copy(alpha = 0.06f),
                                    Color.Transparent
                                ),
                                center = Offset(w * 0.15f, h * 0.10f),
                                radius = w * 0.85f
                            )
                            val orb2 = Brush.radialGradient(
                                colors = listOf(
                                    palette.secondaryGlow.copy(alpha = 0.26f),
                                    palette.secondaryGlow.copy(alpha = 0.05f),
                                    Color.Transparent
                                ),
                                center = Offset(w * 0.88f, h * 0.42f),
                                radius = w * 0.90f
                            )
                            val orb3 = Brush.radialGradient(
                                colors = listOf(
                                    palette.tertiaryGlow.copy(alpha = 0.18f),
                                    Color.Transparent
                                ),
                                center = Offset(w * 0.30f, h * 0.88f),
                                radius = w * 0.75f
                            )
                            drawRect(brush = bgBrush)
                            drawCircle(brush = orb1, radius = w * 0.85f, center = Offset(w * 0.15f, h * 0.10f))
                            drawCircle(brush = orb2, radius = w * 0.90f, center = Offset(w * 0.88f, h * 0.42f))
                            drawCircle(brush = orb3, radius = w * 0.75f, center = Offset(w * 0.30f, h * 0.88f))
                        }
                    }
                }
            },
        content = content
    )
}

@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    tintColor: Color = Color.Unspecified,
    surfaceAlpha: Float = -1f,
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val style = LocalGlassStyle.current
    val palette = style.palette

    val effectiveCorner = when (style.engineId) {
        ThemeEngineId.BRUSHED_METAL.id -> 12.dp
        ThemeEngineId.CYBERPUNK_NEON.id -> 10.dp
        ThemeEngineId.AMOLED_BLACK.id -> 18.dp
        ThemeEngineId.NORDIC_MATTE.id -> 26.dp
        else -> cornerRadius
    }

    val shape = RoundedCornerShape(effectiveCorner)
    val clickMod = if (onClick != null) Modifier.clickable { onClick() } else Modifier

    Column(
        modifier = modifier
            .clip(shape)
            .then(clickMod)
            .drawWithCache {
                val rPx = effectiveCorner.toPx()
                val cr = CornerRadius(rPx, rPx)

                onDrawBehind {
                    when (style.engineId) {
                        ThemeEngineId.AMOLED_BLACK.id -> {
                            drawRoundRect(
                                color = if (highlighted) palette.primaryGlow.copy(alpha = 0.12f) else Color(0xFF050505),
                                cornerRadius = cr
                            )
                            drawRoundRect(
                                color = if (highlighted) palette.primaryGlow else palette.primaryGlow.copy(alpha = 0.38f),
                                cornerRadius = cr,
                                style = Stroke(width = if (highlighted) 1.5.dp.toPx() else 0.9.dp.toPx())
                            )
                        }
                        ThemeEngineId.BRUSHED_METAL.id -> {
                            val metalBrush = Brush.linearGradient(
                                colors = listOf(
                                    style.tint.surfaceColor,
                                    palette.deepBgMid,
                                    style.tint.surfaceColor.copy(alpha = 0.92f)
                                ),
                                start = Offset(0f, 0f),
                                end = Offset(size.width, size.height)
                            )
                            drawRoundRect(brush = metalBrush, cornerRadius = cr)
                            val step = 3.dp.toPx().coerceAtLeast(2f)
                            var ly = 0f
                            while (ly < size.height) {
                                drawLine(
                                    color = Color.White.copy(alpha = 0.03f),
                                    start = Offset(0f, ly),
                                    end = Offset(size.width, ly),
                                    strokeWidth = 1f
                                )
                                ly += step
                            }
                            val chamferRim = Brush.linearGradient(
                                colors = listOf(
                                    palette.primaryGlow.copy(alpha = if (highlighted) 1f else 0.78f),
                                    Color.White.copy(alpha = 0.25f),
                                    palette.primaryGlow.copy(alpha = if (highlighted) 0.85f else 0.35f)
                                ),
                                start = Offset(0f, 0f),
                                end = Offset(size.width, size.height)
                            )
                            drawRoundRect(
                                brush = chamferRim,
                                cornerRadius = cr,
                                style = Stroke(width = if (highlighted) 1.8.dp.toPx() else 1.1.dp.toPx())
                            )
                        }
                        ThemeEngineId.CYBERPUNK_NEON.id -> {
                            drawRoundRect(
                                color = style.tint.surfaceColor.copy(alpha = 0.92f),
                                cornerRadius = cr
                            )
                            val neonBrush = Brush.horizontalGradient(
                                colors = listOf(palette.primaryGlow, palette.secondaryGlow)
                            )
                            drawRoundRect(
                                brush = neonBrush,
                                cornerRadius = cr,
                                style = Stroke(width = if (highlighted) 2.0.dp.toPx() else 1.2.dp.toPx())
                            )
                        }
                        ThemeEngineId.VELVET_CINEMA.id -> {
                            val velvetBrush = Brush.verticalGradient(
                                colors = listOf(
                                    style.tint.surfaceColor,
                                    palette.deepBgMid
                                )
                            )
                            drawRoundRect(brush = velvetBrush, cornerRadius = cr)
                            drawRoundRect(
                                color = if (highlighted) palette.primaryGlow else palette.primaryGlow.copy(alpha = 0.28f),
                                cornerRadius = cr,
                                style = Stroke(width = if (highlighted) 1.5.dp.toPx() else 0.8.dp.toPx())
                            )
                        }
                        ThemeEngineId.NORDIC_MATTE.id -> {
                            drawRoundRect(
                                color = if (highlighted) style.tint.surfaceColor.copy(alpha = 1f) else style.tint.surfaceColor.copy(alpha = 0.88f),
                                cornerRadius = cr
                            )
                            if (highlighted) {
                                drawRoundRect(
                                    color = palette.primaryGlow,
                                    cornerRadius = cr,
                                    style = Stroke(width = 1.4.dp.toPx())
                                )
                            }
                        }
                        else -> {
                            val baseTint = if (tintColor != Color.Unspecified) tintColor else style.tint.surfaceColor
                            val rimColor = style.tint.rimTint
                            val alpha = if (surfaceAlpha >= 0f) surfaceAlpha else style.frostAlpha
                            val strokePx = if (highlighted) 1.6.dp.toPx() else 1.1.dp.toPx()

                            val glassFill = Brush.linearGradient(
                                colors = listOf(
                                    baseTint.copy(alpha = (alpha * 1.45f).coerceAtMost(0.58f)),
                                    baseTint.copy(alpha = alpha * 0.78f),
                                    palette.primaryGlow.copy(alpha = if (highlighted) 0.20f else 0.06f),
                                    baseTint.copy(alpha = (alpha * 1.15f).coerceAtMost(0.48f))
                                ),
                                start = Offset(0f, 0f),
                                end = Offset(size.width, size.height)
                            )
                            val topSheen = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = (alpha * 1.3f).coerceAtMost(0.32f)),
                                    Color.Transparent
                                ),
                                startY = 0f,
                                endY = size.height * 0.42f
                            )
                            val rimBrush = Brush.linearGradient(
                                colors = listOf(
                                    rimColor.copy(alpha = if (highlighted) 0.95f else 0.78f),
                                    rimColor.copy(alpha = 0.25f),
                                    if (highlighted) palette.primaryGlow.copy(alpha = 0.85f) else rimColor.copy(alpha = 0.12f),
                                    rimColor.copy(alpha = 0.48f)
                                ),
                                start = Offset(0f, 0f),
                                end = Offset(size.width, size.height)
                            )
                            drawRoundRect(brush = glassFill, cornerRadius = cr)
                            drawRoundRect(brush = topSheen, cornerRadius = cr)
                            drawRoundRect(brush = rimBrush, cornerRadius = cr, style = Stroke(width = strokePx))
                        }
                    }
                }
            },
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

    val pillRadius = when (style.engineId) {
        ThemeEngineId.BRUSHED_METAL.id -> 8.dp
        ThemeEngineId.CYBERPUNK_NEON.id -> 6.dp
        else -> 50.dp
    }
    val shape = RoundedCornerShape(pillRadius)
    val clickMod = if (onClick != null) Modifier.clickable { onClick() } else Modifier

    Row(
        modifier = modifier
            .clip(shape)
            .then(clickMod)
            .drawWithCache {
                val cr = CornerRadius(pillRadius.toPx(), pillRadius.toPx())
                onDrawBehind {
                    when (style.engineId) {
                        ThemeEngineId.AMOLED_BLACK.id -> {
                            drawRoundRect(
                                color = if (active) palette.primaryGlow.copy(alpha = 0.22f) else Color(0xFF0A0A0A),
                                cornerRadius = cr
                            )
                            drawRoundRect(
                                color = if (active) palette.primaryGlow else Color.White.copy(alpha = 0.32f),
                                cornerRadius = cr,
                                style = Stroke(width = if (active) 1.4.dp.toPx() else 0.8.dp.toPx())
                            )
                        }
                        ThemeEngineId.BRUSHED_METAL.id -> {
                            drawRoundRect(
                                color = if (active) palette.primaryGlow.copy(alpha = 0.28f) else style.tint.surfaceColor,
                                cornerRadius = cr
                            )
                            drawRoundRect(
                                color = if (active) palette.primaryGlow else palette.primaryGlow.copy(alpha = 0.55f),
                                cornerRadius = cr,
                                style = Stroke(width = 1.1.dp.toPx())
                            )
                        }
                        ThemeEngineId.CYBERPUNK_NEON.id -> {
                            drawRoundRect(
                                color = if (active) palette.primaryGlow.copy(alpha = 0.25f) else style.tint.surfaceColor,
                                cornerRadius = cr
                            )
                            drawRoundRect(
                                color = if (active) palette.primaryGlow else palette.secondaryGlow.copy(alpha = 0.7f),
                                cornerRadius = cr,
                                style = Stroke(width = 1.2.dp.toPx())
                            )
                        }
                        ThemeEngineId.NORDIC_MATTE.id -> {
                            drawRoundRect(
                                color = if (active) palette.primaryGlow.copy(alpha = 0.30f) else style.tint.surfaceColor,
                                cornerRadius = cr
                            )
                        }
                        else -> {
                            val tint = style.tint.surfaceColor
                            val fillBrush = if (active) {
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        palette.primaryGlow.copy(alpha = 0.45f),
                                        palette.secondaryGlow.copy(alpha = 0.45f)
                                    )
                                )
                            } else {
                                Brush.verticalGradient(
                                    colors = listOf(
                                        tint.copy(alpha = (style.frostAlpha * 1.4f).coerceAtMost(0.45f)),
                                        tint.copy(alpha = style.frostAlpha * 0.7f)
                                    )
                                )
                            }
                            val rimBrush = Brush.linearGradient(
                                colors = if (active) {
                                    listOf(Color.White.copy(alpha = 0.95f), palette.primaryGlow.copy(alpha = 0.8f))
                                } else {
                                    listOf(style.tint.rimTint.copy(alpha = 0.65f), style.tint.rimTint.copy(alpha = 0.15f))
                                }
                            )
                            drawRoundRect(brush = fillBrush, cornerRadius = cr)
                            drawRoundRect(brush = rimBrush, cornerRadius = cr, style = Stroke(width = 1.1.dp.toPx()))
                        }
                    }
                }
            }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content
    )
}