package fr.luteal.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

object LutealTheme {
    val phaseColors: PhaseColors
        @Composable
        @ReadOnlyComposable
        get() = LocalPhaseColors.current
}

@Composable
fun LutealTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val phaseColors = PhaseColors()

    CompositionLocalProvider(
        LocalPhaseColors provides phaseColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LutealTypography,
            shapes = LutealShapes,
            content = content
        )
    }
}
