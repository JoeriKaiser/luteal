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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.luteal.app.EntrySaveState
import fr.luteal.app.LutealViewModel
import fr.luteal.app.R
import java.time.LocalDate

@Composable
fun LutealMainScaffold(
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
            onComplete = { role, focusMap ->
                viewModel.completeOnboarding(role, focusMap)
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
                                label = { Text(stringResource(visibleLabelRes), maxLines = 2) },
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
                                }
                            )
                            LutealDestination.DUO -> DuoScreen(
                                state = uiState,
                                onSharingChange = viewModel::updateDuoSharing
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
            existingEntry = if (req.date == uiState.today) uiState.todayEntry else null,
            currentCycle = uiState.currentCycle,
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
