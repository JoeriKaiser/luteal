package fr.luteal.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.luteal.core.designsystem.theme.LutealSpacing

/**
 * Relative weight of a grouped surface.
 *
 * Screens stack several cards, and when every one of them carries identical
 * padding, outline, and container colour the eye has nowhere to land. Emphasis
 * varies those three properties together so hierarchy survives without
 * elevation, which Luteal reserves for transient overlays.
 */
enum class LutealCardEmphasis {
    /** One per screen at most: the fact the screen exists to report. */
    HERO,

    /** The default grouped surface. */
    STANDARD,

    /** Secondary grouping on Stone Container, no outline. */
    QUIET
}

@Composable
fun LutealCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    emphasis: LutealCardEmphasis = LutealCardEmphasis.STANDARD,
    content: @Composable ColumnScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val containerColor = when (emphasis) {
        LutealCardEmphasis.HERO, LutealCardEmphasis.STANDARD -> scheme.surface
        LutealCardEmphasis.QUIET -> scheme.surfaceVariant
    }
    val border = when (emphasis) {
        // A full-strength outline, not outlineVariant: the hero card has to
        // hold its own against the quieter surfaces stacked below it.
        LutealCardEmphasis.HERO -> BorderStroke(1.dp, scheme.outline)
        LutealCardEmphasis.STANDARD -> BorderStroke(1.dp, scheme.outlineVariant)
        LutealCardEmphasis.QUIET -> null
    }
    val contentPadding: Dp = when (emphasis) {
        LutealCardEmphasis.HERO -> LutealSpacing.xl
        LutealCardEmphasis.STANDARD, LutealCardEmphasis.QUIET -> LutealSpacing.lg
    }
    val colors = CardDefaults.cardColors(containerColor = containerColor)

    if (onClick == null) {
        Card(
            modifier = modifier,
            shape = MaterialTheme.shapes.large,
            colors = colors,
            border = border,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    } else {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = MaterialTheme.shapes.large,
            colors = colors,
            border = border,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    }
}
