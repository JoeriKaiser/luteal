package fr.luteal.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val Evergreen40 = Color(0xFF235B4E)
val Evergreen80 = Color(0xFF8FD4BE)
val WarmStone98 = Color(0xFFFCFAF5)

// Linen Ground sits a deliberate step below Porcelain Surface. The previous
// value differed from the surface by roughly two percent, so resting cards
// read as flat page rather than as objects. Luteal stays flat by default, so
// this tonal gap is the only thing doing that grouping work in light mode.
val WarmStone95 = Color(0xFFF1EDE1)
val WarmStone90 = Color(0xFFE6E1D3)
val WarmStone80 = Color(0xFFD6D2C8)
val WarmInk10 = Color(0xFF171C19)
val WarmInk20 = Color(0xFF252B27)
val WarmInk50 = Color(0xFF646B66)

@Immutable
data class PhaseTone(
    val content: Color,
    val container: Color
)

@Immutable
data class PhaseColors(
    val menstrual: PhaseTone,
    val follicular: PhaseTone,
    val ovulatory: PhaseTone,
    val luteal: PhaseTone
)

val LightPhaseColors = PhaseColors(
    menstrual = PhaseTone(Color(0xFF934545), Color(0xFFF5DEDA)),
    follicular = PhaseTone(Color(0xFF486D55), Color(0xFFDCE9DC)),
    ovulatory = PhaseTone(Color(0xFF775F1D), Color(0xFFF1E7C7)),
    luteal = PhaseTone(Color(0xFF5D587E), Color(0xFFE7E2F1))
)

val DarkPhaseColors = PhaseColors(
    menstrual = PhaseTone(Color(0xFFFFB3AE), Color(0xFF542A2B)),
    follicular = PhaseTone(Color(0xFFA9D2B3), Color(0xFF293F30)),
    ovulatory = PhaseTone(Color(0xFFE1CA7C), Color(0xFF463B1E)),
    luteal = PhaseTone(Color(0xFFC9C1EA), Color(0xFF393650))
)

val LocalPhaseColors = staticCompositionLocalOf { LightPhaseColors }

val LightColorScheme = lightColorScheme(
    primary = Evergreen40,
    onPrimary = Color(0xFFF3FFF9),
    primaryContainer = Color(0xFFC6EBDD),
    onPrimaryContainer = Color(0xFF0B3B31),
    secondary = Color(0xFF50645D),
    onSecondary = Color(0xFFF5FFF9),
    secondaryContainer = Color(0xFFD7E8E0),
    onSecondaryContainer = Color(0xFF263B34),
    tertiary = Color(0xFF755B45),
    onTertiary = Color(0xFFFFF8F3),
    tertiaryContainer = Color(0xFFF0DFD2),
    onTertiaryContainer = Color(0xFF493220),
    background = WarmStone95,
    onBackground = WarmInk20,
    surface = WarmStone98,
    onSurface = WarmInk20,
    surfaceVariant = WarmStone90,
    onSurfaceVariant = Color(0xFF555D58),
    outline = Color(0xFF747B76),
    outlineVariant = WarmStone80,
    error = Color(0xFF9D3D40),
    onError = Color(0xFFFFF8F7),
    errorContainer = Color(0xFFFADAD9),
    onErrorContainer = Color(0xFF5C171A),
    scrim = Color(0xFF101512)
)

val DarkColorScheme = darkColorScheme(
    primary = Evergreen80,
    onPrimary = Color(0xFF09382E),
    primaryContainer = Color(0xFF174A3E),
    onPrimaryContainer = Color(0xFFB5F1DC),
    secondary = Color(0xFFB7CCC3),
    onSecondary = Color(0xFF22352E),
    secondaryContainer = Color(0xFF394D45),
    onSecondaryContainer = Color(0xFFD3E9DF),
    tertiary = Color(0xFFE0BFA5),
    onTertiary = Color(0xFF422D1D),
    // Desaturated toward warm grey. The previous clay container read as an
    // orange alert next to the evergreen surfaces around it, which is the
    // wrong signal for a mood observation and for an estimate.
    tertiaryContainer = Color(0xFF4F423A),
    onTertiaryContainer = Color(0xFFF0E0D2),
    background = Color(0xFF121714),
    onBackground = Color(0xFFE4EAE5),
    surface = Color(0xFF181E1B),
    onSurface = Color(0xFFE4EAE5),
    surfaceVariant = Color(0xFF252D29),
    onSurfaceVariant = Color(0xFFC2C9C4),
    outline = Color(0xFF8B938D),
    outlineVariant = Color(0xFF3D4540),
    error = Color(0xFFFFB3B2),
    onError = Color(0xFF5F1218),
    errorContainer = Color(0xFF7D292D),
    onErrorContainer = Color(0xFFFFDAD8),
    scrim = Color(0xFF0B0F0D)
)
