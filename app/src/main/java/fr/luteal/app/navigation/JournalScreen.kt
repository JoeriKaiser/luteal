package fr.luteal.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Today
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
import androidx.compose.ui.text.style.TextOverflow
import fr.luteal.app.LutealUiState
import fr.luteal.app.R
import fr.luteal.core.common.FrenchDateFormatter
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
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(LutealSpacing.xxs)
            ) {
                Text(
                    text = if (isToday) {
                        stringResource(R.string.journal_today)
                    } else {
                        FrenchDateFormatter.formatFullDate(entry.date)
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = journalEntrySummary(entry),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun journalEntrySummary(entry: DailyEntry): String {
    val parts = buildList {
        entry.bleedingIntensity?.let { add(stringResource(R.string.journal_summary_bleeding)) }
        entry.painLevel?.let { add(stringResource(R.string.journal_summary_pain, it)) }
        entry.moodLevel?.let { add(stringResource(R.string.journal_summary_mood, it)) }
        entry.energyLevel?.let { add(stringResource(R.string.journal_summary_energy, it)) }
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
