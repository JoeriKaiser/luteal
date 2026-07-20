package fr.luteal.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.luteal.core.designsystem.theme.FollicularColor
import fr.luteal.core.designsystem.theme.LutealColor
import fr.luteal.core.designsystem.theme.MenstrualColor
import fr.luteal.core.designsystem.theme.OvulatoryColor
import fr.luteal.core.model.CyclePhase

@Composable
fun CyclePhaseBadge(
    phase: CyclePhase,
    modifier: Modifier = Modifier
) {
    val (phaseColor, phaseIcon) = when (phase) {
        CyclePhase.MENSTRUAL -> MenstrualColor to Icons.Rounded.WaterDrop
        CyclePhase.FOLLICULAR -> FollicularColor to Icons.Rounded.Spa
        CyclePhase.OVULATORY -> OvulatoryColor to Icons.Rounded.WbSunny
        CyclePhase.LUTEAL -> LutealColor to Icons.Rounded.NightsStay
    }

    val badgeShape = CircleShape
    val glowingBackground = phaseColor.copy(alpha = 0.18f)
    val glowingBorder = Brush.linearGradient(
        colors = listOf(
            phaseColor.copy(alpha = 0.8f),
            phaseColor.copy(alpha = 0.3f)
        )
    )

    Row(
        modifier = modifier
            .shadow(
                elevation = 4.dp,
                shape = badgeShape,
                ambientColor = phaseColor,
                spotColor = phaseColor
            )
            .clip(badgeShape)
            .background(color = glowingBackground, shape = badgeShape)
            .border(width = 1.dp, brush = glowingBorder, shape = badgeShape)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = phaseIcon,
            contentDescription = null,
            tint = phaseColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = phase.displayName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = phaseColor
        )
    }
}
