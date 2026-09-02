package fr.luteal.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class ObservationScaleType {
    PAIN,
    MOOD,
    ENERGY,
    GENERIC
}

/**
 * A one-to-five observation scale with differentiated visual metaphors.
 *
 * Pain uses graduated focal dots (severity).
 * Mood uses emotional valence curves (difficult to positive).
 * Energy uses battery charge levels (depleted to fully charged).
 */
@Composable
fun ObservationScale(
    label: String,
    supportingText: String,
    value: Int?,
    onValueChange: (Int?) -> Unit,
    valueDescription: (Int) -> String,
    modifier: Modifier = Modifier,
    type: ObservationScaleType = ObservationScaleType.GENERIC
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, style = MaterialTheme.typography.titleSmall)
        Text(
            text = supportingText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            (1..5).forEach { option ->
                ScaleStep(
                    option = option,
                    selected = value == option,
                    description = valueDescription(option),
                    type = type,
                    onClick = { onValueChange(option.takeUnless { value == option }) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ScaleStep(
    option: Int,
    selected: Boolean,
    description: String,
    type: ObservationScaleType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val container = if (selected) scheme.primaryContainer else scheme.surface
    val markColor = if (selected) scheme.onPrimaryContainer else scheme.outline
    val labelColor = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant
    val border = if (selected) {
        BorderStroke(2.dp, scheme.primary)
    } else {
        BorderStroke(1.dp, scheme.outlineVariant)
    }

    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 64.dp)
            .semantics {
                role = Role.RadioButton
                stateDescription = description
            },
        shape = MaterialTheme.shapes.small,
        color = container,
        border = border
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier.height(30.dp),
                contentAlignment = Alignment.Center
            ) {
                when (type) {
                    ObservationScaleType.PAIN -> PainIndicator(option, selected, markColor)
                    ObservationScaleType.MOOD -> MoodIndicator(option, markColor)
                    ObservationScaleType.ENERGY -> EnergyIndicator(option, markColor)
                    ObservationScaleType.GENERIC -> GenericIndicator(option, markColor)
                }
            }
            Text(
                text = option.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = labelColor
            )
        }
    }
}

@Composable
private fun PainIndicator(option: Int, selected: Boolean, markColor: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = (3.5f + (option - 1) * 2.2f).dp.toPx()
        if (selected) {
            drawCircle(color = markColor, radius = radius, center = center)
        } else {
            drawCircle(
                color = markColor.copy(alpha = 0.35f),
                radius = radius,
                center = center
            )
        }
    }
}

@Composable
private fun MoodIndicator(option: Int, markColor: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 2.dp.toPx()
        when (option) {
            1 -> {
                drawArc(
                    color = markColor,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(w * 0.15f, h * 0.42f),
                    size = Size(w * 0.7f, h * 0.45f),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            2 -> {
                drawArc(
                    color = markColor,
                    startAngle = 190f,
                    sweepAngle = 160f,
                    useCenter = false,
                    topLeft = Offset(w * 0.2f, h * 0.46f),
                    size = Size(w * 0.6f, h * 0.32f),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            3 -> {
                drawLine(
                    color = markColor,
                    start = Offset(w * 0.2f, h * 0.52f),
                    end = Offset(w * 0.8f, h * 0.52f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
            4 -> {
                drawArc(
                    color = markColor,
                    startAngle = 10f,
                    sweepAngle = 160f,
                    useCenter = false,
                    topLeft = Offset(w * 0.2f, h * 0.32f),
                    size = Size(w * 0.6f, h * 0.32f),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            5 -> {
                drawArc(
                    color = markColor,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(w * 0.15f, h * 0.22f),
                    size = Size(w * 0.7f, h * 0.45f),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }
    }
}

@Composable
private fun EnergyIndicator(option: Int, markColor: Color) {
    Canvas(modifier = Modifier.size(width = 16.dp, height = 24.dp)) {
        val totalSlots = 5
        val slotHeight = 3.dp.toPx()
        val gap = 2.dp.toPx()
        val startY = size.height - slotHeight
        for (i in 1..totalSlots) {
            val y = startY - (i - 1) * (slotHeight + gap)
            val isSlotActive = i <= option
            val slotColor = if (isSlotActive) markColor else markColor.copy(alpha = 0.2f)
            drawRoundRect(
                color = slotColor,
                topLeft = Offset(0f, y),
                size = Size(size.width, slotHeight),
                cornerRadius = CornerRadius(1.5.dp.toPx())
            )
        }
    }
}

@Composable
private fun GenericIndicator(option: Int, markColor: Color) {
    Canvas(modifier = Modifier.size(width = 10.dp, height = 24.dp)) {
        val markHeight = (6 + (option - 1) * 4.5f).dp.toPx()
        val y = size.height - markHeight
        drawRoundRect(
            color = markColor,
            topLeft = Offset(0f, y),
            size = Size(size.width, markHeight),
            cornerRadius = CornerRadius(2.dp.toPx())
        )
    }
}
