package fr.luteal.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.luteal.core.designsystem.theme.CelestialCyan
import fr.luteal.core.designsystem.theme.MidnightCosmos
import fr.luteal.core.designsystem.theme.OrbitLavender
import fr.luteal.core.designsystem.theme.StarlightGold

@Composable
fun WhimsicalButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val buttonGradient = if (enabled) {
        Brush.linearGradient(
            colors = listOf(
                CelestialCyan,
                OrbitLavender,
                StarlightGold.copy(alpha = 0.90f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.Gray.copy(alpha = 0.4f),
                Color.DarkGray.copy(alpha = 0.4f)
            )
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(buttonGradient)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MidnightCosmos,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MidnightCosmos,
            fontWeight = FontWeight.Bold
        )
    }
}
