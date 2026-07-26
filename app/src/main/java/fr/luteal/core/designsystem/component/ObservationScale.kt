package fr.luteal.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A one-to-five observation scale.
 *
 * Each step carries a mark whose height grows with its value, so the ordering
 * is legible without reading the numerals. Five identically sized boxes
 * communicated magnitude to screen readers via `stateDescription` and to
 * everyone else not at all.
 *
 * Selection is signalled by container, outline, mark colour, and text weight
 * together. Colour alone never carries it.
 */
@Composable
fun ObservationScale(
    label: String,
    supportingText: String,
    value: Int?,
    onValueChange: (Int?) -> Unit,
    valueDescription: (Int) -> String,
    modifier: Modifier = Modifier
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
    // Steps of 6dp across five options: tall enough to rank at a glance,
    // short enough that step one still reads as a mark rather than a dot.
    val markHeight = (8 + (option - 1) * 6).dp

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
                modifier = Modifier.height(32.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .height(markHeight)
                        .background(markColor, RoundedCornerShape(3.dp))
                )
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
