package fr.luteal.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Celestial Primary & Accents
val LunarSilver = Color(0xFFE2E8F0)
val StarlightGold = Color(0xFFFFD166)
val CelestialCyan = Color(0xFF4CC9F0)
val OrbitLavender = Color(0xFF9D4EDD)
val NebulaBlue = Color(0xFF3A86EF)
val CosmicMint = Color(0xFF06D6A0)

// Deep Space Backgrounds
val MidnightCosmos = Color(0xFF090B15)
val NebulaIndigo = Color(0xFF0E1225)
val StarlightDarkSurface = Color(0xFF14182E)
val GlassmorphicCardBg = Color(0xDD181D36)

// Moon Phase & Cycle Colors (Astronomical metaphors)
val EclipseCrimson = Color(0xFFEF476F)  // Phase Menstruelle / Nouvelle Lune
val AuroraCyan = Color(0xFF06D6A0)      // Phase Folliculaire / Premier Croissant
val SolarAmber = Color(0xFFFFD166)      // Phase Ovulatoire / Pleine Lune
val GalaxyIndigo = Color(0xFF9D4EDD)    // Phase Lutéale / Dernier Quartier

@Immutable
data class PhaseColors(
    val menstrual: Color = EclipseCrimson,
    val follicular: Color = AuroraCyan,
    val ovulatory: Color = SolarAmber,
    val luteal: Color = GalaxyIndigo
)

val LocalPhaseColors = staticCompositionLocalOf { PhaseColors() }

// Astronomy Dark Scheme (Default)
val DarkColorScheme = darkColorScheme(
    primary = StarlightGold,
    onPrimary = MidnightCosmos,
    primaryContainer = Color(0xFF2A2744),
    onPrimaryContainer = StarlightGold,
    secondary = CelestialCyan,
    onSecondary = MidnightCosmos,
    secondaryContainer = Color(0xFF16324D),
    onSecondaryContainer = CelestialCyan,
    tertiary = OrbitLavender,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF361952),
    onTertiaryContainer = OrbitLavender,
    background = MidnightCosmos,
    onBackground = LunarSilver,
    surface = StarlightDarkSurface,
    onSurface = LunarSilver,
    surfaceVariant = NebulaIndigo,
    onSurfaceVariant = Color(0xFFCBD5E1)
)

// Astronomy Light Scheme (Soft Moonlight)
val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1E293B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E8F0),
    onPrimaryContainer = Color(0xFF0F172A),
    secondary = NebulaBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDBEAFE),
    onSecondaryContainer = Color(0xFF1E40AF),
    tertiary = OrbitLavender,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF3E8FF),
    onTertiaryContainer = Color(0xFF6B21A8),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569)
)
