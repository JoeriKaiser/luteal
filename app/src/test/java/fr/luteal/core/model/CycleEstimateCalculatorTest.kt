package fr.luteal.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class CycleEstimateCalculatorTest {
    @Test
    fun `returns null when fewer than one interval exists`() {
        val single = listOf(cycle("2025-01-01"))
        assertNull(CycleEstimateCalculator.estimateNextPeriod(single))

        val empty = emptyList<Cycle>()
        assertNull(CycleEstimateCalculator.estimateNextPeriod(empty))
    }

    @Test
    fun `distinguishes missing history from unmodellable intervals`() {
        assertEquals(
            CycleEstimateResult.NeedsMoreHistory,
            CycleEstimateCalculator.evaluate(listOf(cycle("2025-01-01")))
        )

        // Recorded history exists, but every interval exceeds 90 days. This
        // user must not be told they have not recorded enough.
        val longCycles = listOf(
            cycle("2025-01-01"),
            cycle("2025-05-01"),
            cycle("2025-09-01")
        )
        assertEquals(
            CycleEstimateResult.IntervalsOutOfRange,
            CycleEstimateCalculator.evaluate(longCycles)
        )
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
        // One interval carries no variability signal, so the population prior
        // fully determines the radius: ceil(1.96 * 2.6 * sqrt(2/3)) = 5.
        assertEquals(5, radiusOf(estimate))
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
        assertEquals(2, estimate.cycleCount)
        assertTrue(radiusOf(estimate) > 0)
    }

    @Test
    fun `never claims a window tighter than the floor`() {
        // A perfectly regular recorded history still carries real uncertainty.
        val cycles = (0..6).map { cycle(LocalDate.parse("2025-01-01").plusDays(28L * it)) }

        val estimate = requireNotNull(CycleEstimateCalculator.estimateNextPeriod(cycles))

        assertEquals(0, estimate.variabilityDays)
        assertTrue(
            "Radius ${radiusOf(estimate)} must respect the 3-day floor",
            radiusOf(estimate) >= 3
        )
    }

    @Test
    fun `expands the range when recorded intervals vary`() {
        val regular = (0..6).map { cycle(LocalDate.parse("2025-01-01").plusDays(28L * it)) }
        val irregular = listOf(
            cycle("2025-01-01"),
            cycle("2025-01-26"),
            cycle("2025-02-27"),
            cycle("2025-03-25"),
            cycle("2025-05-02")
        )

        val regularRadius = radiusOf(
            requireNotNull(CycleEstimateCalculator.estimateNextPeriod(regular))
        )
        val irregularRadius = radiusOf(
            requireNotNull(CycleEstimateCalculator.estimateNextPeriod(irregular))
        )

        assertTrue(
            "Irregular history ($irregularRadius) must widen versus regular ($regularRadius)",
            irregularRadius > regularRadius
        )
    }

    @Test
    fun `uncertainty shrinks as consistent history accumulates`() {
        // Same underlying regularity, more evidence for it.
        val few = (0..1).map { cycle(LocalDate.parse("2025-01-01").plusDays(28L * it)) }
        val many = (0..6).map { cycle(LocalDate.parse("2025-01-01").plusDays(28L * it)) }

        val fewRadius = radiusOf(requireNotNull(CycleEstimateCalculator.estimateNextPeriod(few)))
        val manyRadius = radiusOf(requireNotNull(CycleEstimateCalculator.estimateNextPeriod(many)))

        assertTrue(
            "Radius must not grow with more consistent evidence ($fewRadius -> $manyRadius)",
            manyRadius <= fewRadius
        )
    }

    @Test
    fun `range stays bounded for extremely erratic history`() {
        val erratic = listOf(
            cycle("2025-01-01"),
            cycle("2025-01-17"),
            cycle("2025-04-10"),
            cycle("2025-04-28"),
            cycle("2025-07-20")
        )

        val estimate = requireNotNull(CycleEstimateCalculator.estimateNextPeriod(erratic))

        assertTrue(radiusOf(estimate) <= 14)
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

    @Test
    fun `estimate window is always symmetric around the central date`() {
        val cycles = listOf(
            cycle("2025-01-01"),
            cycle("2025-01-30"),
            cycle("2025-02-26")
        )

        val estimate = requireNotNull(CycleEstimateCalculator.estimateNextPeriod(cycles))

        assertEquals(
            ChronoUnit.DAYS.between(estimate.earliestDate, estimate.centralDate),
            ChronoUnit.DAYS.between(estimate.centralDate, estimate.latestDate)
        )
    }

    private fun radiusOf(estimate: CycleEstimate): Int =
        ChronoUnit.DAYS.between(estimate.centralDate, estimate.latestDate).toInt()

    private fun cycle(startDate: String) = cycle(LocalDate.parse(startDate))

    private fun cycle(startDate: LocalDate) = Cycle(
        id = startDate.toString(),
        startDate = startDate
    )
}
