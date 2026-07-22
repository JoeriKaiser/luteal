package fr.luteal.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import fr.luteal.app.LutealUiState
import fr.luteal.app.R
import fr.luteal.core.common.FrenchDateFormatter
import fr.luteal.core.designsystem.component.AdaptiveActionGroup
import fr.luteal.core.designsystem.component.LutealCard
import fr.luteal.core.designsystem.component.LutealPrimaryButton
import fr.luteal.core.designsystem.component.LutealSecondaryButton
import fr.luteal.core.designsystem.component.ObservationPill
import fr.luteal.core.designsystem.component.ObservationTone
import fr.luteal.core.designsystem.component.StatusPill
import fr.luteal.core.designsystem.component.StatusTone
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.model.BleedingIntensity
import java.time.temporal.ChronoUnit

@Composable
fun TodayScreen(
    state: LutealUiState,
    onStartPeriod: () -> Unit,
    onEditToday: () -> Unit,
    onBackfillCycle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = LutealSpacing.md, vertical = LutealSpacing.md),
        verticalArrangement = Arrangement.spacedBy(LutealSpacing.md)
    ) {
        ScreenHeader(
            title = stringResource(R.string.today_title),
            subtitle = FrenchDateFormatter.formatFullDate(state.today)
        )

        CycleFactCard(state)

        AdaptiveActionGroup(
            primary = { actionModifier ->
                LutealPrimaryButton(
                    text = stringResource(R.string.action_start_period_short),
                    onClick = onStartPeriod,
                    icon = Icons.Rounded.WaterDrop,
                    modifier = actionModifier
                )
            },
            secondary = { actionModifier ->
                LutealSecondaryButton(
                    text = if (state.todayEntry != null) {
                        stringResource(R.string.action_edit_today)
                    } else {
                        stringResource(R.string.action_log_entry_short)
                    },
                    onClick = onEditToday,
                    icon = if (state.todayEntry != null) Icons.Rounded.Edit else Icons.Rounded.Add,
                    modifier = actionModifier
                )
            }
        )

        TodayObservationCard(state = state, onEditToday = onEditToday)
        EstimateSection(state = state, onBackfillCycle = onBackfillCycle)

        if (state.cycles.size >= 2) {
            CycleStatsSection(state)
        }

        Spacer(Modifier.height(LutealSpacing.lg))
    }
}

@Composable
private fun CycleFactCard(state: LutealUiState) {
    val cycle = state.currentCycle
    LutealCard(modifier = Modifier.fillMaxWidth()) {
        if (cycle == null) {
            Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
                StatusPill(
                    text = stringResource(R.string.local_only_label),
                    tone = StatusTone.LOCAL_ONLY
                )
                Text(
                    text = stringResource(R.string.cycle_no_history_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = stringResource(R.string.cycle_no_history_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val day = state.dayOfCycle ?: 1
            val stackStatus = LocalDensity.current.fontScale > 1.3f
            Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
                if (stackStatus) {
                    Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
                        Text(
                            text = stringResource(R.string.cycle_day, day),
                            style = MaterialTheme.typography.headlineMedium
                        )
                        StatusPill(
                            text = stringResource(R.string.recorded_label),
                            tone = StatusTone.RECORDED
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = stringResource(R.string.cycle_day, day),
                            style = MaterialTheme.typography.headlineMedium
                        )
                        StatusPill(
                            text = stringResource(R.string.recorded_label),
                            tone = StatusTone.RECORDED
                        )
                    }
                }
                Text(
                    text = stringResource(
                        R.string.cycle_day_recorded_support,
                        FrenchDateFormatter.formatShortDate(cycle.startDate)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                state.todayEntry?.bleedingIntensity?.let { intensity ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = stringResource(
                            R.string.today_bleeding_label,
                            bleedingLabel(intensity)
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodayObservationCard(
    state: LutealUiState,
    onEditToday: () -> Unit
) {
    val entry = state.todayEntry
    LutealCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onEditToday
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.today_summary_title),
                style = MaterialTheme.typography.titleMedium
            )
            Icon(
                imageVector = if (entry == null) Icons.Rounded.Add else Icons.Rounded.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.height(LutealSpacing.xs))

        if (entry == null || !entry.hasObservations) {
            Text(
                text = stringResource(R.string.today_summary_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(LutealSpacing.xs))
            Text(
                text = stringResource(R.string.today_summary_open_hint),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)
            ) {
                entry.bleedingIntensity?.let { intensity ->
                    ObservationPill(
                        text = bleedingLabel(intensity),
                        tone = ObservationTone.BLEEDING
                    )
                }
                entry.painLevel?.let { level ->
                    ObservationPill(
                        text = stringResource(R.string.today_pain_label, level),
                        tone = ObservationTone.PAIN
                    )
                }
                entry.moodLevel?.let { level ->
                    ObservationPill(
                        text = stringResource(R.string.today_mood_label, level),
                        tone = ObservationTone.MOOD
                    )
                }
                entry.energyLevel?.let { level ->
                    ObservationPill(
                        text = stringResource(R.string.today_energy_label, level),
                        tone = ObservationTone.ENERGY
                    )
                }
            }
            if (entry.notes.isNotBlank()) {
                Spacer(Modifier.height(LutealSpacing.xs))
                Text(
                    text = stringResource(R.string.today_notes_label, entry.notes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3
                )
            }
        }
    }
}

@Composable
private fun EstimateSection(state: LutealUiState, onBackfillCycle: () -> Unit) {
    LutealCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.estimate_title),
                style = MaterialTheme.typography.titleMedium
            )
            StatusPill(
                text = stringResource(R.string.estimated_label),
                tone = StatusTone.ESTIMATED
            )
        }

        Spacer(Modifier.height(LutealSpacing.xs))

        val estimate = state.estimate
        if (estimate == null) {
            Text(
                text = stringResource(R.string.estimate_unavailable_short),
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(LutealSpacing.xxs))
            Text(
                text = stringResource(R.string.estimate_unavailable_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onBackfillCycle) {
                Text(text = stringResource(R.string.backfill_action))
            }
        } else {
            Text(
                text = stringResource(
                    R.string.estimate_range,
                    FrenchDateFormatter.formatShortDate(estimate.earliestDate),
                    FrenchDateFormatter.formatShortDate(estimate.latestDate)
                ),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(LutealSpacing.xxs))
            val daysUntil = ChronoUnit.DAYS.between(state.today, estimate.earliestDate).toInt()
            Text(
                text = if (daysUntil > 0) {
                    stringResource(R.string.estimate_days_remaining, daysUntil)
                } else {
                    stringResource(R.string.estimate_in_progress)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = pluralStringResource(
                    R.plurals.estimate_basis,
                    estimate.cycleCount,
                    estimate.cycleCount
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.estimate_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CycleStatsSection(state: LutealUiState) {
    val completedLengths = state.cycles
        .filterNot { it.isCurrent }
        .map { it.lengthInDays }
        .filter { it > 0 }
    if (completedLengths.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
        Text(
            text = stringResource(R.string.cycle_stats_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(
                    R.string.cycle_stats_avg_length,
                    completedLengths.average().toInt()
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.cycle_stats_total_count, state.cycles.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun bleedingLabel(intensity: BleedingIntensity): String = stringResource(
    when (intensity) {
        BleedingIntensity.NONE -> R.string.bleeding_none
        BleedingIntensity.SPOTTING -> R.string.bleeding_spotting
        BleedingIntensity.LIGHT -> R.string.bleeding_light
        BleedingIntensity.MEDIUM -> R.string.bleeding_medium
        BleedingIntensity.HEAVY -> R.string.bleeding_heavy
    }
)

@Composable
internal fun ScreenHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xxs)) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
