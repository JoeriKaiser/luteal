package fr.luteal.core.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CurrentCyclePhaseCalculatorTest {
    private val start = LocalDate.parse("2026-07-01")
    private val cycle = Cycle(id = "current", startDate = start)

    @Test
    fun `cycle start is recorded menstrual phase`() {
        assertPhase(start, CyclePhase.MENSTRUAL, PhaseCertainty.RECORDED)
    }

    @Test
    fun `recorded full flow in early cycle is menstrual phase`() {
        val today = start.plusDays(3)
        assertPhase(
            today,
            CyclePhase.MENSTRUAL,
            PhaseCertainty.RECORDED,
            entry = DailyEntry(today, bleedingIntensity = BleedingIntensity.MEDIUM)
        )
    }

    @Test
    fun `missing early bleeding detail stays indeterminate`() {
        assertReason(
            start.plusDays(3),
            PhaseIndeterminateReason.EARLY_CYCLE_WITHOUT_BLEEDING_DETAIL
        )
    }

    @Test
    fun `spotting does not establish menstrual phase`() {
        val today = start.plusDays(3)
        assertReason(
            today,
            PhaseIndeterminateReason.EARLY_CYCLE_WITHOUT_BLEEDING_DETAIL,
            DailyEntry(today, bleedingIntensity = BleedingIntensity.SPOTTING)
        )
    }

    @Test
    fun `date before ovulation uncertainty is estimated follicular`() {
        assertPhase(
            start.plusDays(8),
            CyclePhase.FOLLICULAR,
            PhaseCertainty.ESTIMATED
        )
    }

    @Test
    fun `phase boundaries remain conservative`() {
        assertPhase(
            LocalDate.parse("2026-07-12"),
            CyclePhase.FOLLICULAR,
            PhaseCertainty.ESTIMATED
        )
        assertReason(
            LocalDate.parse("2026-07-13"),
            PhaseIndeterminateReason.PHASE_TRANSITION
        )
        assertReason(
            LocalDate.parse("2026-07-21"),
            PhaseIndeterminateReason.PHASE_TRANSITION
        )
        assertPhase(
            LocalDate.parse("2026-07-22"),
            CyclePhase.LUTEAL,
            PhaseCertainty.ESTIMATED
        )
    }

    @Test
    fun `ovulation uncertainty remains indeterminate without stable history`() {
        assertReason(
            LocalDate.parse("2026-07-17"),
            PhaseIndeterminateReason.PHASE_TRANSITION
        )
    }

    @Test
    fun `stable history permits central low confidence ovulatory label`() {
        val result = result(cycleCount = 6, variabilityDays = 4)
        val actual = CurrentCyclePhaseCalculator.evaluate(
            today = LocalDate.parse("2026-07-17"),
            currentCycle = cycle,
            todayEntry = null,
            estimateResult = result
        )

        assertEquals(
            CurrentCyclePhase.Available(CyclePhase.OVULATORY, PhaseCertainty.ESTIMATED),
            actual
        )
    }

    @Test
    fun `high variability blocks the central ovulatory label`() {
        val result = result(cycleCount = 6, variabilityDays = 8)
        val actual = CurrentCyclePhaseCalculator.evaluate(
            today = LocalDate.parse("2026-07-17"),
            currentCycle = cycle,
            todayEntry = null,
            estimateResult = result
        )

        assertEquals(
            CurrentCyclePhase.Indeterminate(PhaseIndeterminateReason.PHASE_TRANSITION),
            actual
        )
    }

    @Test
    fun `date after ovulation uncertainty is estimated luteal`() {
        assertPhase(
            LocalDate.parse("2026-07-26"),
            CyclePhase.LUTEAL,
            PhaseCertainty.ESTIMATED
        )
    }

    @Test
    fun `next period window and expired estimate are not called luteal`() {
        assertReason(
            LocalDate.parse("2026-07-28"),
            PhaseIndeterminateReason.NEXT_PERIOD_WINDOW
        )
        assertReason(
            LocalDate.parse("2026-08-04"),
            PhaseIndeterminateReason.ESTIMATE_EXPIRED
        )
    }

    @Test
    fun `insufficient and unsupported histories retain their distinct reasons`() {
        val today = start.plusDays(8)
        val insufficient = CurrentCyclePhaseCalculator.evaluate(
            today,
            cycle,
            DailyEntry(today, bleedingIntensity = BleedingIntensity.NONE),
            CycleEstimateResult.NeedsMoreHistory
        )
        val unsupported = CurrentCyclePhaseCalculator.evaluate(
            today,
            cycle,
            DailyEntry(today, bleedingIntensity = BleedingIntensity.NONE),
            CycleEstimateResult.IntervalsOutOfRange
        )

        assertEquals(
            CurrentCyclePhase.Indeterminate(PhaseIndeterminateReason.NEEDS_MORE_HISTORY),
            insufficient
        )
        assertEquals(
            CurrentCyclePhase.Indeterminate(PhaseIndeterminateReason.INTERVALS_OUT_OF_RANGE),
            unsupported
        )
    }

    private fun assertPhase(
        today: LocalDate,
        phase: CyclePhase,
        certainty: PhaseCertainty,
        entry: DailyEntry? = null
    ) {
        assertEquals(
            CurrentCyclePhase.Available(phase, certainty),
            CurrentCyclePhaseCalculator.evaluate(today, cycle, entry, result())
        )
    }

    private fun assertReason(
        today: LocalDate,
        reason: PhaseIndeterminateReason,
        entry: DailyEntry? = null
    ) {
        assertEquals(
            CurrentCyclePhase.Indeterminate(reason),
            CurrentCyclePhaseCalculator.evaluate(today, cycle, entry, result())
        )
    }

    private fun result(
        cycleCount: Int = 3,
        variabilityDays: Int = 4
    ) = CycleEstimateResult.Available(
        CycleEstimate(
            earliestDate = LocalDate.parse("2026-07-28"),
            centralDate = LocalDate.parse("2026-07-30"),
            latestDate = LocalDate.parse("2026-08-01"),
            cycleCount = cycleCount,
            variabilityDays = variabilityDays
        )
    )
}
