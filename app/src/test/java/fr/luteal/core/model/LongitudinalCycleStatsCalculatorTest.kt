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

        assertTrue(cycle2Item.hasStrawSwing)
        assertFalse(cycle1Item.hasStrawSwing)
    }
}
