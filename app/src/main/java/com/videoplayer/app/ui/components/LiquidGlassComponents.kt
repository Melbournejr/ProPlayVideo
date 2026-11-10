package com.videoplayer.app.ui.components

import androidx.compose.foundation.background
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
    LIQUID_GLASS("liquid_glass", "✨ Apple Liquid Glass", "Kính lỏng khúc xạ ánh sáng đa lớp chuẩn visionOS", "Mặc định"),
    BRUSHED_METAL("brushed_metal", "🎧 Brushed Metal Pro", "Kim loại phay xước Hi-Res & viền vát kim cương", "Walkman"),
    AMOLED_BLACK("amoled_black", "🌑 Pitch Black AMOLED", "Đen tuyệt đối #000000 tiết kiệm pin & viền mảnh", "OLED"),
    CYBERPUNK_NEON("cyberpunk_neon", "⚡ Cyberpunk Neon", "HUD tương lai, lưới kỹ thuật số & viền phát sáng", "Sci-Fi"),
    VELVET_CINEMA("velvet_cinema", "🎬 Velvet Cinema", "Nhung mờ phòng chiếu IMAX ấm cúng, dịu mắt", "Cinema"),
    NORDIC_MATTE("nordic_matte", "☁️ Soft Nordic Matte", "Thiết kế phẳng Bắc Âu tối giản, bo tròn mềm mại", "Minimal")
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

// 1. Danh sách màu phủ bề mặt kính (Dành cho bộ Apple Liquid Glass)
val GlassTintPresets = listOf(
    GlassTintOption("crystal_white", "💎 Pha lê Trắng (Apple Frost)", Color(0xFFFFFFFF), Color(0xFFFFFFFF)),
    GlassTintOption("glacier_cyan", "🧊 Xanh Băng Giá (Glacier Cyan)", Color(0xFF64DFDF), Color(0xFF80FFDB)),
    GlassTintOption("amethyst_purple", "🔮 Tím Thạch Anh (Amethyst Glass)", Color(0xFFB388FF), Color(0xFFEA80FC)),
    GlassTintOption("rose_gold", "🥂 Vàng Hồng (Rose Champagne)", Color(0xFFFFAB91), Color(0xFFFFCCBC)),
    GlassTintOption("emerald_jade", "翡翠 Ngọc Lục Bảo (Emerald Jade)", Color(0xFF69F0AE), Color(0xFFB9F6CA)),
    GlassTintOption("ruby_crimson", "🍷 Đỏ Hồng Ngọc (Ruby Wine)", Color(0xFFFF5252), Color(0xFFFF8A80)),
    GlassTintOption("obsidian_smoke", "🖤 Kính Khói Đen (Smoky Obsidian)", Color(0xFF263238), Color(0xFF90A4AE))
)

// 2. Danh sách tông màu ánh sáng nền (Dành cho bộ Apple Liquid Glass)
val GlassThemePresets = listOf(
    GlassPalette(
        id = "apple_ice",
        name = "🧊 Apple Ice Crystal (visionOS Chuẩn)",
        primaryGlow = Color(0xFF00F0FF),
        secondaryGlow = Color(0xFF7B2CBF),
        tertiaryGlow = Color(0xFFFF2A85),
        deepBgTop = Color(0xFF050B16),
        deepBgMid = Color(0xFF0A1428),
        deepBgBottom = Color(0xFF03060C)
    ),
    GlassPalette(
        id = "aurora_emerald",
        name = "🌌 Cực Quang Ngọc Lục Bảo",
        primaryGlow = Color(0xFF00F5D4),
        secondaryGlow = Color(0xFF00BBF9),
        tertiaryGlow = Color(0xFF9B5DE5),
        deepBgTop = Color(0xFF031412),
        deepBgMid = Color(0xFF062320),
        deepBgBottom = Color(0xFF020B0A)
    ),
    GlassPalette(
        id = "sunset_magenta",
        name = "🌇 Hoàng Hôn Hồng Ngọc",
        primaryGlow = Color(0xFFFF4D6D),
        secondaryGlow = Color(0xFFFF9E00),
        tertiaryGlow = Color(0xFF7209B7),
        deepBgTop = Color(0xFF1A0612),
        deepBgMid = Color(0xFF280B1C),
        deepBgBottom = Color(0xFF0D0208)
    ),
    GlassPalette(
        id = "gold_champagne",
        name = "✨ Vàng Champagne Hoàng Gia",
        primaryGlow = Color(0xFFFFD166),
        secondaryGlow = Color(0xFFEF476F),
        tertiaryGlow = Color(0xFF118AB2),
        deepBgTop = Color(0xFF171105),
        deepBgMid = Color(0xFF241B08),
        deepBgBottom = Color(0xFF0B0802)
    ),
    GlassPalette(
        id = "deep_ocean",
        name = "🌊 Đại Dương Sâu Thẳm",
        primaryGlow = Color(0xFF4CC9F0),
        secondaryGlow = Color(0xFF4361EE),
        tertiaryGlow = Color(0xFF3A0CA3),
        deepBgTop = Color(0xFF03071E),
        deepBgMid = Color(0xFF081238),
        deepBgBottom = Color(0xFF01030D)
    )
)

// 3. Danh sách các biến thể con cho 5 Bộ Theme Mới
val ThemeSubVariants = listOf(
    // Brushed Metal Pro
    ThemeSubVariant("gold_signature", "brushed_metal", "🏆 Vàng Đồng Signature (WM1ZM2 Gold)", Color(0xFFD4AF37), Color(0xFFFFDF73), Color(0xFF141416), Color(0xFF1E1F23), Color(0xFF0C0C0E), Color(0xFF222328)),
    ThemeSubVariant("titanium_silver", "brushed_metal", "⚙️ Xám Titan Phay Xước (Titanium Pro)", Color(0xFF90CAF9), Color(0xFFCFD8DC), Color(0xFF121519), Color(0xFF1B2026), Color(0xFF0B0D10), Color(0xFF20252C)),
    ThemeSubVariant("carbon_copper", "brushed_metal", "🔥 Đen Carbon & Đồng Đỏ (Copper Edition)", Color(0xFFFF8A65), Color(0xFFD84315), Color(0xFF14100E), Color(0xFF1F1815), Color(0xFF0A0807), Color(0xFF241C19)),

    // Pitch Black AMOLED
    ThemeSubVariant("amoled_emerald", "amoled_black", "💚 Đen Tuyệt Đối & Viền Ngọc Lục Bảo", Color(0xFF00E676), Color(0xFF69F0AE), Color(0xFF000000), Color(0xFF080808), Color(0xFF000000), Color(0xFF0A0A0A)),
    ThemeSubVariant("amoled_white", "amoled_black", "🤍 Đen Tuyệt Đối & Viền Trắng Tinh Khiết", Color(0xFFFFFFFF), Color(0xFFB0BEC5), Color(0xFF000000), Color(0xFF080808), Color(0xFF000000), Color(0xFF0A0A0A)),
    ThemeSubVariant("amoled_crimson", "amoled_black", "❤️ Đen Tuyệt Đối & Viền Đỏ Cinema", Color(0xFFFF5252), Color(0xFFFF8A80), Color(0xFF000000), Color(0xFF080808), Color(0xFF000000), Color(0xFF0A0A0A)),
    ThemeSubVariant("amoled_amber", "amoled_black", "🧡 Đen Tuyệt Đối & Viền Cam Hổ Phách", Color(0xFFFFAB00), Color(0xFFFFD740), Color(0xFF000000), Color(0xFF080808), Color(0xFF000000), Color(0xFF0A0A0A)),

    // Cyberpunk Neon
    ThemeSubVariant("cyber_yellow", "cyberpunk_neon", "⚡ Vàng Cyber & Xanh Điện Tử (Night City)", Color(0xFFFCEE0A), Color(0xFF00F0FF), Color(0xFF090414), Color(0xFF120826), Color(0xFF05020A), Color(0xFF160B2E)),
    ThemeSubVariant("cyber_synthwave", "cyberpunk_neon", "🔮 Hồng Laser & Tím Neon (Synthwave)", Color(0xFFFF007F), Color(0xFF00F5D4), Color(0xFF14031A), Color(0xFF21072B), Color(0xFF09010D), Color(0xFF280936)),
    ThemeSubVariant("cyber_matrix", "cyberpunk_neon", "💻 Xanh Ma Trận Kỹ Thuật Số (Matrix)", Color(0xFF00FF66), Color(0xFF00B8D4), Color(0xFF020F06), Color(0xFF051C0C), Color(0xFF010803), Color(0xFF072410)),

    // Velvet Cinema
    ThemeSubVariant("cinema_burgundy", "velvet_cinema", "🍷 Đỏ Nhung Rạp Hát Cổ Điển (Royal Theatre)", Color(0xFFFFD54F), Color(0xFFFF8A65), Color(0xFF1C070B), Color(0xFF2B0B11), Color(0xFF110406), Color(0xFF330E15)),
    ThemeSubVariant("cinema_imax", "velvet_cinema", "🎬 Xanh Đêm Phòng Chiếu IMAX (Midnight IMAX)", Color(0xFF64B5F6), Color(0xFFFFD54F), Color(0xFF07101E), Color(0xFF0D1B30), Color(0xFF040912), Color(0xFF13243E)),
    ThemeSubVariant("cinema_vip", "velvet_cinema", "👑 Tím Nhung Phòng VIP (Velvet Lounge)", Color(0xFFE1BEE7), Color(0xFFFFD54F), Color(0xFF14081C), Color(0xFF210E2E), Color(0xFF0B0410), Color(0xFF2A133B)),

    // Soft Nordic Matte
    ThemeSubVariant("nordic_slate", "nordic_matte", "🌫️ Xám Đá Phiến Bắc Âu (Nordic Slate)", Color(0xFF80CBC4), Color(0xFF90CAF9), Color(0xFF181C20), Color(0xFF22272E), Color(0xFF121518), Color(0xFF282E37)),
    ThemeSubVariant("nordic_sage", "nordic_matte", "🌿 Xanh Rêu Mờ Tự Nhiên (Sage Matte)", Color(0xFFA5D6A7), Color(0xFFFFE082), Color(0xFF151C18), Color(0xFF1E2822), Color(0xFF0F1411), Color(0xFF25322A)),
    ThemeSubVariant("nordic_espresso", "nordic_matte", "☕ Nâu Cà Phê Ấm (Warm Espresso)", Color(0xFFFFCC80), Color(0xFFBCAAA4), Color(0xFF1C1614), Color(0xFF28201D), Color(0xFF120E0C), Color(0xFF322824))
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

/**
 * Tạo GlassStyleConfig tổng hợp: Nếu đang chọn 1 trong 5 bộ Theme mới,
 * tự động ánh xạ bảng màu của biến thể đó sang GlassPalette để toàn bộ ứng dụng đổi màu đồng bộ!
 */
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

/**
 * Nền không gian tự động biến đổi theo từng Bộ Theme:
 * - Liquid Glass: Quầng sáng khúc xạ đa tâm
 * - Pitch Black AMOLED: Đen tuyền #000000 tuyệt đối (tắt điểm ảnh OLED)
 * - Brushed Metal Pro: Nền kim loại phay xước ngang tinh tế
 * - Cyberpunk Neon: Lưới tọa độ kỹ thuật số Sci-Fi + Quầng sáng Neon
 * - Velvet Cinema: Ánh sáng màn chiếu rạp hát ấm áp từ đỉnh màn hình
 * - Soft Nordic Matte: Nền nhám phẳng dịu mắt
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
                        ThemeEngineId.BRUSHED_METAL.id -> {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(palette.deepBgTop, palette.deepBgMid, palette.deepBgBottom)
                                )
                            )
                            // Vẽ các đường vân kim loại phay xước ngang siêu mảnh
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
                            // Lưới tọa độ kỹ thuật số Cyber
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
                            // Ánh đèn hắt nhẹ từ màn chiếu rạp phim phía trên
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
                            // Mặc định: Apple Liquid Glass
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

/**
 * Thẻ Card tự động thay đổi hình dáng, độ bo góc và chất liệu bề mặt theo từng Bộ Theme!
 */
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

    // Điều chỉnh độ bo góc đặc trưng theo từng bộ Theme
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
                            // Nền đen OLED #050505 + Viền mảnh sắc nét
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
                            // Nền kim loại phay xước + Viền vát kim cương Vàng Đồng / Titan
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
                            // Nền Cyber tối + Viền kép phát sáng Neon
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
                            // Bề mặt nhung mờ ấm áp
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
                            // Bề mặt phẳng Matte hoàn toàn, không viền chói
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
                            // Mặc định: Apple Liquid Glass
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

/**
 * Nút Viên Thuốc (Pill) tự động biến đổi theo Bộ Theme đang chọn
 */
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
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content
    )
}