package fr.luteal.core.designsystem.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import fr.luteal.core.designsystem.theme.FloatyLavender
import fr.luteal.core.designsystem.theme.MintSoftGlow
import fr.luteal.core.designsystem.theme.RoseQuartz

@Composable
fun FloatyBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "floaty_bg_transition")

    val floatOffset1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_offset_1"
    )

    val floatOffset2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_offset_2"
    )

    val floatOffset3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 30f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_offset_3"
    )

    val backgroundColor = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Soft floating Rose Quartz gradient circle
            val center1 = Offset(width * 0.25f + floatOffset1, height * 0.2f + floatOffset2)
            val radius1 = width.coerceAtLeast(height) * 0.45f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        RoseQuartz.copy(alpha = 0.35f),
                        RoseQuartz.copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = center1,
                    radius = radius1
                ),
                center = center1,
                radius = radius1
            )

            // Soft floating Floaty Lavender gradient circle
            val center2 = Offset(width * 0.8f + floatOffset2, height * 0.5f + floatOffset3)
            val radius2 = width.coerceAtLeast(height) * 0.5f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        FloatyLavender.copy(alpha = 0.35f),
                        FloatyLavender.copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = center2,
                    radius = radius2
                ),
                center = center2,
                radius = radius2
            )

            // Soft floating Mint Soft Glow gradient circle
            val center3 = Offset(width * 0.3f + floatOffset3, height * 0.8f + floatOffset1)
            val radius3 = width.coerceAtLeast(height) * 0.4f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        MintSoftGlow.copy(alpha = 0.30f),
                        MintSoftGlow.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = center3,
                    radius = radius3
                ),
                center = center3,
                radius = radius3
            )
        }

        content()
    }
}
