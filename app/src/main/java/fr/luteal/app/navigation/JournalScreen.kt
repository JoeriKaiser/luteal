package fr.luteal.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.luteal.app.LutealUiState
import fr.luteal.app.R
import fr.luteal.core.common.FrenchDateFormatter
import fr.luteal.core.model.BleedingIntensity
import fr.luteal.core.designsystem.component.AdaptiveActionGroup
import fr.luteal.core.designsystem.component.LutealEmptyState
import fr.luteal.core.designsystem.component.LutealPrimaryButton
import fr.luteal.core.designsystem.component.LutealSecondaryButton
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.model.DailyEntry
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@Composable
fun JournalScreen(
    state: LutealUiState,
    onSelectDate: (LocalDate) -> Unit
) {
    val recordedEntries = state.entries
        .filter(DailyEntry::hasObservations)
        .sortedByDescending(DailyEntry::date)
    var showDatePicker by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = LutealSpacing.md,
            vertical = LutealSpacing.lg
        ),
        verticalArrangement = Arrangement.spacedBy(LutealSpacing.md)
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.journal_title),
                subtitle = stringResource(R.string.journal_subtitle)
            )
        }
        if (recordedEntries.isEmpty()) {
            item {
                LutealEmptyState(
                    title = stringResource(R.string.journal_empty_title),
                    body = stringResource(R.string.journal_empty_body),
                    actionText = stringResource(R.string.action_log_today),
                    onAction = { onSelectDate(state.today) },
                    icon = Icons.Rounded.CalendarMonth,
                    modifier = Modifier.padding(
                        horizontal = LutealSpacing.lg,
                        vertical = LutealSpacing.xl
                    )
                )
            }
            item {
                LutealSecondaryButton(
                    text = stringResource(R.string.journal_choose_date),
                    onClick = { showDatePicker = true },
                    icon = Icons.Rounded.CalendarMonth,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            item {
                AdaptiveActionGroup(
                    primary = { actionModifier ->
                        LutealPrimaryButton(
                            text = if (state.todayEntry?.hasObservations == true) {
                                stringResource(R.string.action_edit_today)
                            } else {
                                stringResource(R.string.action_log_today)
                            },
                            onClick = { onSelectDate(state.today) },
                            icon = if (state.todayEntry?.hasObservations == true) {
                                Icons.Rounded.Edit
                            } else {
                                Icons.Rounded.Today
                            },
                            modifier = actionModifier
                        )
                    },
                    secondary = { actionModifier ->
                        LutealSecondaryButton(
                            text = stringResource(R.string.journal_choose_date),
                            onClick = { showDatePicker = true },
                            icon = Icons.Rounded.CalendarMonth,
                            modifier = actionModifier
                        )
                    }
                )
            }
            recordedEntries
                .groupBy { YearMonth.from(it.date) }
                .forEach { (_, monthEntries) ->
                    item(key = "month-${monthEntries.first().date}") {
                        Text(
                            text = FrenchDateFormatter.formatMonthYear(monthEntries.first().date),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = LutealSpacing.xs)
                        )
                    }
                    items(monthEntries, key = { it.date.toString() }) { entry ->
                        JournalEntryRow(
                            entry = entry,
                            isToday = entry.date == state.today,
                            onClick = { onSelectDate(entry.date) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
        }
    }

    if (showDatePicker) {
        JournalDatePickerDialog(
            today = state.today,
            onSelect = {
                showDatePicker = false
                onSelectDate(it)
            },
            onDismiss = { showDatePicker = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JournalEntryRow(
    entry: DailyEntry,
    isToday: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = androidx.compose.ui.graphics.Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = LutealSpacing.xxl)
                .padding(vertical = LutealSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(LutealSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BleedingMark(intensity = entry.bleedingIntensity)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)
            ) {
                Text(
                    text = if (isToday) {
                        stringResource(R.string.journal_today)
                    } else {
                        FrenchDateFormatter.formatFullDate(entry.date)
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                // Levels are drawn rather than spelled out, so a heavy day and
                // a mild one no longer render as the same block of grey text.
                val levels = listOfNotNull(
                    entry.painLevel?.let { stringResource(R.string.level_label_pain) to it },
                    entry.moodLevel?.let { stringResource(R.string.level_label_mood) to it },
                    entry.energyLevel?.let { stringResource(R.string.level_label_energy) to it }
                )
                if (levels.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(LutealSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)
                    ) {
                        levels.forEach { (label, level) ->
                            MiniLevel(label = label, level = level)
                        }
                    }
                }
                val trailing = journalEntryTrailingSummary(entry)
                if (trailing.isNotEmpty()) {
                    Text(
                        text = trailing,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Recorded flow for the day, as a drop that fills with intensity.
 *
 * The intensity name travels with it in the accessible label, so the fill is
 * a second encoding of something already stated rather than the only one.
 */
@Composable
private fun BleedingMark(intensity: BleedingIntensity?) {
    val scheme = MaterialTheme.colorScheme
    val recorded = intensity != null && intensity != BleedingIntensity.NONE
    val description = if (intensity == null) {
        stringResource(R.string.journal_no_bleeding_recorded)
    } else {
        stringResource(R.string.today_bleeding_label, bleedingLabel(intensity))
    }
    val dropSize = when (intensity) {
        BleedingIntensity.HEAVY -> 26.dp
        BleedingIntensity.MEDIUM -> 22.dp
        BleedingIntensity.LIGHT -> 19.dp
        BleedingIntensity.SPOTTING -> 16.dp
        else -> 14.dp
    }

    Box(
        modifier = Modifier
            .size(40.dp)
            .background(
                if (recorded) scheme.primaryContainer else scheme.surfaceVariant,
                CircleShape
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (recorded) Icons.Rounded.WaterDrop else Icons.Rounded.Remove,
            contentDescription = null,
            tint = if (recorded) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
            modifier = Modifier.size(dropSize)
        )
    }
}

/** A one-to-five level as filled segments, with the label kept visible. */
@Composable
private fun MiniLevel(label: String, level: Int) {
    val scheme = MaterialTheme.colorScheme
    val description = stringResource(R.string.level_a11y, label, level)
    Row(
        horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics { contentDescription = description }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            (1..5).forEach { step ->
                Box(
                    modifier = Modifier
                        .size(width = 7.dp, height = 7.dp)
                        .background(
                            if (step <= level) scheme.primary else scheme.outlineVariant,
                            RoundedCornerShape(2.dp)
                        )
                )
            }
        }
    }
}

@Composable
private fun journalEntryTrailingSummary(entry: DailyEntry): String {
    val parts = buildList {
        if (entry.symptomIds.isNotEmpty()) {
            add(
                pluralStringResource(
                    R.plurals.journal_summary_other_count,
                    entry.symptomIds.size,
                    entry.symptomIds.size
                )
            )
        }
        if (entry.notes.isNotBlank()) add(stringResource(R.string.journal_summary_private_note))
    }
    return parts.joinToString(separator = " · ")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JournalDatePickerDialog(
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val zone = ZoneId.systemDefault()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = today.atStartOfDay(zone).toInstant().toEpochMilli(),
        selectableDates = remember(today) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val date = Instant.ofEpochMilli(utcTimeMillis).atZone(zone).toLocalDate()
                    return !date.isAfter(today)
                }

                override fun isSelectableYear(year: Int): Boolean = year <= today.year
            }
        }
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onSelect(Instant.ofEpochMilli(millis).atZone(zone).toLocalDate())
                    }
                },
                enabled = state.selectedDateMillis != null
            ) {
                Text(stringResource(R.string.journal_open_date))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    ) {
        DatePicker(state = state)
    }
}
