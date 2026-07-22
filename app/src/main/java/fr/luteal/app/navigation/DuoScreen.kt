package fr.luteal.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.luteal.app.LutealUiState
import fr.luteal.app.R
import fr.luteal.core.common.FrenchDateFormatter
import fr.luteal.core.designsystem.component.LutealCard
import fr.luteal.core.designsystem.component.LutealToggleRow
import fr.luteal.core.designsystem.component.StatusPill
import fr.luteal.core.designsystem.component.StatusTone
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.model.DuoSharingField

@Composable
fun DuoScreen(
    state: LutealUiState,
    onSharingChange: (DuoSharingField, Boolean) -> Unit
) {
    val sharing = state.preferences.duoSharing
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = LutealSpacing.md, vertical = LutealSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(LutealSpacing.lg)
    ) {
        ScreenHeader(
            title = stringResource(R.string.duo_title),
            subtitle = stringResource(R.string.duo_subtitle)
        )

        ConnectionSummary()
        PartnerPreview(state)

        SharingSectionHeader(
            title = stringResource(R.string.duo_cycle_group_title),
            body = stringResource(R.string.duo_sharing_body)
        )
        Column {
            LutealToggleRow(
                title = stringResource(R.string.duo_share_cycle_day),
                description = stringResource(R.string.duo_share_cycle_day_desc),
                checked = sharing.shareCycleDay,
                onCheckedChange = { onSharingChange(DuoSharingField.CYCLE_DAY, it) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LutealToggleRow(
                title = stringResource(R.string.duo_share_estimate),
                description = stringResource(R.string.duo_share_estimate_desc),
                checked = sharing.sharePeriodEstimate,
                onCheckedChange = { onSharingChange(DuoSharingField.PERIOD_ESTIMATE, it) }
            )
        }

        SharingSectionHeader(
            title = stringResource(R.string.duo_feelings_group_title),
            body = stringResource(R.string.duo_feelings_group_body)
        )
        Column {
            LutealToggleRow(
                title = stringResource(R.string.duo_share_mood),
                description = stringResource(R.string.duo_share_mood_desc),
                checked = sharing.shareMood,
                onCheckedChange = { onSharingChange(DuoSharingField.MOOD, it) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LutealToggleRow(
                title = stringResource(R.string.duo_share_energy),
                description = stringResource(R.string.duo_share_energy_desc),
                checked = sharing.shareEnergy,
                onCheckedChange = { onSharingChange(DuoSharingField.ENERGY, it) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LutealToggleRow(
                title = stringResource(R.string.duo_share_support),
                description = stringResource(R.string.duo_share_support_desc),
                checked = sharing.shareSupportRequests,
                onCheckedChange = { onSharingChange(DuoSharingField.SUPPORT_REQUESTS, it) }
            )
        }

        LutealCard(modifier = Modifier.fillMaxWidth()) {
            StatusPill(
                text = stringResource(R.string.private_label),
                tone = StatusTone.PRIVATE
            )
            Spacer(Modifier.height(LutealSpacing.xs))
            Text(
                text = stringResource(R.string.duo_private_notes_never_shared),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(LutealSpacing.md))
    }
}

@Composable
private fun ConnectionSummary() {
    Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
        StatusPill(
            text = stringResource(R.string.duo_local_status),
            tone = StatusTone.LOCAL_ONLY
        )
        Text(
            text = stringResource(R.string.duo_not_connected_title),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = stringResource(R.string.duo_not_connected_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SharingSectionHeader(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xxs)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PartnerPreview(state: LutealUiState) {
    val sharing = state.preferences.duoSharing
    val visibleItems = buildList {
        if (sharing.shareCycleDay) {
            add(
                state.dayOfCycle?.let {
                    stringResource(R.string.duo_preview_cycle_day, it)
                } ?: stringResource(R.string.duo_preview_no_cycle)
            )
        }
        val estimate = state.estimate
        if (sharing.sharePeriodEstimate && estimate != null) {
            add(
                stringResource(
                    R.string.duo_preview_estimate,
                    FrenchDateFormatter.formatShortDate(estimate.earliestDate),
                    FrenchDateFormatter.formatShortDate(estimate.latestDate)
                )
            )
        }
        val todayEntry = state.todayEntry
        if (sharing.shareMood && todayEntry?.moodLevel != null) {
            add(stringResource(R.string.duo_preview_mood, todayEntry.moodLevel))
        }
        if (sharing.shareEnergy && todayEntry?.energyLevel != null) {
            add(stringResource(R.string.duo_preview_energy, todayEntry.energyLevel))
        }
        if (sharing.shareSupportRequests) {
            add(stringResource(R.string.duo_preview_support_ready))
        }
    }

    LutealCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.duo_preview_title),
                style = MaterialTheme.typography.titleMedium
            )
            StatusPill(
                text = stringResource(R.string.duo_preview_local_label),
                tone = StatusTone.LOCAL_ONLY
            )
        }
        Spacer(Modifier.height(LutealSpacing.xs))
        Text(
            text = stringResource(R.string.duo_preview_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(LutealSpacing.sm))
        if (visibleItems.isEmpty()) {
            Text(
                text = stringResource(R.string.duo_preview_hidden),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
                visibleItems.forEach { item ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(text = item, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
