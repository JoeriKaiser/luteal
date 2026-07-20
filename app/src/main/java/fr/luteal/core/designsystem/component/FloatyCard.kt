package fr.luteal.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import fr.luteal.core.designsystem.theme.FloatyLavender
import fr.luteal.core.designsystem.theme.LavenderGlow
import fr.luteal.core.designsystem.theme.RoseQuartz

@Composable
fun FloatyCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardShape = RoundedCornerShape(28.dp)
    val borderGradient = Brush.linearGradient(
        colors = listOf(
            RoseQuartz.copy(alpha = 0.6f),
            FloatyLavender.copy(alpha = 0.4f),
            LavenderGlow.copy(alpha = 0.6f)
        )
    )

    val surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)

    val cardModifier = modifier
        .shadow(elevation = 8.dp, shape = cardShape)
        .clip(cardShape)
        .background(surfaceColor, shape = cardShape)
        .border(width = 1.dp, brush = borderGradient, shape = cardShape)
        .then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            }
        )

    Column(
        modifier = cardModifier,
        content = content
    )
}
