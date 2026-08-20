package fr.luteal.app.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.luteal.app.EntrySaveState
import fr.luteal.app.LutealViewModel
import fr.luteal.app.R
import fr.luteal.core.model.ObservationCatalog
import fr.luteal.core.model.TemperatureUnit
import java.time.LocalDate

@Composable
fun LutealMainScaffold(
    widgetDestination: String? = null,
    onWidgetDestinationConsumed: () -> Unit = {},
    viewModel: LutealViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedDestination by rememberSaveable { mutableStateOf(LutealDestination.TODAY) }
    var editorRequest by remember { mutableStateOf<EditorRequest?>(null) }
    var showBackfillDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val saveError = stringResource(R.string.save_error)
    val saveSuccess = stringResource(R.string.save_success_local)
    val useCompactNavigationLabels = LocalDensity.current.fontScale >= 1.5f

    // Widget requests survive a cold start and wait until onboarding is
    // complete. They navigate only; health data is still edited and confirmed
    // inside the app.
    LaunchedEffect(widgetDestination, uiState.preferences.hasCompletedOnboarding) {
        val destination = widgetDestination ?: return@LaunchedEffect
        if (!uiState.preferences.hasCompletedOnboarding) return@LaunchedEffect
        when (destination) {
            fr.luteal.app.MainActivity.WIDGET_DESTINATION_TODAY -> {
                selectedDestination = LutealDestination.TODAY
            }
            fr.luteal.app.MainActivity.WIDGET_DESTINATION_TODAY_EDITOR -> {
                selectedDestination = LutealDestination.TODAY
                viewModel.clearEntrySaveState()
                editorRequest = EditorRequest(uiState.today, startPeriodIntent = false)
            }
            fr.luteal.app.MainActivity.WIDGET_DESTINATION_DUO -> {
                selectedDestination = LutealDestination.DUO
            }
        }
        onWidgetDestinationConsumed()
    }

    LaunchedEffect(uiState.operationFailed) {
        if (uiState.operationFailed) {
            snackbarHostState.showSnackbar(saveError)
            viewModel.clearOperationError()
        }
    }

    LaunchedEffect(uiState.entrySaveState) {
        if (uiState.entrySaveState == EntrySaveState.SUCCESS) {
            editorRequest = null
            snackbarHostState.showSnackbar(saveSuccess)
            viewModel.clearEntrySaveState()
        }
    }

    if (!uiState.preferences.hasCompletedOnboarding) {
        OnboardingScreen(
            onComplete = { role, focusMap, ageBandId ->
                viewModel.completeOnboarding(role, focusMap, ageBandId)
            }
        )
    } else {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        LutealDestination.entries.forEach { destination ->
                            val visibleLabelRes = if (
                                useCompactNavigationLabels && destination == LutealDestination.TODAY
                            ) {
                                R.string.nav_today_compact
                            } else {
                                destination.labelRes
                            }
                            NavigationBarItem(
                                selected = destination == selectedDestination,
                                onClick = { selectedDestination = destination },
                                icon = {
                                    Icon(
                                        imageVector = destination.icon,
                                        contentDescription = stringResource(destination.labelRes)
                                    )
                                },
                                // A second line does not fit the navigation
                                // bar's height, so wrapping pushed "Reglages"
                                // out of the bar entirely at large font
                                // scales. The icon and the unchanged
                                // contentDescription still carry the meaning.
                                label = {
                                    Text(
                                        text = stringResource(visibleLabelRes),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            ) { contentPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(modifier = Modifier.fillMaxSize().widthIn(max = 760.dp)) {
                        when (selectedDestination) {
                            LutealDestination.TODAY -> TodayScreen(
                                state = uiState,
                                onStartPeriod = {
                                    viewModel.clearEntrySaveState()
                                    editorRequest = EditorRequest(uiState.today, startPeriodIntent = true)
                                },
                                onEditToday = {
                                    viewModel.clearEntrySaveState()
                                    editorRequest = EditorRequest(uiState.today, startPeriodIntent = false)
                                },
                                onBackfillCycle = { showBackfillDialog = true }
                            )
                            LutealDestination.JOURNAL -> JournalScreen(
                                state = uiState,
                                onSelectDate = {
                                    viewModel.clearEntrySaveState()
                                    editorRequest = EditorRequest(it, startPeriodIntent = false)
                                },
                                onEditCycle = { cycleId, newStartDate ->
                                    viewModel.editCycleStartDate(cycleId, newStartDate)
                                },
                                onDeleteCycle = { cycleId ->
                                    viewModel.deleteCycle(cycleId)
                                },
                                onToggleCycleExclusion = { cycleId, isExcluded, reason ->
                                    viewModel.toggleCycleExclusion(cycleId, isExcluded, reason)
                                },
                                onExportClinicalReport = viewModel::exportClinicalReport,
                                onStartPeriod = {
                                    viewModel.clearEntrySaveState()
                                    editorRequest = EditorRequest(uiState.today, startPeriodIntent = true)
                                }
                            )
                            LutealDestination.DUO -> DuoScreen(
                                onOpenSettings = {
                                    selectedDestination = LutealDestination.SETTINGS
                                }
                            )
                            LutealDestination.SETTINGS -> SettingsScreen()
                        }
                    }
                }
            }
        }
    }

    if (showBackfillDialog) {
        BackfillCycleDialog(
            existingCycleStarts = uiState.cycles.map { it.startDate }.toSet(),
            onConfirm = { startDate ->
                viewModel.addBackfilledCycle(startDate)
                showBackfillDialog = false
            },
            onDismiss = { showBackfillDialog = false }
        )
    }

    editorRequest?.let { req ->
        DailyEntrySheet(
            date = req.date,
            existingEntry = uiState.entries.firstOrNull { it.date == req.date },
            existingBiomarker = uiState.biomarkers.firstOrNull { it.date == req.date },
            temperatureUnit = TemperatureUnit.entries.firstOrNull {
                it.name == uiState.preferences.temperatureUnit
            } ?: TemperatureUnit.CELSIUS,
            currentCycle = uiState.currentCycle,
            offeredSymptomIds = ObservationCatalog.symptomIdsFor(
                uiState.preferences.declaredContexts
            ),
            startPeriodIntent = req.startPeriodIntent,
            isSaving = uiState.entrySaveState == EntrySaveState.SAVING,
            saveFailed = uiState.entrySaveState == EntrySaveState.FAILED,
            onDismiss = {
                viewModel.clearEntrySaveState()
                editorRequest = null
            },
            onSave = viewModel::saveEntry
        )
    }
}

private data class EditorRequest(
    val date: LocalDate,
    val startPeriodIntent: Boolean
)

private enum class LutealDestination(
    @StringRes val labelRes: Int,
    val icon: ImageVector
) {
    TODAY(R.string.nav_today, Icons.Rounded.Today),
    JOURNAL(R.string.nav_journal, Icons.Rounded.CalendarToday),
    DUO(R.string.nav_duo, Icons.Rounded.FavoriteBorder),
    SETTINGS(R.string.nav_settings, Icons.Rounded.Settings)
}
