package fr.luteal.core.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import fr.luteal.app.R
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.designsystem.theme.LutealTheme

@Preview(
    name = "Components light",
    showBackground = true,
    widthDp = 360
)
@Preview(
    name = "Components dark",
    showBackground = true,
    widthDp = 360,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Preview(
    name = "Components 200 percent text",
    showBackground = true,
    widthDp = 360,
    fontScale = 2f
)
@Composable
private fun DesignSystemCatalogPreview() {
    LutealTheme {
        val energyDescriptions = (1..5).associateWith { value ->
            stringResource(R.string.editor_energy_value_description, value)
        }
        Column(
            modifier = Modifier.padding(LutealSpacing.md),
            verticalArrangement = Arrangement.spacedBy(LutealSpacing.md)
        ) {
            StatusPill(
                text = stringResource(R.string.recorded_label),
                tone = StatusTone.RECORDED
            )
            StatusPill(
                text = stringResource(R.string.estimated_label),
                tone = StatusTone.ESTIMATED
            )
            AdaptiveActionGroup(
                primary = { modifier ->
                    LutealPrimaryButton(
                        text = stringResource(R.string.action_save_entry),
                        onClick = {},
                        modifier = modifier
                    )
                },
                secondary = { modifier ->
                    LutealSecondaryButton(
                        text = stringResource(R.string.onboarding_button_back),
                        onClick = {},
                        modifier = modifier
                    )
                }
            )
            LutealToggleRow(
                title = stringResource(R.string.duo_share_cycle_day),
                description = stringResource(R.string.duo_share_cycle_day_desc),
                checked = true,
                onCheckedChange = {}
            )
            ObservationScale(
                label = stringResource(R.string.editor_energy),
                supportingText = stringResource(R.string.editor_energy_scale_support),
                value = 3,
                onValueChange = {},
                valueDescription = energyDescriptions::getValue,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
