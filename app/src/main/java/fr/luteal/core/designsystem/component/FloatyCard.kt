package fr.luteal.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.luteal.core.designsystem.theme.CelestialCyan
import fr.luteal.core.designsystem.theme.GlassmorphicCardBg
import fr.luteal.core.designsystem.theme.OrbitLavender

@Composable
fun FloatyCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardShape = RoundedCornerShape(28.dp)

    val borderGradient = Brush.linearGradient(
        colors = listOf(
            CelestialCyan.copy(alpha = 0.45f),
            OrbitLavender.copy(alpha = 0.30f),
            Color.White.copy(alpha = 0.15f)
        )
    )

    val cardModifier = modifier
        .shadow(
            elevation = 12.dp,
            shape = cardShape,
            ambientColor = CelestialCyan.copy(alpha = 0.2f),
            spotColor = OrbitLavender.copy(alpha = 0.3f)
        )
        .clip(cardShape)
        .background(GlassmorphicCardBg)
        .border(width = 1.dp, brush = borderGradient, shape = cardShape)
        .then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            }
        )

    Column(
        modifier = cardModifier.padding(20.dp),
        content = content
    )
}
