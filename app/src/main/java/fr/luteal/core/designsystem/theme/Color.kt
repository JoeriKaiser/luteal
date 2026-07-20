package fr.luteal.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Primary
val RoseQuartz = Color(0xFFE89BA7)
val RoseQuartzDark = Color(0xFFC76D7C)

// Secondary
val FloatyLavender = Color(0xFFB8A4DC)
val LavenderGlow = Color(0xFFD4C8F0)

// Tertiary
val WarmPeach = Color(0xFFF7C59F)
val MintSoftGlow = Color(0xFFA8E6CF)

// Background
val NebularDust = Color(0xFFFAF7FA)
val NebularDustDark = Color(0xFF1C1720)

// Surface
val SurfaceFloatyLight = Color(0xFFFFFFFF)
val SurfaceFloatyDark = Color(0xFF26202B)

// Accent / Phase colors
val MenstrualColor = Color(0xFFE57373)
val FollicularColor = Color(0xFF81C784)
val OvulatoryColor = Color(0xFFFFD54F)
val LutealColor = Color(0xFFBA68C8)

@Immutable
data class PhaseColors(
    val menstrual: Color = MenstrualColor,
    val follicular: Color = FollicularColor,
    val ovulatory: Color = OvulatoryColor,
    val luteal: Color = LutealColor
)

val LocalPhaseColors = staticCompositionLocalOf { PhaseColors() }

val LightColorScheme = lightColorScheme(
    primary = RoseQuartz,
    onPrimary = Color.White,
    primaryContainer = LavenderGlow,
    onPrimaryContainer = RoseQuartzDark,
    secondary = FloatyLavender,
    onSecondary = Color.White,
    secondaryContainer = LavenderGlow,
    onSecondaryContainer = Color(0xFF382056),
    tertiary = WarmPeach,
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = MintSoftGlow,
    onTertiaryContainer = Color(0xFF003829),
    background = NebularDust,
    onBackground = Color(0xFF1C1720),
    surface = SurfaceFloatyLight,
    onSurface = Color(0xFF1C1720),
    surfaceVariant = NebularDust,
    onSurfaceVariant = Color(0xFF49454F)
)

val DarkColorScheme = darkColorScheme(
    primary = RoseQuartzDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5A2A34),
    onPrimaryContainer = RoseQuartz,
    secondary = FloatyLavender,
    onSecondary = Color(0xFF281347),
    secondaryContainer = Color(0xFF3E2C5E),
    onSecondaryContainer = LavenderGlow,
    tertiary = WarmPeach,
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF5C3C1A),
    onTertiaryContainer = WarmPeach,
    background = NebularDustDark,
    onBackground = Color(0xFFE6E1E5),
    surface = SurfaceFloatyDark,
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF322A38),
    onSurfaceVariant = Color(0xFFCAC4D0)
)
