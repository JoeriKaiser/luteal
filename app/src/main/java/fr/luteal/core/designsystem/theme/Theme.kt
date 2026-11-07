package fr.luteal.core.designsystem.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

val LocalReducedMotion = compositionLocalOf { false }

@Composable
fun LutealTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isReducedMotion = remember(context) {
        try {
            val animatorScale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
            val transitionScale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                1.0f
            )
            animatorScale == 0f || transitionScale == 0f
        } catch (_: Exception) {
            false
        }
    }

    CompositionLocalProvider(
        LocalPhaseColors provides if (darkTheme) DarkPhaseColors else LightPhaseColors,
        LocalReducedMotion provides isReducedMotion
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = LutealTypography,
            shapes = LutealShapes,
            content = content
        )
    }
}
