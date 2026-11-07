package fr.luteal.core.designsystem.component

import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import fr.luteal.core.designsystem.theme.LocalReducedMotion

@Composable
fun LutealPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    trailingIconRotation: Float = 0f,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val reducedMotion = LocalReducedMotion.current
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled && !loading && !reducedMotion) 0.96f else 1f,
        animationSpec = tween(durationMillis = 150, easing = EaseOut),
        label = "primary_button_press_scale"
    )
    val contentPadding = when {
        icon != null && !loading -> ButtonDefaults.ButtonWithIconContentPadding
        trailingIcon != null && !loading -> PaddingValues(start = 24.dp, top = 8.dp, end = 16.dp, bottom = 8.dp)
        else -> ButtonDefaults.ContentPadding
    }

    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.medium,
        contentPadding = contentPadding,
        interactionSource = interactionSource
    ) {
        ButtonContent(
            text = text,
            icon = icon,
            trailingIcon = trailingIcon,
            trailingIconRotation = trailingIconRotation,
            loading = loading
        )
    }
}

@Composable
fun LutealSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    trailingIconRotation: Float = 0f,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val reducedMotion = LocalReducedMotion.current
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled && !reducedMotion) 0.96f else 1f,
        animationSpec = tween(durationMillis = 150, easing = EaseOut),
        label = "secondary_button_press_scale"
    )
    val contentPadding = when {
        icon != null -> ButtonDefaults.ButtonWithIconContentPadding
        trailingIcon != null -> PaddingValues(start = 24.dp, top = 8.dp, end = 16.dp, bottom = 8.dp)
        else -> ButtonDefaults.ContentPadding
    }

    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        contentPadding = contentPadding,
        interactionSource = interactionSource
    ) {
        ButtonContent(
            text = text,
            icon = icon,
            trailingIcon = trailingIcon,
            trailingIconRotation = trailingIconRotation,
            loading = false
        )
    }
}

@Composable
private fun RowScope.ButtonContent(
    text: String,
    icon: ImageVector?,
    trailingIcon: ImageVector? = null,
    trailingIconRotation: Float = 0f,
    loading: Boolean
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            loading -> CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            icon != null -> Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
        if (trailingIcon != null && !loading) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = trailingIconRotation }
            )
        }
    }
}
