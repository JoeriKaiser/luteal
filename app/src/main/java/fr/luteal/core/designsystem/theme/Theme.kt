package fr.luteal.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun LutealTheme(
    darkTheme: Boolean = true, // Astronomy cosmic theme defaults to dark mode
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(
        LocalPhaseColors provides PhaseColors()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LutealTypography,
            shapes = LutealShapes,
            content = content
        )
    }
}
