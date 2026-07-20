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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import fr.luteal.core.designsystem.theme.CelestialCyan
import fr.luteal.core.designsystem.theme.MidnightCosmos
import fr.luteal.core.designsystem.theme.NebulaBlue
import fr.luteal.core.designsystem.theme.NebulaIndigo
import fr.luteal.core.designsystem.theme.OrbitLavender
import fr.luteal.core.designsystem.theme.StarlightGold
import kotlin.random.Random

private data class StarParticle(
    val xRatio: Float,
    val yRatio: Float,
    val radius: Float,
    val alpha: Float
)

@Composable
fun FloatyBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "celestial_bg_transition")

    val floatOffset1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 50f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nebula_offset_1"
    )

    val floatOffset2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -40f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nebula_offset_2"
    )

    val starlightPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "starlight_pulse"
    )

    // Pre-calculate deterministic star field
    val stars = remember {
        val random = Random(42)
        List(45) {
            StarParticle(
                xRatio = random.nextFloat(),
                yRatio = random.nextFloat(),
                radius = random.nextFloat() * 2.5f + 1f,
                alpha = random.nextFloat() * 0.6f + 0.2f
            )
        }
    }

    val spaceGradient = Brush.verticalGradient(
        colors = listOf(
            MidnightCosmos,
            NebulaIndigo,
            MidnightCosmos
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(spaceGradient)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Glowing Orbit Lavender Nebula
            val center1 = Offset(width * 0.3f + floatOffset1, height * 0.25f + floatOffset2)
            val radius1 = width.coerceAtLeast(height) * 0.55f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        OrbitLavender.copy(alpha = 0.25f),
                        OrbitLavender.copy(alpha = 0.06f),
                        Color.Transparent
                    ),
                    center = center1,
                    radius = radius1
                ),
                center = center1,
                radius = radius1
            )

            // Glowing Celestial Cyan & Nebula Blue Glow
            val center2 = Offset(width * 0.85f + floatOffset2, height * 0.6f + floatOffset1)
            val radius2 = width.coerceAtLeast(height) * 0.6f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        CelestialCyan.copy(alpha = 0.22f),
                        NebulaBlue.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = center2,
                    radius = radius2
                ),
                center = center2,
                radius = radius2
            )

            // Soft Starlight Gold Aureole
            val center3 = Offset(width * 0.2f - floatOffset2, height * 0.8f + floatOffset1)
            val radius3 = width.coerceAtLeast(height) * 0.45f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        StarlightGold.copy(alpha = 0.15f * starlightPulse),
                        StarlightGold.copy(alpha = 0.04f),
                        Color.Transparent
                    ),
                    center = center3,
                    radius = radius3
                ),
                center = center3,
                radius = radius3
            )

            // Twinkling Stardust Field
            stars.forEach { star ->
                val x = star.xRatio * width
                val y = star.yRatio * height
                drawCircle(
                    color = Color.White.copy(alpha = (star.alpha * starlightPulse).coerceIn(0.1f, 1.0f)),
                    radius = star.radius,
                    center = Offset(x, y)
                )
            }
        }

        content()
    }
}
