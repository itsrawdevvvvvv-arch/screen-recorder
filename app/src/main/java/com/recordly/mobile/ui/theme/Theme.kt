package com.recordly.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val StudioBg = Color(0xFF0B0D12)
val StudioSurface = Color(0xFF141821)
val StudioSurfaceElevated = Color(0xFF1C2230)
val StudioBorder = Color(0x12FFFFFF)
val StudioPrimary = Color(0xFF6C5CE7)
val StudioPrimaryBright = Color(0xFF8B7BFF)
val StudioAccent = Color(0xFF00D2A0)
val StudioDanger = Color(0xFFFF4757)
val StudioWarning = Color(0xFFFFA502)
val StudioTextPrimary = Color(0xFFF1F5F9)
val StudioTextSecondary = Color(0xFF94A3B8)

private val RecordlyColorScheme = darkColorScheme(
    primary = StudioPrimary,
    secondary = StudioAccent,
    tertiary = StudioWarning,
    background = StudioBg,
    surface = StudioSurface,
    surfaceVariant = StudioSurfaceElevated,
    error = StudioDanger,
    onPrimary = Color.White,
    onSecondary = Color(0xFF032A21),
    onBackground = StudioTextPrimary,
    onSurface = StudioTextPrimary,
    onSurfaceVariant = StudioTextSecondary
)

@Composable
fun RecordlyMobileTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RecordlyColorScheme,
        content = content
    )
}
