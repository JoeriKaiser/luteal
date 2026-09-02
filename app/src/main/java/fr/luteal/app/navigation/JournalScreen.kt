package fr.luteal.app.navigation

import android.content.Context
import android.net.Uri
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.IconButton
import androidx.compose.ui.platform.LocalContext
import fr.luteal.core.model.ClinicalReportConfig
import fr.luteal.core.model.ReportFormat
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import fr.luteal.core.model.CycleExclusionReason
import fr.luteal.core.model.LongitudinalCycleStatsCalculator
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.WaterDrop
import fr.luteal.core.model.Cycle
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.luteal.app.LutealUiState
import fr.luteal.app.R
import fr.luteal.core.common.LocalizedDateFormatter
import fr.luteal.core.designsystem.component.AdaptiveActionGroup
import fr.luteal.core.designsystem.component.CalendarLegendCard
import fr.luteal.core.designsystem.component.LutealCard
import fr.luteal.core.designsystem.component.LutealEmptyState
import fr.luteal.core.designsystem.component.LutealPrimaryButton
import fr.luteal.core.designsystem.component.LutealSecondaryButton
import fr.luteal.core.designsystem.component.MonthCalendarGrid
import fr.luteal.core.designsystem.component.ThermalShiftChart
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.model.BleedingIntensity
import fr.luteal.core.model.DailyEntry
import fr.luteal.core.model.TemperatureUnit
import fr.luteal.core.model.ThermalShiftCalculator
import fr.luteal.core.model.ThermalShiftResult
import fr.luteal.core.model.MonthCalendarProjectionCalculator
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields

enum class JournalViewMode {
    CALENDAR,
    TIMELINE,
    VARIABILITY,
    THERMAL
}

@Composable
fun JournalScreen(
    state: LutealUiState,
    onSelectDate: (LocalDate) -> Unit,
    onEditCycle: (cycleId: String, newStartDate: LocalDate) -> Unit = { _, _ -> },
    onDeleteCycle: (cycleId: String) -> Unit = {},
    onToggleCycleExclusion: (cycleId: String, isExcluded: Boolean, reason: CycleExclusionReason?) -> Unit = { _, _, _ -> },
    onExportClinicalReport: (Context, android.net.Uri, ClinicalReportConfig) -> Unit = { _, _, _ -> },
    onStartPeriod: () -> Unit = {},
    initialViewMode: JournalViewMode = JournalViewMode.CALENDAR
) {
    val context = LocalContext.current
    var viewMode by rememberSaveable { mutableStateOf(initialViewMode) }
    var currentMonth by rememberSaveable { mutableStateOf(YearMonth.from(state.today)) }
    var selectedDate by rememberSaveable { mutableStateOf(state.today) }
    val locale = LocalConfiguration.current.locales[0]
    var showDatePicker by remember { mutableStateOf(false) }
    var cycleToEdit by remember { mutableStateOf<Cycle?>(null) }
    var cycleToDelete by remember { mutableStateOf<Cycle?>(null) }
    var cycleToManageExclusion by remember { mutableStateOf<Cycle?>(null) }
    var showReportDialog by remember { mutableStateOf(false) }
    var pendingReportConfig by remember { mutableStateOf<ClinicalReportConfig?>(null) }
    val reportPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val config = pendingReportConfig
        if (uri != null && config != null) {
            onExportClinicalReport(context, uri, config)
        }
        pendingReportConfig = null
    }
    val reportHtmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/html")
    ) { uri ->
        val config = pendingReportConfig
        if (uri != null && config != null) {
            onExportClinicalReport(context, uri, config)
        }
        pendingReportConfig = null
    }

    if (showReportDialog) {
        ClinicalReportDialog(
            onDismiss = { showReportDialog = false },
            onExport = { config ->
                showReportDialog = false
                pendingReportConfig = config
                val ext = if (config.format == ReportFormat.HTML) "html" else "pdf"
                val name = "luteal_recapitulatif_medical_${LocalDate.now()}.$ext"
                if (config.format == ReportFormat.HTML) {
                    reportHtmlLauncher.launch(name)
                } else {
                    reportPdfLauncher.launch(name)
                }
            }
        )
    }

    val recordedEntries = remember(state.entries) {
        state.entries
            .filter(DailyEntry::hasObservations)
            .sortedByDescending(DailyEntry::date)
    }

    val entriesByDate = remember(state.entries) {
        state.entries.associateBy(DailyEntry::date)
    }

    val calendarProjection = remember(currentMonth, state.today, state.cycles, state.entries, state.estimateResult, locale) {
        MonthCalendarProjectionCalculator.project(
            targetMonth = currentMonth,
            today = state.today,
            cycles = state.cycles,
            entries = state.entries,
            estimateResult = state.estimateResult,
            firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek
        )
    }

    cycleToManageExclusion?.let { cycle ->
        CycleExclusionDialog(
            cycle = cycle,
            onDismiss = { cycleToManageExclusion = null },
            onConfirm = { isExcluded, reason ->
                onToggleCycleExclusion(cycle.id, isExcluded, reason)
                cycleToManageExclusion = null
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = LutealSpacing.md,
            vertical = LutealSpacing.lg
        ),
        verticalArrangement = Arrangement.spacedBy(LutealSpacing.md)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScreenHeader(
                    title = stringResource(R.string.journal_title),
                    subtitle = stringResource(R.string.journal_subtitle)
                )
                IconButton(onClick = { showReportDialog = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Description,
                        contentDescription = stringResource(R.string.report_dialog_title),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item {
            ScrollableTabRow(
                selectedTabIndex = viewMode.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 0.dp,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
            ) {
                Tab(
                    selected = viewMode == JournalViewMode.CALENDAR,
                    onClick = { viewMode = JournalViewMode.CALENDAR },
                    text = {
                        Text(
                            text = stringResource(R.string.calendar_view_calendar),
                            style = MaterialTheme.typography.titleSmall
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.CalendarMonth,
                            contentDescription = null
                        )
                    }
                )
                Tab(
                    selected = viewMode == JournalViewMode.TIMELINE,
                    onClick = { viewMode = JournalViewMode.TIMELINE },
                    text = {
                        Text(
                            text = stringResource(R.string.calendar_view_list),
                            style = MaterialTheme.typography.titleSmall
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.FormatListBulleted,
                            contentDescription = null
                        )
                    }
                )
                Tab(
                    selected = viewMode == JournalViewMode.VARIABILITY,
                    onClick = { viewMode = JournalViewMode.VARIABILITY },
                    text = {
                        Text(
                            text = stringResource(R.string.journal_view_variability),
                            style = MaterialTheme.typography.titleSmall
                         )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ShowChart,
                            contentDescription = null
                        )
                    }
                )
                Tab(
                    selected = viewMode == JournalViewMode.THERMAL,
                    onClick = { viewMode = JournalViewMode.THERMAL },
                    text = {
                        Text(
                            text = stringResource(R.string.journal_view_thermal),
                            style = MaterialTheme.typography.titleSmall
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.DeviceThermostat,
                            contentDescription = null
                        )
                    }
                )
            }
        }

        if (viewMode == JournalViewMode.CALENDAR) {
            item {
                MonthNavigationBar(
                    currentMonth = currentMonth,
                    isCurrentMonthToday = currentMonth == YearMonth.from(state.today),
                    onPreviousMonth = { currentMonth = currentMonth.minusMonths(1) },
                    onNextMonth = { currentMonth = currentMonth.plusMonths(1) },
                    onJumpToToday = {
                        currentMonth = YearMonth.from(state.today)
                        selectedDate = state.today
                    }
                )
            }

            item {
                MonthCalendarGrid(
                    projection = calendarProjection,
                    selectedDate = selectedDate,
                    onSelectDate = { selectedDate = it }
                )
            }

            item {
                CalendarLegendCard()
            }

            item {
                SelectedDayInspectionCard(
                    date = selectedDate,
                    entry = entriesByDate[selectedDate],
                    isToday = selectedDate == state.today,
                    cycleStart = state.cycles.firstOrNull { it.startDate == selectedDate },
                    onEditCycle = { cycleToEdit = it },
                    onDeleteCycle = { cycleToDelete = it },
                    onEditOrAdd = { onSelectDate(selectedDate) }
                )
            }
        } else if (viewMode == JournalViewMode.TIMELINE) {
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
                                text = LocalizedDateFormatter.formatMonthYear(monthEntries.first().date, locale),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = LutealSpacing.xs)
                            )
                        }
                        items(monthEntries, key = { it.date.toString() }) { entry ->
                            val cycleAtDate = state.cycles.firstOrNull { it.startDate == entry.date }
                            JournalEntryRow(
                                entry = entry,
                                isToday = entry.date == state.today,
                                cycleStart = cycleAtDate,
                                onEditCycle = { cycleToEdit = it },
                                onDeleteCycle = { cycleToDelete = it },
                                onClick = { onSelectDate(entry.date) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
            }
        } else if (viewMode == JournalViewMode.VARIABILITY) {
            item {
                val stats = remember(state.cycles) {
                    LongitudinalCycleStatsCalculator.calculate(state.cycles)
                }
                CycleVariabilityVisualizer(
                    stats = stats,
                    onManageExclusion = { cycleId ->
                        cycleToManageExclusion = state.cycles.firstOrNull { it.id == cycleId }
                    },
                    onStartPeriod = onStartPeriod
                )
            }
        } else {
            item {
                ThermalHistoryCard(state = state)
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

    cycleToEdit?.let { cycle ->
        EditCycleDialog(
            cycle = cycle,
            existingCycles = state.cycles,
            today = state.today,
            onDismiss = { cycleToEdit = null },
            onConfirm = { newStartDate ->
                onEditCycle(cycle.id, newStartDate)
                cycleToEdit = null
            }
        )
    }

    cycleToDelete?.let { cycle ->
        DeleteCycleConfirmDialog(
            onDismiss = { cycleToDelete = null },
            onConfirm = {
                onDeleteCycle(cycle.id)
                cycleToDelete = null
            }
        )
    }
}

@Composable
private fun MonthNavigationBar(
    currentMonth: YearMonth,
    isCurrentMonthToday: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onJumpToToday: () -> Unit,
    modifier: Modifier = Modifier
) {
    val locale = LocalConfiguration.current.locales[0]
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs)
        ) {
            IconButton(
                onClick = onPreviousMonth,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.calendar_previous_month)
                )
            }

            Text(
                text = LocalizedDateFormatter.formatMonthYear(currentMonth.atDay(1), locale),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            IconButton(
                onClick = onNextMonth,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.calendar_next_month)
                )
            }
        }

        if (!isCurrentMonthToday) {
            LutealSecondaryButton(
                text = stringResource(R.string.calendar_today_button),
                onClick = onJumpToToday,
                icon = Icons.Rounded.Today
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedDayInspectionCard(
    date: LocalDate,
    entry: DailyEntry?,
    isToday: Boolean,
    cycleStart: Cycle?,
    onEditCycle: (Cycle) -> Unit,
    onDeleteCycle: (Cycle) -> Unit,
    onEditOrAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasObservations = entry?.hasObservations == true
    val locale = LocalConfiguration.current.locales[0]

    LutealCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LutealSpacing.md),
            verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xxs)) {
                    Text(
                        text = if (isToday) {
                            stringResource(R.string.journal_today)
                        } else {
                            LocalizedDateFormatter.formatFullDate(date, locale)
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (isToday) {
                        Text(
                            text = LocalizedDateFormatter.formatFullDate(date, locale),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (entry?.bleedingIntensity != null && entry.bleedingIntensity != BleedingIntensity.NONE) {
                    BleedingMark(intensity = entry.bleedingIntensity)
                }
            }

            if (cycleStart != null) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = LutealSpacing.sm, vertical = LutealSpacing.xs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.cycle_start_badge),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
                            IconButton(
                                onClick = { onEditCycle(cycleStart) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = stringResource(R.string.cycle_action_edit),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = { onDeleteCycle(cycleStart) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = stringResource(R.string.cycle_action_delete),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            if (hasObservations) {
                val levels = listOfNotNull(
                    entry?.painLevel?.let { stringResource(R.string.level_label_pain) to it },
                    entry?.moodLevel?.let { stringResource(R.string.level_label_mood) to it },
                    entry?.energyLevel?.let { stringResource(R.string.level_label_energy) to it }
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

                val trailing = entry?.let { journalEntryTrailingSummary(it) }.orEmpty()
                if (trailing.isNotEmpty()) {
                    Text(
                        text = trailing,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!entry?.notes.isNullOrBlank()) {
                    Text(
                        text = entry?.notes.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.calendar_day_empty_inspection),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LutealPrimaryButton(
                text = when {
                    hasObservations -> stringResource(R.string.action_edit_today)
                    isToday -> stringResource(R.string.action_log_today)
                    else -> stringResource(R.string.action_add_observation)
                },
                onClick = onEditOrAdd,
                icon = if (hasObservations) Icons.Rounded.Edit else Icons.Rounded.Add,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JournalEntryRow(
    entry: DailyEntry,
    isToday: Boolean,
    cycleStart: Cycle?,
    onEditCycle: (Cycle) -> Unit,
    onDeleteCycle: (Cycle) -> Unit,
    onClick: () -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LutealSpacing.xxs)
    ) {
        if (cycleStart != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = LutealSpacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = stringResource(R.string.cycle_start_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = LutealSpacing.xs, vertical = 2.dp)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
                    IconButton(
                        onClick = { onEditCycle(cycleStart) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = stringResource(R.string.cycle_action_edit),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = { onDeleteCycle(cycleStart) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.cycle_action_delete),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

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
                            LocalizedDateFormatter.formatFullDate(entry.date, locale)
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

@Composable
private fun ThermalHistoryCard(state: LutealUiState) {
    val cycle = state.currentCycle
    val unit = TemperatureUnit.entries.firstOrNull {
        it.name == state.preferences.temperatureUnit
    } ?: TemperatureUnit.CELSIUS
    if (cycle == null) {
        LutealCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
                Text(text = stringResource(R.string.thermal_empty_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.thermal_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }
    val observations = state.biomarkers.filter { it.date >= cycle.startDate }
    val shift = remember(cycle.startDate, observations) {
        ThermalShiftCalculator.evaluateCycle(cycle.startDate, observations)
    }
    LutealCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
            Text(
                text = stringResource(R.string.thermal_chart_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.thermal_chart_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (observations.none { it.bbt != null }) {
                Text(
                    text = stringResource(R.string.thermal_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                ThermalShiftChart(
                    cycleStart = cycle.startDate,
                    observations = observations,
                    shift = shift,
                    unit = unit,
                    contentDescription = stringResource(
                        R.string.thermal_chart_summary,
                        observations.count { it.bbt != null },
                        observations.count { it.bbt?.isDisturbed == true },
                        if (shift is ThermalShiftResult.Confirmed) {
                            stringResource(R.string.thermal_shift_confirmed)
                        } else {
                            stringResource(R.string.thermal_chart_no_shift)
                        }
                    )
                )
                if (shift is ThermalShiftResult.Confirmed) {
                    Text(
                        text = stringResource(R.string.thermal_shift_confirmed),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.coverline_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JournalDatePickerDialog(
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val zone = java.time.ZoneOffset.UTC
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
