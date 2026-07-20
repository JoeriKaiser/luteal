package fr.luteal.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brightness2
import androidx.compose.material.icons.rounded.Brightness5
import androidx.compose.material.icons.rounded.Brightness7
import androidx.compose.material.icons.rounded.NightlightRound
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.luteal.core.designsystem.theme.AuroraCyan
import fr.luteal.core.designsystem.theme.EclipseCrimson
import fr.luteal.core.designsystem.theme.GalaxyIndigo
import fr.luteal.core.designsystem.theme.SolarAmber
import fr.luteal.core.model.CyclePhase

@Composable
fun CyclePhaseBadge(
    phase: CyclePhase,
    modifier: Modifier = Modifier
) {
    val (badgeColor, phaseIcon, moonName) = when (phase) {
        CyclePhase.MENSTRUAL -> Triple(EclipseCrimson, Icons.Rounded.NightlightRound, "Nouvelle Lune • Règles")
        CyclePhase.FOLLICULAR -> Triple(AuroraCyan, Icons.Rounded.Brightness2, "Croissant • Folliculaire")
        CyclePhase.OVULATORY -> Triple(SolarAmber, Icons.Rounded.Brightness7, "Pleine Lune • Ovulation")
        CyclePhase.LUTEAL -> Triple(GalaxyIndigo, Icons.Rounded.Brightness5, "Dernier Quartier • Lutéale")
    }

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(badgeColor.copy(alpha = 0.20f))
            .border(width = 1.dp, color = badgeColor.copy(alpha = 0.60f), shape = CircleShape)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = phaseIcon,
            contentDescription = null,
            tint = badgeColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = moonName,
            style = MaterialTheme.typography.labelMedium,
            color = badgeColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}
