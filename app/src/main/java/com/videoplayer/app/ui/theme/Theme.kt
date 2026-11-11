package com.videoplayer.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.videoplayer.app.ui.components.GlassStyleConfig
import com.videoplayer.app.ui.components.LocalGlassPalette
import com.videoplayer.app.ui.components.LocalGlassStyle

/**
 * Cấu hình Typography chuẩn Tiếng Việt:
 * - Dùng FontFamily.SansSerif (Roboto/MiSans hệ thống hỗ trợ đầy đủ 100% dấu tiếng Việt)
 * - Bật includeFontPadding = true để không bao giờ bị cắt dấu mũ + dấu nặng (ể, ệ, ử, ự)
 */
private val BaseViTextStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    platformStyle = PlatformTextStyle(includeFontPadding = true),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None
    )
)

val AppVietnameseTypography = Typography(
    titleLarge = BaseViTextStyle.copy(
        fontSize = 20.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp
    ),
    titleMedium = BaseViTextStyle.copy(
        fontSize = 16.sp,
        lineHeight = 23.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.1.sp
    ),
    titleSmall = BaseViTextStyle.copy(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = BaseViTextStyle.copy(
        fontSize = 15.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal
    ),
    bodyMedium = BaseViTextStyle.copy(
        fontSize = 13.5.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal
    ),
    bodySmall = BaseViTextStyle.copy(
        fontSize = 12.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Normal
    ),
    labelLarge = BaseViTextStyle.copy(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold
    ),
    labelMedium = BaseViTextStyle.copy(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium
    ),
    labelSmall = BaseViTextStyle.copy(
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Medium
    )
)

@Composable
fun VideoPlayerTheme(
    styleConfig: GlassStyleConfig = GlassStyleConfig(),
    content: @Composable () -> Unit
) {
    val palette = styleConfig.palette
    val colorScheme = darkColorScheme(
        primary = palette.primaryGlow,
        secondary = palette.secondaryGlow,
        tertiary = palette.tertiaryGlow,
        background = palette.deepBgBottom,
        surface = palette.deepBgMid,
        onPrimary = Color(0xFF001F29),
        onSecondary = Color.White,
        onBackground = Color.White,
        onSurface = Color.White
    )

    CompositionLocalProvider(
        LocalGlassStyle provides styleConfig,
        LocalGlassPalette provides palette
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppVietnameseTypography,
            content = content
        )
    }
}