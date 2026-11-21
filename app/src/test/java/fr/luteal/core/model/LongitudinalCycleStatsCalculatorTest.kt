package fr.luteal.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LongitudinalCycleStatsCalculatorTest {

    @Test
    fun emptyCyclesReturnsEmptyStats() {
        val stats = LongitudinalCycleStatsCalculator.calculate(emptyList())
        assertEquals(0, stats.totalCyclesCount)
        assertEquals(0, stats.completedCyclesCount)
        assertEquals(0, stats.excludedCyclesCount)
        assertNull(stats.rollingMedianDays)
        assertNull(stats.rollingMeanDays)
        assertTrue(stats.items.isEmpty())
    }

    @Test
    fun regularCyclesCalculatesMedianMeanAndItems() {
        val cycles = listOf(
            Cycle(id = "1", startDate = LocalDate.of(2026, 1, 1), endDate = LocalDate.of(2026, 1, 28)),
            Cycle(id = "2", startDate = LocalDate.of(2026, 1, 29), endDate = LocalDate.of(2026, 2, 26)),
            Cycle(id = "3", startDate = LocalDate.of(2026, 2, 27), endDate = null) // in-progress
        )

        val stats = LongitudinalCycleStatsCalculator.calculate(cycles)
        assertEquals(3, stats.totalCyclesCount)
        assertEquals(2, stats.completedCyclesCount)
        assertEquals(0, stats.excludedCyclesCount)

        assertNotNull(stats.rollingMedianDays)
        assertEquals(28.5, stats.rollingMedianDays!!, 0.5)

        assertEquals(3, stats.items.size)
        // Most recent first
        assertTrue(stats.items[0].isCurrent)
        assertFalse(stats.items[1].isCurrent)
        assertFalse(stats.items[2].isCurrent)
    }

    @Test
    fun excludedCyclesAreOmittedFromRollingAverages() {
        val cycles = listOf(
            Cycle(id = "1", startDate = LocalDate.of(2026, 1, 1), endDate = LocalDate.of(2026, 1, 28)), // 28d
            Cycle(
                id = "2",
                startDate = LocalDate.of(2026, 1, 29),
                endDate = LocalDate.of(2026, 3, 14), // 45d, excluded
                isExcludedFromEstimates = true,
                exclusionReason = CycleExclusionReason.ILLNESS
            ),
            Cycle(id = "3", startDate = LocalDate.of(2026, 3, 15), endDate = LocalDate.of(2026, 4, 11)), // 28d
            Cycle(id = "4", startDate = LocalDate.of(2026, 4, 12), endDate = null) // current
        )

        val stats = LongitudinalCycleStatsCalculator.calculate(cycles)
        assertEquals(4, stats.totalCyclesCount)
        assertEquals(3, stats.completedCyclesCount)
        assertEquals(1, stats.excludedCyclesCount)

        // Rolling mean should only average 28 and 28 -> 28.0 (45 excluded)
        assertEquals(28.0, stats.rollingMeanDays!!, 0.1)
        assertEquals(28.0, stats.rollingMedianDays!!, 0.1)
    }

    @Test
    fun detectsStrawSwingsGreaterOrEqualSevenDays() {
        val cycles = listOf(
            Cycle(id = "1", startDate = LocalDate.of(2026, 1, 1), endDate = LocalDate.of(2026, 1, 28)), // 28d
            Cycle(id = "2", startDate = LocalDate.of(2026, 1, 29), endDate = LocalDate.of(2026, 3, 6)), // 37d (+9d swing)
            Cycle(id = "3", startDate = LocalDate.of(2026, 3, 7), endDate = null)
        )

        val stats = LongitudinalCycleStatsCalculator.calculate(cycles)
        // Items are most recent first: [Cycle 3, Cycle 2, Cycle 1]
        val cycle2Item = stats.items.first { it.cycleId == "2" }
        val cycle1Item = stats.items.first { it.cycleId == "1" }

        assertTrue(cycle2Item.hasSevenDayShift)
        assertFalse(cycle1Item.hasSevenDayShift)
    }

    @Test
    fun excludedFiftyDayCycleFollowedByTwentyEightDayCycleDoesNotTriggerStrawSwing() {
        val cycles = listOf(
            Cycle(
                id = "1",
                startDate = LocalDate.of(2026, 1, 1),
                endDate = LocalDate.of(2026, 2, 19), // 50d
                isExcludedFromEstimates = true,
                exclusionReason = CycleExclusionReason.MEDICAL_TREATMENT
            ),
            Cycle(
                id = "2",
                startDate = LocalDate.of(2026, 2, 20),
                endDate = LocalDate.of(2026, 3, 19) // 28d
            ),
            Cycle(
                id = "3",
                startDate = LocalDate.of(2026, 3, 20),
                endDate = null
            )
        )

        val stats = LongitudinalCycleStatsCalculator.calculate(cycles)
        val cycle2Item = stats.items.first { it.cycleId == "2" }

        assertEquals(28, cycle2Item.lengthDays)
        assertFalse(cycle2Item.hasSevenDayShift)
    }

    @Test
    fun excludedLongCycleBetweenNormalCyclesDoesNotTriggerStrawSwingOnSubsequentCycle() {
        val cycles = listOf(
            Cycle(
                id = "1",
                startDate = LocalDate.of(2026, 1, 1),
                endDate = LocalDate.of(2026, 1, 28) // 28d
            ),
            Cycle(
                id = "2",
                startDate = LocalDate.of(2026, 1, 29),
                endDate = LocalDate.of(2026, 3, 19), // 50d
                isExcludedFromEstimates = true,
                exclusionReason = CycleExclusionReason.ILLNESS
            ),
            Cycle(
                id = "3",
                startDate = LocalDate.of(2026, 3, 20),
                endDate = LocalDate.of(2026, 4, 16) // 28d
            ),
            Cycle(
                id = "4",
                startDate = LocalDate.of(2026, 4, 17),
                endDate = null
            )
        )

        val stats = LongitudinalCycleStatsCalculator.calculate(cycles)
        val cycle3Item = stats.items.first { it.cycleId == "3" }

        assertEquals(28, cycle3Item.lengthDays)
        assertFalse(cycle3Item.hasSevenDayShift)
        val cycle2Item = stats.items.first { it.cycleId == "2" }
        assertFalse("Excluded cycle itself must not carry Shift >= 7d badge", cycle2Item.hasSevenDayShift)
    }

    @Test
    fun gapAboveNinetyDaysResetsConsecutiveShiftComparisonAndSpottingNotCountedAsPeriodFlow() {
        val cycles = listOf(
            Cycle(
                id = "1",
                startDate = LocalDate.of(2026, 1, 1),
                endDate = LocalDate.of(2026, 1, 28), // 28d
                periodDays = listOf(
                    PeriodDay(LocalDate.of(2026, 1, 1), BleedingIntensity.MEDIUM),
                    PeriodDay(LocalDate.of(2026, 1, 14), BleedingIntensity.SPOTTING)
                )
            ),
            Cycle(
                id = "2",
                startDate = LocalDate.of(2026, 1, 29),
                endDate = LocalDate.of(2026, 5, 28) // 120d gap (> 90d)
            ),
            Cycle(
                id = "3",
                startDate = LocalDate.of(2026, 5, 29),
                endDate = LocalDate.of(2026, 7, 4) // 37d (would look like +9d vs cycle 1 without reset)
            ),
            Cycle(id = "4", startDate = LocalDate.of(2026, 7, 5), endDate = null)
        )

        val stats = LongitudinalCycleStatsCalculator.calculate(cycles)
        val c1 = stats.items.first { it.cycleId == "1" }
        val c2 = stats.items.first { it.cycleId == "2" }
        val c3 = stats.items.first { it.cycleId == "3" }

        assertEquals(1, c1.bleedingDaysCount)
        assertFalse("Out-of-range gap must not trigger Shift >= 7d", c2.hasSevenDayShift)
        assertFalse("Cycle after >90d gap must not compare against pre-gap cycle", c3.hasSevenDayShift)
    }
}
