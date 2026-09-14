package fr.luteal.core.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CurrentCyclePhaseCalculatorTest {
    private val start = LocalDate.parse("2026-07-01")
    private val central = LocalDate.parse("2026-07-30")
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
    fun `active bleeding on cycle day 8 in todayEntry returns menstrual phase`() {
        val today = start.plusDays(8)
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
    fun `ovulation band is plus or minus two days around the luteal anchor`() {
        assertPhase(
            LocalDate.parse("2026-07-14"),
            CyclePhase.FOLLICULAR,
            PhaseCertainty.ESTIMATED
        )
        assertReason(
            LocalDate.parse("2026-07-15"),
            PhaseIndeterminateReason.PHASE_TRANSITION
        )
        assertReason(
            LocalDate.parse("2026-07-19"),
            PhaseIndeterminateReason.PHASE_TRANSITION
        )
        assertPhase(
            LocalDate.parse("2026-07-20"),
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
        val actual = CurrentCyclePhaseCalculator.evaluate(
            today = LocalDate.parse("2026-07-17"),
            currentCycle = cycle,
            todayEntry = null,
            estimateResult = result(radius = 5, cycleCount = 6, variabilityDays = 4)
        )

        assertEquals(
            CurrentCyclePhase.Available(CyclePhase.OVULATORY, PhaseCertainty.ESTIMATED),
            actual
        )
    }

    @Test
    fun `high variability blocks the central ovulatory label`() {
        val actual = CurrentCyclePhaseCalculator.evaluate(
            today = LocalDate.parse("2026-07-17"),
            currentCycle = cycle,
            todayEntry = null,
            estimateResult = result(radius = 5, cycleCount = 6, variabilityDays = 8)
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
    fun `luteal span does not depend on earliest date`() {
        val lutealDay = LocalDate.parse("2026-07-20")
        for (radius in listOf(4, 5, 9, 11, 22)) {
            assertEquals(
                "R=$radius should still name luteal on $lutealDay",
                CurrentCyclePhase.Available(CyclePhase.LUTEAL, PhaseCertainty.ESTIMATED),
                evaluateOn(lutealDay, radius)
            )
        }
    }

    @Test
    fun `ten days before central date is luteal at production radius five`() {
        assertEquals(
            CurrentCyclePhase.Available(CyclePhase.LUTEAL, PhaseCertainty.ESTIMATED),
            evaluateOn(central.minusDays(10), radius = 5)
        )
    }

    @Test
    fun `next period window starts two days before central date`() {
        assertPhase(
            LocalDate.parse("2026-07-27"),
            CyclePhase.LUTEAL,
            PhaseCertainty.ESTIMATED
        )
        assertReason(
            LocalDate.parse("2026-07-28"),
            PhaseIndeterminateReason.NEXT_PERIOD_WINDOW
        )
        assertReason(
            LocalDate.parse("2026-08-04"),
            PhaseIndeterminateReason.NEXT_PERIOD_WINDOW,
            radius = 5
        )
        assertReason(
            LocalDate.parse("2026-08-05"),
            PhaseIndeterminateReason.ESTIMATE_EXPIRED,
            radius = 5
        )
    }

    @Test
    fun `ovulatory label remains reachable when radius is eleven`() {
        val actual = CurrentCyclePhaseCalculator.evaluate(
            today = LocalDate.parse("2026-07-17"),
            currentCycle = cycle,
            todayEntry = null,
            estimateResult = result(radius = 11, cycleCount = 6, variabilityDays = 4)
        )
        assertEquals(
            CurrentCyclePhase.Available(CyclePhase.OVULATORY, PhaseCertainty.ESTIMATED),
            actual
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
        entry: DailyEntry? = null,
        radius: Int = 5
    ) {
        assertEquals(
            CurrentCyclePhase.Available(phase, certainty),
            CurrentCyclePhaseCalculator.evaluate(today, cycle, entry, result(radius))
        )
    }

    private fun assertReason(
        today: LocalDate,
        reason: PhaseIndeterminateReason,
        entry: DailyEntry? = null,
        radius: Int = 5
    ) {
        assertEquals(
            CurrentCyclePhase.Indeterminate(reason),
            CurrentCyclePhaseCalculator.evaluate(today, cycle, entry, result(radius))
        )
    }

    private fun evaluateOn(today: LocalDate, radius: Int) =
        CurrentCyclePhaseCalculator.evaluate(today, cycle, null, result(radius))

    private fun result(
        radius: Int,
        cycleCount: Int = 3,
        variabilityDays: Int = 4
    ) = CycleEstimateResult.Available(
        CycleEstimate(
            earliestDate = central.minusDays(radius.toLong()),
            centralDate = central,
            latestDate = central.plusDays(radius.toLong()),
            cycleCount = cycleCount,
            variabilityDays = variabilityDays
        )
    )
}
