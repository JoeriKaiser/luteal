package fr.luteal.app

import fr.luteal.core.model.Cycle
import fr.luteal.core.model.CycleEstimate
import fr.luteal.core.model.CycleEstimateResult
import fr.luteal.core.model.CurrentCyclePhase
import fr.luteal.core.model.DailyEntry
import fr.luteal.core.model.PhaseCertainty
import fr.luteal.core.model.PhaseIndeterminateReason
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LutealUiStateTest {

    @Test
    fun defaultInitializationState() {
        val today = LocalDate.of(2026, 9, 2)
        val defaultState = LutealUiState(today = today)

        assertFalse(defaultState.isInitializing)
        assertNull(defaultState.todayEntry)
        assertNull(defaultState.dayOfCycle)
        assertNull(defaultState.estimate)
        assertEquals(
            CurrentCyclePhase.Indeterminate(PhaseIndeterminateReason.NO_CURRENT_CYCLE),
            defaultState.currentPhase
        )

        val coldStartState = LutealUiState(today = today, isInitializing = true)
        assertTrue(coldStartState.isInitializing)
    }

    @Test
    fun precomputedValuesAreStoredDirectly() {
        val today = LocalDate.of(2026, 9, 2)
        val cycle = Cycle(id = "cycle-1", startDate = today.minusDays(10))
        val todayEntry = DailyEntry(date = today)
        val estimate = CycleEstimate(
            earliestDate = today.plusDays(15),
            centralDate = today.plusDays(18),
            latestDate = today.plusDays(21),
            cycleCount = 4,
            variabilityDays = 3
        )
        val phase = CurrentCyclePhase.Available(
            phase = fr.luteal.core.model.CyclePhase.FOLLICULAR,
            certainty = PhaseCertainty.ESTIMATED
        )

        val state = LutealUiState(
            today = today,
            cycles = listOf(cycle),
            currentCycle = cycle,
            entries = listOf(todayEntry),
            isInitializing = false,
            todayEntry = todayEntry,
            dayOfCycle = 11,
            estimate = estimate,
            currentPhase = phase
        )

        assertFalse(state.isInitializing)
        assertEquals(todayEntry, state.todayEntry)
        assertEquals(11, state.dayOfCycle)
        assertEquals(estimate, state.estimate)
        assertEquals(phase, state.currentPhase)
    }

    @Test
    fun defaultsDeriveValuesWhenNotPassedExplicitly() {
        val today = LocalDate.of(2026, 9, 2)
        val cycle = Cycle(id = "cycle-1", startDate = today.minusDays(4))
        val todayEntry = DailyEntry(date = today)

        val state = LutealUiState(
            today = today,
            currentCycle = cycle,
            entries = listOf(todayEntry)
        )

        assertEquals(todayEntry, state.todayEntry)
        assertEquals(5, state.dayOfCycle)
    }
}
