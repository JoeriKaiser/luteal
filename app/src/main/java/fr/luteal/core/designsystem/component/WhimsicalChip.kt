package fr.luteal.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.luteal.core.designsystem.theme.FloatyLavender
import fr.luteal.core.designsystem.theme.RoseQuartz

@Composable
fun WhimsicalChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val animatedBgColor by animateColorAsState(
        targetValue = if (selected) {
            RoseQuartz.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
        },
        animationSpec = tween(durationMillis = 250),
        label = "chip_bg_color"
    )

    val animatedContentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 250),
        label = "chip_content_color"
    )

    val borderBrush = if (selected) {
        Brush.horizontalGradient(
            colors = listOf(
                RoseQuartz,
                FloatyLavender
            )
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )
        )
    }

    val chipShape = CircleShape

    Row(
        modifier = modifier
            .shadow(
                elevation = if (selected) 4.dp else 0.dp,
                shape = chipShape,
                ambientColor = RoseQuartz,
                spotColor = FloatyLavender
            )
            .clip(chipShape)
            .background(color = animatedBgColor, shape = chipShape)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                brush = borderBrush,
                shape = chipShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = animatedContentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = animatedContentColor
        )
    }
}
