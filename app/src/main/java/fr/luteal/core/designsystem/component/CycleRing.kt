package fr.luteal.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp

/**
 * The Luteal emblem, sized up to frame the current cycle day.
 *
 * The four cardinal arcs match the launcher mark (`ic_launcher_foreground`) redrawn
 * at screen scale: restrained cyclical geometry, which is the one identity
 * gesture the design system allows.
 *
 * **The arcs carry no data and must not start carrying any.** A ring that
 * filled in proportion to the cycle would need a total to divide by, and the
 * only candidates are a fixed 28-day assumption or a prediction. Both are
 * explicit product non-goals: the first assumes a regular cycle, the second
 * presents an estimate as a certainty. The recorded day count is the fact
 * being reported here, and it is reported as a number.
 */
@Composable
fun CycleDayRing(
    dayOfCycle: Int,
    accessibleLabel: String,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    // The numeral inside scales with the user's font setting, so the frame has
    // to grow with it or the digits collide with the arcs.
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.8f)
    val diameter = 132.dp * fontScale
    val stroke = 7.dp
    // All arcs carry the same weight on purpose. An accented arc against a
    // pale one reads as a filled track, which is the progress meter this
    // component must not become.
    val arcColor = scheme.primary

    Box(
        modifier = modifier
            .size(diameter)
            .clearAndSetSemantics { contentDescription = accessibleLabel },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(diameter)) {
            val strokePx = stroke.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            val topLeft = Offset(inset, inset)
            val style = Stroke(width = strokePx, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            // Four cardinal arcs (top, right, bottom, left) separated by 20-degree
            // diagonal césures, matching the launcher mark.
            val gapDegrees = 20f
            val sweep = 90f - gapDegrees
            val halfSweep = sweep / 2f

            // Top arc (centered at 270 degrees)
            drawArc(
                color = arcColor,
                startAngle = 270f - halfSweep,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = style
            )
            // Right arc (centered at 0 degrees)
            drawArc(
                color = arcColor,
                startAngle = 0f - halfSweep,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = style
            )
            // Bottom arc (centered at 90 degrees)
            drawArc(
                color = arcColor,
                startAngle = 90f - halfSweep,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = style
            )
            // Left arc (centered at 180 degrees)
            drawArc(
                color = arcColor,
                startAngle = 180f - halfSweep,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = style
            )
        }
        Text(
            text = dayOfCycle.toString(),
            style = MaterialTheme.typography.displaySmall,
            color = scheme.onSurface
        )
    }
}
