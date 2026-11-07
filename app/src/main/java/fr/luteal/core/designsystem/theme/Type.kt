package fr.luteal.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private val LutealFontFamily = FontFamily.SansSerif

val LutealTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = LutealFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.02).em
    ),
    displayMedium = TextStyle(
        fontFamily = LutealFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = (-0.02).em
    ),
    displaySmall = TextStyle(
        fontFamily = LutealFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.01).em
    ),
    headlineLarge = TextStyle(
        fontFamily = LutealFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.01).em
    ),
    headlineMedium = TextStyle(
        fontFamily = LutealFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.01).em
    ),
    headlineSmall = TextStyle(
        fontFamily = LutealFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.01).em
    ),
    titleLarge = TextStyle(
        fontFamily = LutealFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.em
    ),
    titleMedium = TextStyle(
        fontFamily = LutealFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.em
    ),
    titleSmall = TextStyle(
        fontFamily = LutealFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.em
    ),
    bodyLarge = TextStyle(
        fontFamily = LutealFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.em
    ),
    bodyMedium = TextStyle(
        fontFamily = LutealFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.em
    ),
    bodySmall = TextStyle(
        fontFamily = LutealFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.em
    ),
    labelLarge = TextStyle(
        fontFamily = LutealFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.01.em
    ),
    labelMedium = TextStyle(
        fontFamily = LutealFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.02.em
    ),
    labelSmall = TextStyle(
        fontFamily = LutealFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.03.em
    )
)

/**
 * Convenience extension to apply OpenType tabular numbers (`tnum`) to numeric metrics,
 * counters, calendar cells, and charts to prevent jitter on changing values.
 */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")
