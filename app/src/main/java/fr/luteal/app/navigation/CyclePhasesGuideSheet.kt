package fr.luteal.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.luteal.app.R
import fr.luteal.core.designsystem.component.LutealCard
import fr.luteal.core.designsystem.component.LutealPrimaryButton
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.model.CyclePhase
import fr.luteal.core.model.ClinicalSources
import fr.luteal.core.model.PartnerPhaseCapsules

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CyclePhasesGuideSheet(
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val uriHandler = LocalUriHandler.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LutealSpacing.lg, vertical = LutealSpacing.md),
            verticalArrangement = Arrangement.spacedBy(LutealSpacing.md)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xxs)) {
                Text(
                    text = stringResource(R.string.duo_guide_sheet_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.duo_guide_sheet_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            CyclePhase.entries.forEach { phase ->
                val tone = phaseTone(phase)
                val capsule = PartnerPhaseCapsules.forPhase(phase)
                val anchorText = phaseAnchorLabel(phase)
                val descText = phaseCapsuleDescription(phase)
                LutealCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(tone.content, CircleShape)
                            )
                            Text(
                                text = anchorText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = tone.content
                            )
                        }

                        Text(
                            text = descText,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Text(
                            text = stringResource(R.string.fact_source_label, capsule.source),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val capsuleA11y = stringResource(R.string.fact_view_source_named, capsule.source)
                        TextButton(
                            onClick = { uriHandler.openUri(capsule.url) },
                            modifier = Modifier
                                .align(Alignment.End)
                                .semantics { contentDescription = capsuleA11y }
                        ) {
                            Text(text = stringResource(R.string.fact_view_source))
                        }
                    }
                }
            }

            LutealCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
                    Text(
                        text = stringResource(R.string.duo_guide_variability_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.duo_guide_variability_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.fact_source_label, ClinicalSources.FEHRING),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val fehringA11y = stringResource(R.string.fact_view_source_named, ClinicalSources.FEHRING)
                    TextButton(
                        onClick = { uriHandler.openUri(ClinicalSources.FEHRING_URL) },
                        modifier = Modifier
                            .align(Alignment.End)
                            .semantics { contentDescription = fehringA11y }
                    ) {
                        Text(text = stringResource(R.string.fact_view_source))
                    }
                }
            }

            Spacer(Modifier.height(LutealSpacing.xs))

            LutealPrimaryButton(
                text = stringResource(R.string.duo_guide_sheet_close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(LutealSpacing.md))
        }
    }
}
