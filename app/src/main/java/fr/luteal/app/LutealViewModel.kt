package fr.luteal.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.luteal.core.data.datastore.UserPreferences
import fr.luteal.core.data.repository.CycleRepository
import fr.luteal.core.data.repository.DailyEntryRepository
import fr.luteal.core.data.repository.UserRepository
import fr.luteal.core.model.AgeBand
import fr.luteal.core.model.Cycle
import fr.luteal.core.model.CycleEstimate
import fr.luteal.core.model.CycleEstimateCalculator
import fr.luteal.core.model.CycleEstimateResult
import fr.luteal.core.model.CurrentCyclePhase
import fr.luteal.core.model.CurrentCyclePhaseCalculator
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
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class LutealViewModel @Inject constructor(
    private val cycleRepository: CycleRepository,
    private val dailyEntryRepository: DailyEntryRepository,
    private val userRepository: UserRepository
) : ViewModel() {
    private val operationState = MutableStateFlow(OperationState())
    private val today: LocalDate = LocalDate.now(Clock.systemDefaultZone())

    val uiState: StateFlow<LutealUiState> = combine(
        cycleRepository.getCycles(),
        cycleRepository.getCurrentCycle(),
        dailyEntryRepository.observeEntries(),
        userRepository.getUserPreferences(),
        operationState
    ) { cycles, currentCycle, entries, preferences, operation ->
        LutealUiState(
            today = today,
            cycles = cycles,
            currentCycle = currentCycle,
            entries = entries,
            preferences = preferences,
            estimateResult = CycleEstimateCalculator.evaluate(
                cycles = cycles,
                ageBand = AgeBand.fromId(preferences.ageBand),
                hasTimingContext = preferences.hasTimingContext
            ),
            entrySaveState = operation.entrySaveState,
            operationFailed = operation.failed
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LutealUiState(today = today)
    )

    fun saveEntry(entry: DailyEntry, startsNewCycle: Boolean) {
        viewModelScope.launch {
            operationState.update { it.copy(entrySaveState = EntrySaveState.SAVING) }
            runCatching {
                dailyEntryRepository.save(entry)
                updateCycleForEntry(entry, startsNewCycle)
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

data class LutealUiState(
    val today: LocalDate,
    val cycles: List<Cycle> = emptyList(),
    val currentCycle: Cycle? = null,
    val entries: List<DailyEntry> = emptyList(),
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
