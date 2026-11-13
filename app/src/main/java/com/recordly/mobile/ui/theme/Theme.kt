package com.recordly.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// User Uploaded Reference Color Palette
val WarmSand = Color(0xFFDCC9A9)       // 01 warm sand
val Terracotta = Color(0xFFB83A2D)     // 02 terracotta
val ForestSage = Color(0xFF4E6851)     // 03 forest sage

// Minimal Sleek Dark Surfaces
val StudioBg = Color(0xFF0E1012)
val StudioSurface = Color(0xFF17191D)
val StudioSurfaceElevated = Color(0xFF22262E)
val StudioBorder = Color(0x1AFFFFFF)

val StudioTextPrimary = Color(0xFFF8FAFC)
val StudioTextSecondary = Color(0xFF9DA3AE)
val StudioTextMuted = Color(0xFF64748B)

// Brand accents
val StudioPrimary = WarmSand
val StudioPrimaryBright = Color(0xFFE8DAC2)
val StudioAccent = ForestSage
val StudioDanger = Terracotta
val StudioWarning = Color(0xFFE5A84B)

// Product Sans / Geometric Typography
val ProductSansFamily = FontFamily.SansSerif

val RecordlyTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = ProductSansFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        color = StudioTextPrimary
    ),
    titleLarge = TextStyle(
        fontFamily = ProductSansFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        color = StudioTextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = ProductSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        color = StudioTextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = ProductSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        color = StudioTextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = ProductSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        color = StudioTextSecondary
    ),
    labelLarge = TextStyle(
        fontFamily = ProductSansFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        color = StudioTextPrimary
    ),
    labelSmall = TextStyle(
        fontFamily = ProductSansFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        color = StudioTextSecondary
    )
)

private val RecordlyColorScheme = darkColorScheme(
    primary = WarmSand,
    secondary = ForestSage,
    tertiary = Terracotta,
    background = StudioBg,
    surface = StudioSurface,
    surfaceVariant = StudioSurfaceElevated,
    error = Terracotta,
    onPrimary = Color(0xFF1A150D),
    onSecondary = Color.White,
    onBackground = StudioTextPrimary,
    onSurface = StudioTextPrimary,
    onSurfaceVariant = StudioTextSecondary
)

@Composable
fun RecordlyMobileTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RecordlyColorScheme,
        typography = RecordlyTypography,
        content = content
    )
}
