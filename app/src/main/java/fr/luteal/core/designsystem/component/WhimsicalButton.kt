package fr.luteal.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.luteal.core.designsystem.theme.FloatyLavender
import fr.luteal.core.designsystem.theme.RoseQuartz

@Composable
fun WhimsicalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String,
    icon: ImageVector? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.95f else 1.0f,
        animationSpec = spring(stiffness = 400f, dampingRatio = 0.6f),
        label = "whimsical_button_scale"
    )

    val buttonGradient = if (enabled) {
        Brush.horizontalGradient(
            colors = listOf(
                RoseQuartz,
                FloatyLavender
            )
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(
                RoseQuartz.copy(alpha = 0.4f),
                FloatyLavender.copy(alpha = 0.4f)
            )
        )
    }

    val contentColor = if (enabled) Color.White else Color.White.copy(alpha = 0.6f)

    Row(
        modifier = modifier
            .scale(scale)
            .clip(CircleShape)
            .background(brush = buttonGradient, shape = CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}
