package fr.luteal.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import fr.luteal.core.designsystem.theme.LocalPhaseColors
import androidx.compose.ui.unit.dp

enum class StatusTone {
    RECORDED,
    ESTIMATED,
    PRIVATE,
    LOCAL_ONLY,
    NEUTRAL
}

enum class ObservationTone {
    BLEEDING,
    PAIN,
    MOOD,
    ENERGY
}

@Composable
fun StatusPill(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val treatment = when (tone) {
        StatusTone.RECORDED -> PillTreatment(
            content = colors.onPrimaryContainer,
            container = colors.primaryContainer,
            icon = Icons.Rounded.CheckCircle
        )
        StatusTone.ESTIMATED -> PillTreatment(
            content = colors.onTertiaryContainer,
            container = colors.tertiaryContainer,
            icon = Icons.Rounded.Schedule
        )
        StatusTone.PRIVATE -> PillTreatment(
            content = colors.onSecondaryContainer,
            container = colors.secondaryContainer,
            icon = Icons.Rounded.Lock
        )
        StatusTone.LOCAL_ONLY -> PillTreatment(
            content = colors.primary,
            container = colors.primaryContainer,
            icon = Icons.Rounded.Lock
        )
        StatusTone.NEUTRAL -> PillTreatment(
            content = colors.onSurfaceVariant,
            container = colors.surfaceVariant,
            icon = Icons.Rounded.Info
        )
    }
    Pill(text = text, treatment = treatment, modifier = modifier)
}

@Composable
fun ObservationPill(
    text: String,
    tone: ObservationTone,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val phaseColors = LocalPhaseColors.current
    val treatment = when (tone) {
        ObservationTone.BLEEDING -> PillTreatment(
            content = phaseColors.menstrual.content,
            container = phaseColors.menstrual.container,
            icon = Icons.Rounded.WaterDrop
        )
        ObservationTone.PAIN -> PillTreatment(
            content = colors.onSecondaryContainer,
            container = colors.secondaryContainer,
            icon = Icons.Rounded.Bolt
        )
        ObservationTone.MOOD -> PillTreatment(
            content = colors.onTertiaryContainer,
            container = colors.tertiaryContainer,
            icon = Icons.Rounded.Favorite
        )
        ObservationTone.ENERGY -> PillTreatment(
            content = colors.onSurfaceVariant,
            container = colors.surfaceVariant,
            icon = Icons.Rounded.Speed
        )
    }
    Pill(text = text, treatment = treatment, modifier = modifier)
}

@Composable
private fun Pill(
    text: String,
    treatment: PillTreatment,
    modifier: Modifier
) {
    Row(
        modifier = modifier
            .heightIn(min = 32.dp)
            .background(treatment.container, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = treatment.icon,
            contentDescription = null,
            tint = treatment.content,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = text,
            color = treatment.content,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1
        )
    }
}

private data class PillTreatment(
    val content: Color,
    val container: Color,
    val icon: ImageVector
)
