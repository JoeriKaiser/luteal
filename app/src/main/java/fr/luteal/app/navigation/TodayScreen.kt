package fr.luteal.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.luteal.app.LutealUiState
import fr.luteal.app.R
import fr.luteal.core.common.FrenchDateFormatter
import fr.luteal.core.designsystem.component.AdaptiveActionGroup
import fr.luteal.core.designsystem.component.CycleDayRing
import fr.luteal.core.designsystem.component.LutealCard
import fr.luteal.core.designsystem.component.LutealCardEmphasis
import fr.luteal.core.designsystem.component.LutealPrimaryButton
import fr.luteal.core.designsystem.component.LutealSecondaryButton
import fr.luteal.core.designsystem.component.ObservationPill
import fr.luteal.core.designsystem.component.ObservationTone
import fr.luteal.core.designsystem.component.StatusPill
import fr.luteal.core.designsystem.component.StatusTone
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.model.BleedingIntensity
import fr.luteal.core.model.CycleEstimateCalculator
import fr.luteal.core.model.CycleEstimateResult
import fr.luteal.core.model.CycleFacts
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

        LutealPrimaryButton(
            text = stringResource(R.string.action_start_period_short),
            onClick = onStartPeriod,
            icon = Icons.Rounded.WaterDrop,
            modifier = Modifier.fillMaxWidth()
        )

        TodayObservationCard(state = state, onEditToday = onEditToday)
        EstimateSection(state = state, onBackfillCycle = onBackfillCycle)

        if (state.cycles.size >= 2) {
            CycleStatsSection(state)
        }

        CycleFactCard(today = state.today)

        Spacer(Modifier.height(LutealSpacing.lg))
    }
}

@Composable
private fun CycleFactCard(state: LutealUiState) {
    val cycle = state.currentCycle
    LutealCard(
        modifier = Modifier.fillMaxWidth(),
        emphasis = LutealCardEmphasis.HERO
    ) {
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
            // The ring plus a column of facts does not survive side by side
            // once the user scales text up, so the same content stacks.
            val stackHero = LocalDensity.current.fontScale > 1.3f
            val ring: @Composable () -> Unit = {
                CycleDayRing(
                    dayOfCycle = day,
                    accessibleLabel = stringResource(R.string.cycle_day, day)
                )
            }
            val facts: @Composable ColumnScope.() -> Unit = {
                Text(
                    text = stringResource(R.string.cycle_day_caption),
                    style = MaterialTheme.typography.titleMedium
                )
                StatusPill(
                    text = stringResource(R.string.recorded_label),
                    tone = StatusTone.RECORDED
                )
                Text(
                    text = stringResource(
                        R.string.cycle_day_recorded_support,
                        FrenchDateFormatter.formatShortDate(cycle.startDate)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.md)) {
                if (stackHero) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)
                    ) {
                        ring()
                        facts()
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(LutealSpacing.lg),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ring()
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)
                        ) { facts() }
                    }
                }
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

        when (val result = state.estimateResult) {
            is CycleEstimateResult.NeedsMoreHistory -> {
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
            }

            // History exists; the intervals are simply outside what this
            // calculator models. Saying "waiting for history" here would be
            // untrue for anyone with long or irregular cycles.
            is CycleEstimateResult.IntervalsOutOfRange -> {
                Text(
                    text = stringResource(R.string.estimate_out_of_range_short),
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(LutealSpacing.xxs))
                Text(
                    text = stringResource(R.string.estimate_out_of_range_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            is CycleEstimateResult.Available -> {
                val estimate = result.estimate
                val daysUntil =
                    ChronoUnit.DAYS.between(state.today, estimate.earliestDate).toInt()
                val daysPastWindow =
                    ChronoUnit.DAYS.between(estimate.latestDate, state.today).toInt()
                val windowDays =
                    ChronoUnit.DAYS.between(estimate.earliestDate, estimate.latestDate)
                        .toInt() + 1

                Text(
                    text = stringResource(
                        R.string.estimate_range,
                        FrenchDateFormatter.formatShortDate(estimate.earliestDate),
                        FrenchDateFormatter.formatShortDate(estimate.latestDate)
                    ),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(LutealSpacing.xxs))
                Text(
                    text = when {
                        daysUntil > 0 ->
                            pluralStringResource(
                                R.plurals.estimate_days_remaining,
                                daysUntil,
                                daysUntil
                            )
                        // Past the whole window: the estimate did not hold, and
                        // saying "in progress" indefinitely would be misleading.
                        daysPastWindow > 0 ->
                            pluralStringResource(
                                R.plurals.estimate_past_window,
                                daysPastWindow,
                                daysPastWindow
                            )
                        else -> stringResource(R.string.estimate_in_progress)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Only worth drawing while the window is still ahead. Once it
                // has opened there is no wait left to scale the band against.
                if (daysUntil > 0) {
                    Spacer(Modifier.height(LutealSpacing.md))
                    EstimateWindowTrack(
                        leadDays = daysUntil,
                        windowDays = windowDays,
                        latestLabel = FrenchDateFormatter.formatShortDate(estimate.latestDate)
                    )
                }

                Spacer(Modifier.height(LutealSpacing.md))
                Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xxs)) {
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
    }
}

/**
 * The wait ahead, drawn to scale, with the estimated window as a band on it.
 *
 * The band's width is the uncertainty: a narrow one means the recorded history
 * agrees with itself, a wide one means it does not. Reporting the same range
 * as a sentence hid that difference entirely.
 */
@Composable
private fun EstimateWindowTrack(
    leadDays: Int,
    windowDays: Int,
    latestLabel: String
) {
    val scheme = MaterialTheme.colorScheme
    val description = stringResource(R.string.estimate_window_a11y, windowDays)
    Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .semantics { contentDescription = description }
        ) {
            // outlineVariant against tertiary, not surfaceVariant against
            // tertiaryContainer: those two are within a few percent of each
            // other in light mode, so the band that carries the whole point of
            // the component was almost invisible against its own track.
            Box(
                modifier = Modifier
                    .weight(leadDays.toFloat())
                    .fillMaxHeight()
                    .background(scheme.outlineVariant)
            )
            Box(
                modifier = Modifier
                    .weight(windowDays.toFloat())
                    .fillMaxHeight()
                    .background(scheme.tertiary)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.today_title),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = latestLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CycleStatsSection(state: LutealUiState) {
    // Same plausibility filter the estimator applies, so the average shown
    // here cannot contradict the estimate rendered directly above it.
    val completedLengths = state.cycles
        .filterNot { it.isCurrent }
        .map { it.lengthInDays }
        .filter { it in CycleEstimateCalculator.plausibleCycleDays }
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
        // An average alone hides how much the cycles actually vary.
        if (completedLengths.size > 1) {
            Text(
                text = stringResource(
                    R.string.cycle_stats_range,
                    completedLengths.min(),
                    completedLengths.max()
                ),
                style = MaterialTheme.typography.bodySmall,
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

/**
 * A sourced population fact, one per day.
 *
 * Chosen from the date rather than at random, so it is stable for the day and
 * identical across relaunches. It is never chosen from the user's own records:
 * a fact surfaced because of where someone is in their cycle would be an
 * inference about their phase.
 *
 * The source is named in the card and the link is a separate, explicit action.
 * Opening it leaves the app and contacts an external site, which is not
 * something a privacy-first app should do on an incidental tap.
 */
@Composable
private fun CycleFactCard(today: java.time.LocalDate) {
    val fact = remember(today) { CycleFacts.forDate(today) }
    val text = factText(fact.id) ?: return
    val uriHandler = LocalUriHandler.current

    LutealCard(
        modifier = Modifier.fillMaxWidth(),
        emphasis = LutealCardEmphasis.QUIET
    ) {
        Text(
            text = stringResource(R.string.fact_card_title),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(LutealSpacing.xs))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(LutealSpacing.sm))
        Text(
            text = stringResource(R.string.fact_source_label, fact.source),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(
            onClick = { uriHandler.openUri(fact.url) },
            contentPadding = PaddingValues(
                horizontal = 0.dp,
                vertical = LutealSpacing.xs
            )
        ) {
            Text(text = stringResource(R.string.fact_view_source))
            Spacer(Modifier.width(LutealSpacing.xxs))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Null for an id with no copy, so a fact cannot render as a blank card.
 *
 * `CycleFactsTest` asserts every id in the catalog has a matching string in
 * both resource files, so this branch should be unreachable in practice.
 */
@Composable
private fun factText(id: String): String? = when (id) {
    "no_single_normal" -> stringResource(R.string.fact_no_single_normal)
    "most_cycles_in_range" -> stringResource(R.string.fact_most_cycles_in_range)
    "variation_is_common" -> stringResource(R.string.fact_variation_is_common)
    "long_cycles_common" -> stringResource(R.string.fact_long_cycles_common)
    "short_cycles_rare" -> stringResource(R.string.fact_short_cycles_rare)
    "ovulation_not_day_14" -> stringResource(R.string.fact_ovulation_not_day_14)
    "luteal_varies" -> stringResource(R.string.fact_luteal_varies)
    "cycles_shorten_with_age" -> stringResource(R.string.fact_cycles_shorten_with_age)
    "quarter_very_regular" -> stringResource(R.string.fact_quarter_very_regular)
    "mean_length" -> stringResource(R.string.fact_mean_length)
    "variation_by_age" -> stringResource(R.string.fact_variation_by_age)
    "variation_after_50" -> stringResource(R.string.fact_variation_after_50)
    "median_iqr" -> stringResource(R.string.fact_median_iqr)
    "variation_late_forties" -> stringResource(R.string.fact_variation_late_forties)
    "same_person_varies" -> stringResource(R.string.fact_same_person_varies)
    "large_cohort_mean" -> stringResource(R.string.fact_large_cohort_mean)
    "period_duration" -> stringResource(R.string.fact_period_duration)
    "blood_volume" -> stringResource(R.string.fact_blood_volume)
    "menarche_age" -> stringResource(R.string.fact_menarche_age)
    "menopause_age" -> stringResource(R.string.fact_menopause_age)
    "ovulation_counts_backwards" -> stringResource(R.string.fact_ovulation_counts_backwards)
    "irregular_definition" -> stringResource(R.string.fact_irregular_definition)
    "health_not_hygiene" -> stringResource(R.string.fact_health_not_hygiene)
    "not_shameful" -> stringResource(R.string.fact_not_shameful)
    else -> null
}

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
