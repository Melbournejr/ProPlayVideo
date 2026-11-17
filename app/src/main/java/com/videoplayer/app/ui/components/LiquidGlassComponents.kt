package com.videoplayer.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Hai bien the chinh cua Apple Liquid Glass (HIG 2025-2026):
 * - REGULAR: Do mo cao hon + dieu chinh do sang nen de chu va icon luon de doc (Navigation Bar, Control Bar, Sheet).
 * - CLEAR: Trong suot cao, uu tien de noi dung video phia sau lo ro (Gesture HUD, Floating Overlay).
 */
enum class LiquidGlassVariant {
    REGULAR,
    CLEAR
}

enum class ThemeEngineId(val id: String, val title: String, val subtitle: String, val badge: String) {
    LIQUID_GLASS(
        "liquid_glass",
        "\u2728 Apple Liquid Glass (HIG)",
        "L\u1EDBp k\u00EDnh ch\u1EE9c n\u0103ng kh\u00FAc x\u1EA1 \u0111\u1ED9ng Regular & Clear chu\u1EA9n Apple",
        "Chu\u1EA9n HIG"
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
        "HUD t\u01B0\u01A1ng lai & vi\u1EC1n ph\u00E1t s\u00E1ng",
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
    val frostAlpha: Float = 0.22f,
    val reduceTransparency: Boolean = false
)

// Mau phu nhe tren lop kinh chuc nang (Su dung de dat theo dung tinh than Apple HIG)
val GlassTintPresets = listOf(
    GlassTintOption("crystal_white", "\uD83D\uDC8E K\u00EDnh Trong Thu\u1EA7n Khi\u1EBFt (Apple Neutral)", Color(0xFF1E2532), Color(0xFFFFFFFF)),
    GlassTintOption("glacier_cyan", "\uD83E\uDDCA \u00C1nh B\u0103ng Lam Nh\u1EB9 (Subtle Ice)", Color(0xFF182A34), Color(0xFFB2F7EF)),
    GlassTintOption("amethyst_purple", "\uD83D\uDD2E Kh\u00F3i T\u00EDm Nh\u1EA1t (Subtle Lavender)", Color(0xFF231D33), Color(0xFFE2CFEA)),
    GlassTintOption("rose_gold", "\uD83E\uDD42 \u00C1nh V\u00E0ng Champagne (Warm Glass)", Color(0xFF2B2220), Color(0xFFFFDAC1)),
    GlassTintOption("emerald_jade", "\uD83D\uDC9A L\u1EE5c B\u1EA3o M\u1EDD (Mint Glass)", Color(0xFF172924), Color(0xFFC7F9CC)),
    GlassTintOption("ruby_crimson", "\uD83C\uDF77 H\u1ED3ng Ng\u1ECDc Tr\u1EA7m (Rose Smoke)", Color(0xFF2D1B20), Color(0xFFFFCCD5)),
    GlassTintOption("obsidian_smoke", "\uD83D\uDDA4 K\u00EDnh Kh\u00F3i T\u1ED1i (Dark Graphite)", Color(0xFF14171D), Color(0xFFCFD8DC))
)

// Tong mau nhan tinh te (Uu tien nen toi sach de noi dung video luon la trung tam)
val GlassThemePresets = listOf(
    GlassPalette(
        id = "apple_ice",
        name = "\uD83E\uDDCA Apple System Blue (HIG Default)",
        primaryGlow = Color(0xFF38BDF8),
        secondaryGlow = Color(0xFF818CF8),
        tertiaryGlow = Color(0xFFC084FC),
        deepBgTop = Color(0xFF070A10),
        deepBgMid = Color(0xFF0C111C),
        deepBgBottom = Color(0xFF05070C)
    ),
    GlassPalette(
        id = "aurora_emerald",
        name = "\uD83C\uDF0C Apple Mint & Teal",
        primaryGlow = Color(0xFF34D399),
        secondaryGlow = Color(0xFF2DD4BF),
        tertiaryGlow = Color(0xFF38BDF8),
        deepBgTop = Color(0xFF060E0D),
        deepBgMid = Color(0xFF0A1715),
        deepBgBottom = Color(0xFF040908)
    ),
    GlassPalette(
        id = "sunset_magenta",
        name = "\uD83C\uDF07 Apple Indigo & Pink",
        primaryGlow = Color(0xFFF472B6),
        secondaryGlow = Color(0xFFA78BFA),
        tertiaryGlow = Color(0xFFFB7185),
        deepBgTop = Color(0xFF0E0812),
        deepBgMid = Color(0xFF160D1E),
        deepBgBottom = Color(0xFF08040B)
    ),
    GlassPalette(
        id = "gold_champagne",
        name = "\u2728 Apple Starlight Warm",
        primaryGlow = Color(0xFFFBBF24),
        secondaryGlow = Color(0xFFF59E0B),
        tertiaryGlow = Color(0xFF38BDF8),
        deepBgTop = Color(0xFF0F0C07),
        deepBgMid = Color(0xFF18140C),
        deepBgBottom = Color(0xFF090704)
    ),
    GlassPalette(
        id = "deep_ocean",
        name = "\uD83C\uDF0A Apple Deep Ocean",
        primaryGlow = Color(0xFF60A5FA),
        secondaryGlow = Color(0xFF3B82F6),
        tertiaryGlow = Color(0xFF6366F1),
        deepBgTop = Color(0xFF060913),
        deepBgMid = Color(0xFF0B1124),
        deepBgBottom = Color(0xFF03050B)
    )
)

val ThemeSubVariants = listOf(
    ThemeSubVariant("gold_signature", "brushed_metal", "\uD83C\uDFC6 V\u00E0ng \u0110\u1ED3ng Signature (WM1ZM2 Gold)", Color(0xFFD4AF37), Color(0xFFFFDF73), Color(0xFF121214), Color(0xFF1A1B1F), Color(0xFF0A0A0C), Color(0xFF202126)),
    ThemeSubVariant("titanium_silver", "brushed_metal", "\u2699\uFE0F X\u00E1m Titan Phay X\u01B0\u1EDBc (Titanium Pro)", Color(0xFF90CAF9), Color(0xFFCFD8DC), Color(0xFF101317), Color(0xFF181C22), Color(0xFF090B0E), Color(0xFF1E232A)),
    ThemeSubVariant("carbon_copper", "brushed_metal", "\uD83D\uDD25 \u0110en Carbon & \u0110\u1ED3ng \u0110\u1ECF (Copper Edition)", Color(0xFFFF8A65), Color(0xFFD84315), Color(0xFF120E0D), Color(0xFF1C1614), Color(0xFF090706), Color(0xFF221A17)),

    ThemeSubVariant("amoled_emerald", "amoled_black", "\uD83D\uDC9A \u0110en Tuy\u1EC7t \u0110\u1ED1i & Vi\u1EC1n Ng\u1ECDc L\u1EE5c B\u1EA3o", Color(0xFF00E676), Color(0xFF69F0AE), Color(0xFF000000), Color(0xFF080808), Color(0xFF000000), Color(0xFF0C0C0C)),
    ThemeSubVariant("amoled_white", "amoled_black", "\uD83E\uDD0D \u0110en Tuy\u1EC7t \u0110\u1ED1i & Vi\u1EC1n Tr\u1EAFng Tinh Khi\u1EBFt", Color(0xFFFFFFFF), Color(0xFFB0BEC5), Color(0xFF000000), Color(0xFF080808), Color(0xFF000000), Color(0xFF0C0C0C)),
    ThemeSubVariant("amoled_crimson", "amoled_black", "\u2764\uFE0F \u0110en Tuy\u1EC7t \u0110\u1ED1i & Vi\u1EC1n \u0110\u1ECF Cinema", Color(0xFFFF5252), Color(0xFFFF8A80), Color(0xFF000000), Color(0xFF080808), Color(0xFF000000), Color(0xFF0C0C0C)),
    ThemeSubVariant("amoled_amber", "amoled_black", "\uD83E\uDDE1 \u0110en Tuy\u1EC7t \u0110\u1ED1i & Vi\u1EC1n Cam H\u1ED5 Ph\u00E1ch", Color(0xFFFFAB00), Color(0xFFFFD740), Color(0xFF000000), Color(0xFF080808), Color(0xFF000000), Color(0xFF0C0C0C)),

    ThemeSubVariant("cyber_yellow", "cyberpunk_neon", "\u26A1 V\u00E0ng Cyber & Xanh \u0110i\u1EC7n T\u1EED (Night City)", Color(0xFFFCEE0A), Color(0xFF00F0FF), Color(0xFF080411), Color(0xFF100720), Color(0xFF040208), Color(0xFF150A2A)),
    ThemeSubVariant("cyber_synthwave", "cyberpunk_neon", "\uD83D\uDD2E H\u1ED3ng Laser & T\u00EDm Neon (Synthwave)", Color(0xFFFF007F), Color(0xFF00F5D4), Color(0xFF110316), Color(0xFF1C0624), Color(0xFF08010B), Color(0xFF240830)),
    ThemeSubVariant("cyber_matrix", "cyberpunk_neon", "\uD83D\uDCBB Xanh Ma Tr\u1EADn K\u1EF9 Thu\u1EADt S\u1ED1 (Matrix)", Color(0xFF00FF66), Color(0xFF00B8D4), Color(0xFF020C05), Color(0xFF04170A), Color(0xFF010602), Color(0xFF061E0E)),

    ThemeSubVariant("cinema_burgundy", "velvet_cinema", "\uD83C\uDF77 \u0110\u1ECF Nhung R\u1EA1p H\u00E1t C\u1ED5 \u0110i\u1EC3n (Royal Theatre)", Color(0xFFFFD54F), Color(0xFFFF8A65), Color(0xFF160609), Color(0xFF24090E), Color(0xFF0D0305), Color(0xFF2B0C12)),
    ThemeSubVariant("cinema_imax", "velvet_cinema", "\uD83C\uDFAC Xanh \u0110\u00EAm Ph\u00F2ng Chi\u1EBFu IMAX (Midnight IMAX)", Color(0xFF64B5F6), Color(0xFFFFD54F), Color(0xFF060D19), Color(0xFF0B1628), Color(0xFF03070E), Color(0xFF101F36)),
    ThemeSubVariant("cinema_vip", "velvet_cinema", "\uD83D\uDC51 T\u00EDm Nhung Ph\u00F2ng VIP (Velvet Lounge)", Color(0xFFE1BEE7), Color(0xFFFFD54F), Color(0xFF110718), Color(0xFF1C0C26), Color(0xFF09030D), Color(0xFF241032)),

    ThemeSubVariant("nordic_slate", "nordic_matte", "\uD83C\uDF2B\uFE0F X\u00E1m \u0110\u00E1 Phi\u1EBFn B\u1EAFc \u00C2u (Nordic Slate)", Color(0xFF80CBC4), Color(0xFF90CAF9), Color(0xFF14181C), Color(0xFF1D2228), Color(0xFF0E1114), Color(0xFF242A32)),
    ThemeSubVariant("nordic_sage", "nordic_matte", "\uD83C\uDF3F Xanh R\u00EAu M\u1EDD T\u1EF1 Nhi\u00EAn (Sage Matte)", Color(0xFFA5D6A7), Color(0xFFFFE082), Color(0xFF121815), Color(0xFF1A231E), Color(0xFF0C100E), Color(0xFF212C25)),
    ThemeSubVariant("nordic_espresso", "nordic_matte", "\u2615 N\u00E2u C\u00E0 Ph\u00EA \u1EA4m (Warm Espresso)", Color(0xFFFFCC80), Color(0xFFBCAAA4), Color(0xFF181311), Color(0xFF221B18), Color(0xFF0E0B0A), Color(0xFF2B221F))
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
    frostAlpha: Float,
    reduceTransparency: Boolean = false
): GlassStyleConfig {
    return if (engineId == ThemeEngineId.LIQUID_GLASS.id) {
        GlassStyleConfig(
            engineId = engineId,
            subVariantId = subVariantId,
            palette = getGlassPaletteById(glassThemeId),
            tint = getGlassTintById(glassTintId),
            frostAlpha = frostAlpha,
            reduceTransparency = reduceTransparency
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
            frostAlpha = frostAlpha,
            reduceTransparency = reduceTransparency
        )
    }
}

val LocalGlassStyle = staticCompositionLocalOf { GlassStyleConfig() }
val LocalGlassPalette = staticCompositionLocalOf { GlassThemePresets.first() }

/**
 * Nen Chinh (Content Canvas Background):
 * Theo dung nguyen tac 1 & 4 cua Apple HIG: Nen chinh la lop chua noi dung (Content Layer),
 * giu su tinh lang, sau va sach de thumbnail video va cac thanh dieu huong Liquid Glass phia tren noi bat.
 */
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
                        else -> {
                            // Nen Content Layer toi sau tinh te + Anh sang moi truong cuc nhe (Subtle Ambient Backlight)
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(palette.deepBgTop, palette.deepBgMid, palette.deepBgBottom)
                                )
                            )
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        palette.primaryGlow.copy(alpha = 0.08f),
                                        Color.Transparent
                                    ),
                                    center = Offset(w * 0.5f, 0f),
                                    radius = w * 0.9f
                                ),
                                center = Offset(w * 0.5f, 0f),
                                radius = w * 0.9f
                            )
                        }
                    }
                }
            },
        content = content
    )
}

/**
 * Ve be mat Apple Liquid Glass that su (chi danh cho Functional Layer: Navigation Bar, Control Bar, Sheet, Floating Overlay):
 * - Co khuc xa vien tren-trai (Top-Left Specular Highlight) va vien duoi-phai (Bottom-Right Refraction Rim)
 * - Co do phong long nhe (Inner Volume Sheen)
 * - Tu dong chuyen sang Opaque neu bat Reduce Transparency
 */
fun Modifier.appleLiquidGlassSurface(
    style: GlassStyleConfig,
    variant: LiquidGlassVariant = LiquidGlassVariant.REGULAR,
    cornerRadius: Dp = 24.dp,
    highlighted: Boolean = false
): Modifier = this.drawWithCache {
    val rPx = cornerRadius.toPx()
    val cr = CornerRadius(rPx, rPx)
    val palette = style.palette
    val tint = style.tint

    // Do duc thich ung theo bien the Regular / Clear va Reduce Transparency
    val baseOpacity = when {
        style.reduceTransparency -> 0.95f
        variant == LiquidGlassVariant.CLEAR -> (style.frostAlpha * 1.45f).coerceIn(0.24f, 0.44f)
        else -> (0.64f + style.frostAlpha * 0.45f).coerceIn(0.68f, 0.86f)
    }

    onDrawBehind {
        // 1. Lop nen kinh khu nhieu (Translucent Backdrop Base)
        val backdropBrush = Brush.verticalGradient(
            colors = listOf(
                tint.surfaceColor.copy(alpha = baseOpacity),
                palette.deepBgMid.copy(alpha = (baseOpacity * 1.05f).coerceAtMost(0.96f))
            )
        )
        drawRoundRect(brush = backdropBrush, cornerRadius = cr)

        // 2. Lop khuc xa anh sang ben trong (Refraction & Specular Volume)
        if (!style.reduceTransparency) {
            val sheenAlpha = if (variant == LiquidGlassVariant.CLEAR) 0.14f else 0.10f
            val topSheen = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = sheenAlpha),
                    palette.primaryGlow.copy(alpha = if (highlighted) 0.08f else 0.02f),
                    Color.Transparent
                ),
                startY = 0f,
                endY = size.height * 0.55f
            )
            drawRoundRect(brush = topSheen, cornerRadius = cr)
        }

        // 3. Vien khuc xa kep (Concentric Specular Rim - Sang sac o dinh trai, diu o day phai)
        val rimBrush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = if (highlighted) 0.72f else 0.42f),
                tint.rimTint.copy(alpha = 0.16f),
                if (highlighted) palette.primaryGlow.copy(alpha = 0.50f) else Color.White.copy(alpha = 0.08f),
                Color.White.copy(alpha = 0.24f)
            ),
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height)
        )
        drawRoundRect(
            brush = rimBrush,
            cornerRadius = cr,
            style = Stroke(width = if (highlighted) 1.3.dp.toPx() else 0.9.dp.toPx())
        )
    }
}

/**
 * LiquidGlassBar: Dung doc quyen cho Top Navigation Bar, Player Control Bar, Floating Action Bar.
 */
@Composable
fun LiquidGlassBar(
    modifier: Modifier = Modifier,
    variant: LiquidGlassVariant = LiquidGlassVariant.REGULAR,
    cornerRadius: Dp = 26.dp,
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val style = LocalGlassStyle.current
    val shape = RoundedCornerShape(cornerRadius)

    Column(
        modifier = modifier
            .clip(shape)
            .appleLiquidGlassSurface(
                style = style,
                variant = variant,
                cornerRadius = cornerRadius,
                highlighted = highlighted
            ),
        content = content
    )
}

/**
 * LiquidGlassCard:
 * - Neu la Content Card trong danh sach (highlighted == false): Dung be mat Content Layer phang sach,
 *   bo goc Concentric, KHONG dung hieu ung kinh chong cheo (Tuan thu Nguyen tac 1 & 4 cua Apple HIG).
 * - Neu la Functional Card / Banner noi bat (highlighted == true): Dung Apple Liquid Glass Regular.
 */
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
    val palette = style.palette

    val effectiveCorner = when (style.engineId) {
        ThemeEngineId.BRUSHED_METAL.id -> 12.dp
        ThemeEngineId.CYBERPUNK_NEON.id -> 10.dp
        else -> cornerRadius
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val morphScale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.975f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "card_morph"
    )

    val shape = RoundedCornerShape(effectiveCorner)
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
                scaleX = morphScale
                scaleY = morphScale
            }
            .clip(shape)
            .then(clickMod)
            .then(
                if (highlighted) {
                    // Functional Layer / Featured Card -> Dung Liquid Glass Regular
                    Modifier.appleLiquidGlassSurface(
                        style = style,
                        variant = LiquidGlassVariant.REGULAR,
                        cornerRadius = effectiveCorner,
                        highlighted = true
                    )
                } else {
                    // Content Layer (Video Item, Folder Item) -> Be mat phang tinh te, KHONG dung Glass
                    Modifier.drawWithCache {
                        val cr = CornerRadius(effectiveCorner.toPx(), effectiveCorner.toPx())
                        val contentBg = when (style.engineId) {
                            ThemeEngineId.AMOLED_BLACK.id -> Color(0xFF0A0A0C)
                            else -> Color(0xFF121722).copy(alpha = 0.88f)
                        }
                        onDrawBehind {
                            drawRoundRect(color = contentBg, cornerRadius = cr)
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.07f),
                                cornerRadius = cr,
                                style = Stroke(width = 0.7.dp.toPx())
                            )
                        }
                    }
                }
            ),
        content = content
    )
}

/**
 * LiquidGlassPill: Nut vien thuoc co chuyen dong Morph dan hoi (Spring Physics).
 * Khong ve chong kinh nang ne khi nam trong Thanh dieu khien (Tranh Glass-on-Glass).
 */
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
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pill_morph"
    )

    val pillRadius = when (style.engineId) {
        ThemeEngineId.BRUSHED_METAL.id -> 10.dp
        ThemeEngineId.CYBERPUNK_NEON.id -> 8.dp
        else -> 50.dp
    }
    val shape = RoundedCornerShape(pillRadius)
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
                scaleX = morphScale
                scaleY = morphScale
            }
            .clip(shape)
            .then(clickMod)
            .drawWithCache {
                val cr = CornerRadius(pillRadius.toPx(), pillRadius.toPx())
                onDrawBehind {
                    val fillBrush = when {
                        active -> Brush.horizontalGradient(
                            colors = listOf(
                                palette.primaryGlow.copy(alpha = 0.34f),
                                palette.secondaryGlow.copy(alpha = 0.28f)
                            )
                        )
                        isPressed -> Brush.horizontalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.22f),
                                Color.White.copy(alpha = 0.14f)
                            )
                        )
                        variant == LiquidGlassVariant.REGULAR -> Brush.verticalGradient(
                            colors = listOf(
                                style.tint.surfaceColor.copy(alpha = 0.78f),
                                palette.deepBgMid.copy(alpha = 0.85f)
                            )
                        )
                        else -> Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.12f),
                                Color.White.copy(alpha = 0.06f)
                            )
                        )
                    }
                    val rimColor = if (active) {
                        palette.primaryGlow.copy(alpha = 0.80f)
                    } else {
                        Color.White.copy(alpha = 0.22f)
                    }
                    drawRoundRect(brush = fillBrush, cornerRadius = cr)
                    drawRoundRect(
                        color = rimColor,
                        cornerRadius = cr,
                        style = Stroke(width = if (active) 1.1.dp.toPx() else 0.7.dp.toPx())
                    )
                }
            }
            .padding(horizontal = 13.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content
    )
}