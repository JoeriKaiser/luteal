package fr.luteal.app

import android.content.Context
import android.net.Uri
import fr.luteal.core.data.ClinicalReportAggregator
import fr.luteal.core.data.report.HtmlReportBuilder
import fr.luteal.core.data.report.PdfReportBuilder
import fr.luteal.core.model.ClinicalReportConfig
import fr.luteal.core.model.ReportFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.luteal.core.data.datastore.UserPreferences
import fr.luteal.core.data.repository.BiomarkerRepository
import fr.luteal.core.data.repository.CycleRepository
import fr.luteal.core.data.repository.DailyEntryRepository
import fr.luteal.app.notification.NotificationScheduler
import fr.luteal.core.data.repository.UserRepository
import fr.luteal.core.model.AgeBand
import fr.luteal.core.model.Cycle
import fr.luteal.core.model.CycleEstimate
import fr.luteal.core.model.CycleEstimateCalculator
import fr.luteal.core.model.CycleEstimateResult
import fr.luteal.core.model.CurrentCyclePhase
import fr.luteal.core.model.CurrentCyclePhaseCalculator
import fr.luteal.core.model.BiomarkerObservation
import fr.luteal.core.model.DailyEntry
import fr.luteal.core.model.DuoSharingField
import fr.luteal.core.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class LutealViewModel @Inject constructor(
    private val cycleRepository: CycleRepository,
    private val dailyEntryRepository: DailyEntryRepository,
    private val biomarkerRepository: BiomarkerRepository,
    private val userRepository: UserRepository,
    private val clinicalReportAggregator: ClinicalReportAggregator,
    private val notificationScheduler: NotificationScheduler,
    private val clock: Clock
) : ViewModel() {
    private val operationState = MutableStateFlow(OperationState())

    /** Local calendar date at construction; seeds the StateFlow before the first emission. */
    private val initialToday: LocalDate = LocalDate.ofInstant(clock.instant(), ZoneId.systemDefault())

    /**
     * Emits the local "today" now and after every date rollover, so a process
     * kept alive overnight never serves yesterday as today (entries would be
     * silently recorded under the wrong date).
     */
    private val todayFlow: Flow<LocalDate> = fr.luteal.app.todayFlow(clock)

    val uiState: StateFlow<LutealUiState> = combine(
        combine(
            cycleRepository.getCycles(),
            cycleRepository.getCurrentCycle(),
            dailyEntryRepository.observeEntries(),
            biomarkerRepository.observeObservations(),
            userRepository.getUserPreferences()
        ) { cycles, currentCycle, entries, biomarkers, preferences ->
            RecordSnapshot(cycles, currentCycle, entries, biomarkers, preferences)
        },
        operationState,
        todayFlow
    ) { records, operation, today ->
        LutealUiState(
            today = today,
            cycles = records.cycles,
            currentCycle = records.currentCycle,
            entries = records.entries,
            biomarkers = records.biomarkers,
            preferences = records.preferences,
            estimateResult = CycleEstimateCalculator.evaluate(
                cycles = records.cycles,
                ageBand = AgeBand.fromId(records.preferences.ageBand),
                hasTimingContext = records.preferences.hasTimingContext
            ),
            entrySaveState = operation.entrySaveState,
            operationFailed = operation.failed
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LutealUiState(today = initialToday)
    )

    fun saveEntry(entry: DailyEntry, biomarker: BiomarkerObservation, startsNewCycle: Boolean) {
        viewModelScope.launch {
            operationState.update { it.copy(entrySaveState = EntrySaveState.SAVING) }
            runCatching {
                dailyEntryRepository.save(entry)
                biomarkerRepository.save(biomarker)
                updateCycleForEntry(entry, startsNewCycle)
                notificationScheduler.reconcileAllSchedules()
            }.onSuccess {
                operationState.update { it.copy(entrySaveState = EntrySaveState.SUCCESS) }
            }.onFailure {
                operationState.update { it.copy(entrySaveState = EntrySaveState.FAILED) }
            }
        }
    }

    fun clearEntrySaveState() {
        operationState.update { it.copy(entrySaveState = EntrySaveState.IDLE) }
    }

    fun exportClinicalReport(context: Context, uri: Uri, config: ClinicalReportConfig) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val data = clinicalReportAggregator.aggregate(config)
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        if (config.format == ReportFormat.PDF) {
                            PdfReportBuilder.writePdfToStream(data, stream)
                        } else {
                            HtmlReportBuilder.writeHtmlToStream(data, stream)
                        }
                    }
                }
            }
        }
    }

    fun toggleCycleExclusion(cycleId: String, isExcluded: Boolean, reason: fr.luteal.core.model.CycleExclusionReason?) {
        viewModelScope.launch {
            cycleRepository.updateCycleExclusion(cycleId, isExcluded, reason)
            notificationScheduler.reconcileAllSchedules()
        }
    }

    fun updateDuoSharing(field: DuoSharingField, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { userRepository.updateDuoSharing(field, enabled) }
                .onFailure { operationState.update { state -> state.copy(failed = true) } }
        }
    }
    fun completeOnboarding(
        role: UserRole = UserRole.PRIMARY_TRACKER,
        disorderTracking: Map<String, Boolean> = emptyMap(),
        ageBandId: String? = null
    ) {
        viewModelScope.launch {
            runCatching { userRepository.completeOnboarding(role, disorderTracking, ageBandId) }
                .onFailure { operationState.update { state -> state.copy(failed = true) } }
        }
    }

    fun addBackfilledCycle(startDate: LocalDate) {
        viewModelScope.launch {
            operationState.update { it.copy(failed = false) }
            runCatching {
                val existing = uiState.value.cycles
                require(existing.none { it.startDate == startDate }) {
                    "Un cycle existe déjà à cette date."
                }
                val prevCycle = existing
                    .filter { it.startDate < startDate }
                    .maxByOrNull { it.startDate }
                if (prevCycle != null && (prevCycle.endDate == null || prevCycle.endDate >= startDate)) {
                    cycleRepository.saveCycle(prevCycle.copy(endDate = startDate.minusDays(1)))
                }
                val nextStart = existing
                    .map(Cycle::startDate)
                    .filter { it > startDate }
                    .minOrNull()
                val cycle = Cycle(
                    id = UUID.randomUUID().toString(),
                    startDate = startDate,
                    endDate = nextStart?.minusDays(1)
                )
                cycleRepository.saveCycle(cycle)
                notificationScheduler.reconcileAllSchedules()
            }.onFailure {
                operationState.update { it.copy(failed = true) }
            }
        }
    }

    fun editCycleStartDate(cycleId: String, newStartDate: LocalDate) {
        viewModelScope.launch {
            operationState.update { it.copy(failed = false) }
            runCatching {
                val existing = uiState.value.cycles
                val targetCycle = existing.firstOrNull { it.id == cycleId }
                    ?: error("Cycle introuvable.")
                if (targetCycle.startDate == newStartDate) return@launch

                require(existing.none { it.id != cycleId && it.startDate == newStartDate }) {
                    "Un autre cycle commence déjà à cette date."
                }

                val updatedList = existing.map {
                    if (it.id == cycleId) it.copy(startDate = newStartDate) else it
                }.sortedBy { it.startDate }

                for (i in updatedList.indices) {
                    val current = updatedList[i]
                    val nextStart = updatedList.getOrNull(i + 1)?.startDate
                    val newEndDate = nextStart?.minusDays(1)
                    val reconciled = current.copy(endDate = newEndDate)
                    cycleRepository.saveCycle(reconciled)
                }
                notificationScheduler.reconcileAllSchedules()
            }.onFailure {
                operationState.update { it.copy(failed = true) }
            }
        }
    }

    fun deleteCycle(cycleId: String) {
        viewModelScope.launch {
            operationState.update { it.copy(failed = false) }
            runCatching {
                cycleRepository.deleteCycle(cycleId)
                val remaining = uiState.value.cycles
                    .filter { it.id != cycleId }
                    .sortedBy { it.startDate }

                for (i in remaining.indices) {
                    val current = remaining[i]
                    val nextStart = remaining.getOrNull(i + 1)?.startDate
                    val newEndDate = nextStart?.minusDays(1)
                    if (current.endDate != newEndDate) {
                        cycleRepository.saveCycle(current.copy(endDate = newEndDate))
                    }
                }
                notificationScheduler.reconcileAllSchedules()
            }.onFailure {
                operationState.update { it.copy(failed = true) }
            }
        }
    }

    fun clearOperationError() {
        operationState.update { it.copy(failed = false) }
    }

    private suspend fun updateCycleForEntry(entry: DailyEntry, startsNewCycle: Boolean) {
        val bleeding = entry.bleedingIntensity
        val current = uiState.value.currentCycle

        if (!startsNewCycle || bleeding == null) return

        if (current != null && current.startDate < entry.date) {
            cycleRepository.saveCycle(current.copy(endDate = entry.date.minusDays(1)))
        }

        val nextRecordedStart = uiState.value.cycles
            .map(Cycle::startDate)
            .filter { it > entry.date }
            .minOrNull()
        val existingAtDate = uiState.value.cycles.firstOrNull { it.startDate == entry.date }
        val cycle = (existingAtDate ?: Cycle(
            id = UUID.randomUUID().toString(),
            startDate = entry.date
        )).copy(
            endDate = nextRecordedStart?.minusDays(1)
        )
        cycleRepository.saveCycle(cycle)
    }
}

private data class RecordSnapshot(
    val cycles: List<Cycle>,
    val currentCycle: Cycle?,
    val entries: List<DailyEntry>,
    val biomarkers: List<BiomarkerObservation>,
    val preferences: UserPreferences
)

data class LutealUiState(
    val today: LocalDate,
    val cycles: List<Cycle> = emptyList(),
    val currentCycle: Cycle? = null,
    val entries: List<DailyEntry> = emptyList(),
    val biomarkers: List<BiomarkerObservation> = emptyList(),
    val preferences: UserPreferences = UserPreferences(),
    val estimateResult: CycleEstimateResult = CycleEstimateResult.NeedsMoreHistory,
    val entrySaveState: EntrySaveState = EntrySaveState.IDLE,
    val operationFailed: Boolean = false
) {
    val estimate: CycleEstimate?
        get() = (estimateResult as? CycleEstimateResult.Available)?.estimate

    val todayEntry: DailyEntry?
        get() = entries.firstOrNull { it.date == today }

    val dayOfCycle: Int?
        get() = currentCycle
            ?.takeIf { !today.isBefore(it.startDate) }
            ?.let { java.time.temporal.ChronoUnit.DAYS.between(it.startDate, today).toInt() + 1 }

    val currentPhase: CurrentCyclePhase
        get() = CurrentCyclePhaseCalculator.evaluate(
            today = today,
            currentCycle = currentCycle,
            todayEntry = todayEntry,
            estimateResult = estimateResult
        )
}

enum class EntrySaveState {
    IDLE,
    SAVING,
    SUCCESS,
    FAILED
}

private data class OperationState(
    val entrySaveState: EntrySaveState = EntrySaveState.IDLE,
    val failed: Boolean = false
)

/**
 * Local-date ticker: emits today immediately, then once per local midnight.
 * The date is derived from [clock] on every emission so tests can advance a
 * fake clock; the delay is recomputed from the same instant, keeping the
 * loop exact across DST transitions.
 */
internal fun todayFlow(clock: Clock): Flow<LocalDate> = flow {
    val zone = ZoneId.systemDefault()
    while (true) {
        val now = clock.instant()
        val today = LocalDate.ofInstant(now, zone)
        emit(today)
        val nextMidnight = today.plusDays(1).atStartOfDay(zone).toInstant()
        delay(Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1_000L))
    }
}.distinctUntilChanged()
