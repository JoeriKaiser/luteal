package fr.luteal.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CycleEstimateCalculatorTest {
    @Test
    fun `returns null when fewer than one interval exists`() {
        val single = listOf(cycle("2025-01-01"))
        assertNull(CycleEstimateCalculator.estimateNextPeriod(single))

        val empty = emptyList<Cycle>()
        assertNull(CycleEstimateCalculator.estimateNextPeriod(empty))
    }

    @Test
    fun `single interval produces a wide low-confidence range`() {
        val cycles = listOf(
            cycle("2025-01-01"),
            cycle("2025-01-29")
        )

        val estimate = requireNotNull(CycleEstimateCalculator.estimateNextPeriod(cycles))

        assertEquals(1, estimate.cycleCount)
        // Interval is 28 days; central = 2025-01-29 + 28 = 2025-02-26.
        assertEquals(LocalDate.parse("2025-02-26"), estimate.centralDate)
        // Forced radius of 5 days each side for a single observation.
        assertEquals(LocalDate.parse("2025-02-21"), estimate.earliestDate)
        assertEquals(LocalDate.parse("2025-03-03"), estimate.latestDate)
        assertEquals(0, estimate.variabilityDays)
    }

    @Test
    fun `returns a range rather than a single certain date`() {
        val cycles = listOf(
            cycle("2025-01-01"),
            cycle("2025-01-29"),
            cycle("2025-02-27")
        )

        val estimate = requireNotNull(CycleEstimateCalculator.estimateNextPeriod(cycles))

        assertEquals(LocalDate.parse("2025-03-28"), estimate.centralDate)
        assertEquals(LocalDate.parse("2025-03-26"), estimate.earliestDate)
        assertEquals(LocalDate.parse("2025-03-30"), estimate.latestDate)
        assertEquals(2, estimate.cycleCount)
    }

    @Test
    fun `expands the range when recorded intervals vary`() {
        val cycles = listOf(
            cycle("2025-01-01"),
            cycle("2025-01-26"),
            cycle("2025-02-27"),
            cycle("2025-03-25")
        )

        val estimate = requireNotNull(CycleEstimateCalculator.estimateNextPeriod(cycles))

        assertEquals(7, estimate.variabilityDays)
        assertEquals(8, estimate.latestDate.dayOfYear - estimate.earliestDate.dayOfYear)
    }

    @Test
    fun `sorts cycle history before calculating intervals`() {
        val cycles = listOf(
            cycle("2025-02-27"),
            cycle("2025-01-01"),
            cycle("2025-01-29")
        )

        val estimate = requireNotNull(CycleEstimateCalculator.estimateNextPeriod(cycles))

        assertEquals(LocalDate.parse("2025-03-28"), estimate.centralDate)
    }

    private fun cycle(startDate: String) = Cycle(
        id = startDate,
        startDate = LocalDate.parse(startDate)
    )
}
