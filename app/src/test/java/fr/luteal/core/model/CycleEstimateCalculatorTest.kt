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
        // One interval carries no variability signal, so the undeclared prior
        // fully determines the radius: ceil(1.96 * 4.54) = 9.
        assertEquals(9, radiusOf(estimate))
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

        assertTrue(radiusOf(estimate) <= 22)
    }

    @Test
    fun `recurring seven day swings withdraw the population prior`() {
        // Same mean interval in both, so only the variability pattern differs.
        // Steady: 28-day cycles with a single one-off 8-day swing, which STRAW
        // treats as one unusual month rather than a property of the cycles.
        val oneOffSwing = intervalsToCycles(listOf(28, 28, 28, 36, 28, 28))
        // Persistent: swings of 7 or more recurring inside the ten-cycle window.
        val recurringSwings = intervalsToCycles(listOf(28, 36, 28, 37, 29, 36))

        val steadyRadius =
            radiusOf(requireNotNull(CycleEstimateCalculator.estimateNextPeriod(oneOffSwing)))
        val variableRadius =
            radiusOf(requireNotNull(CycleEstimateCalculator.estimateNextPeriod(recurringSwings)))

        assertTrue(
            "Recurring swings ($variableRadius) must widen versus a one-off ($steadyRadius)",
            variableRadius > steadyRadius
        )
    }

    @Test
    fun `a single large swing does not trigger the variability path`() {
        // One 9-day swing in an otherwise regular history. Requiring recurrence
        // is what stops a single mistyped or unusual cycle widening everything.
        val cycles = intervalsToCycles(listOf(28, 28, 28, 28, 37))

        val estimate = requireNotNull(CycleEstimateCalculator.estimateNextPeriod(cycles))

        // With the prior still at full weight the radius stays modest; the
        // withdrawn-prior path would push this materially wider.
        assertTrue(
            "Radius ${radiusOf(estimate)} should stay bounded for one swing",
            radiusOf(estimate) < 12
        )
    }

    @Test
    fun `honest uncertainty is no longer truncated at fourteen days`() {
        // Wildly varying but in-range intervals. The old cap reported more
        // precision than this history supports.
        val cycles = intervalsToCycles(listOf(16, 84, 18, 83, 17, 85))

        val estimate = requireNotNull(CycleEstimateCalculator.estimateNextPeriod(cycles))

        assertTrue(
            "Radius ${radiusOf(estimate)} should exceed the retired 14-day cap",
            radiusOf(estimate) > 14
        )
        assertTrue(radiusOf(estimate) <= 22)
    }

    @Test
    fun `age band selects the variability prior`() {
        // Two recorded intervals, so the prior still dominates and the band
        // choice is visible in the result.
        val cycles = intervalsToCycles(listOf(28, 28))

        val lowestVariability = radiusOf(
            requireNotNull(
                CycleEstimateCalculator.estimateNextPeriod(cycles, AgeBand.AGE_35_39)
            )
        )
        val highestVariability = radiusOf(
            requireNotNull(
                CycleEstimateCalculator.estimateNextPeriod(cycles, AgeBand.AGE_50_PLUS)
            )
        )

        assertTrue(
            "50+ ($highestVariability) must be wider than 35-39 ($lowestVariability)",
            highestVariability > lowestVariability
        )
    }

    @Test
    fun `undeclared age band does not default to the narrowest prior`() {
        val cycles = intervalsToCycles(listOf(28, 28))

        val undeclared =
            radiusOf(requireNotNull(CycleEstimateCalculator.estimateNextPeriod(cycles)))
        val narrowestBand = radiusOf(
            requireNotNull(
                CycleEstimateCalculator.estimateNextPeriod(cycles, AgeBand.AGE_35_39)
            )
        )

        assertTrue(
            "Undeclared ($undeclared) must not be narrower than the lowest band " +
                "($narrowestBand): understating uncertainty is the failure that matters",
            undeclared >= narrowestBand
        )
    }

    @Test
    fun `declared timing context widens the window`() {
        val cycles = intervalsToCycles(listOf(28, 29, 28))

        val withoutContext = radiusOf(
            requireNotNull(
                CycleEstimateCalculator.estimateNextPeriod(cycles, hasTimingContext = false)
            )
        )
        val withContext = radiusOf(
            requireNotNull(
                CycleEstimateCalculator.estimateNextPeriod(cycles, hasTimingContext = true)
            )
        )

        assertTrue(
            "Declared timing context ($withContext) must widen versus none ($withoutContext)",
            withContext > withoutContext
        )
    }

    @Test
    fun `a declared context never moves the central date`() {
        val cycles = intervalsToCycles(listOf(28, 29, 28))

        val plain = requireNotNull(CycleEstimateCalculator.estimateNextPeriod(cycles))
        val declared = requireNotNull(
            CycleEstimateCalculator.estimateNextPeriod(
                cycles,
                AgeBand.AGE_50_PLUS,
                hasTimingContext = true
            )
        )

        // Contexts and age may only change how uncertain the app says it is.
        // Moving the prediction itself would be inference about a condition.
        assertEquals(plain.centralDate, declared.centralDate)
    }

    @Test
    fun `observed variability outranks a mere declaration`() {
        // Swings well beyond the population floor, so the user's own record is
        // unambiguously the stronger evidence.
        val recurringSwings = intervalsToCycles(listOf(24, 40, 26, 42, 25, 41))

        val declaredOnly = radiusOf(
            requireNotNull(
                CycleEstimateCalculator.estimateNextPeriod(
                    intervalsToCycles(listOf(28, 29, 28, 29, 28, 29)),
                    hasTimingContext = true
                )
            )
        )
        val observed = radiusOf(
            requireNotNull(CycleEstimateCalculator.estimateNextPeriod(recurringSwings))
        )

        assertTrue(
            "Recorded variability ($observed) should outweigh a declaration alone " +
                "($declaredOnly)",
            observed > declaredOnly
        )
    }

    /** Builds a cycle history from a list of interval lengths in days. */
    private fun intervalsToCycles(intervals: List<Int>): List<Cycle> {
        var date = LocalDate.parse("2025-01-01")
        val cycles = mutableListOf(cycle(date))
        for (interval in intervals) {
            date = date.plusDays(interval.toLong())
            cycles += cycle(date)
        }
        return cycles
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

    @Test
    fun `backfilled cycles properly close prior open cycles`() {
        val c1 = Cycle(id = "1", startDate = LocalDate.parse("2026-05-24"))
        val c2 = Cycle(id = "2", startDate = LocalDate.parse("2026-06-21"), endDate = null)
        val closedC1 = c1.copy(endDate = c2.startDate.minusDays(1))

        assertEquals(LocalDate.parse("2026-06-20"), closedC1.endDate)
        assertEquals(28, closedC1.lengthInDays)
    }

    @Test
    fun `excluded cycle intervals are omitted from estimation`() {
        val c1 = Cycle(id = "1", startDate = LocalDate.parse("2026-01-01"))
        val c2 = Cycle(id = "2", startDate = LocalDate.parse("2026-01-29"))
        val c3 = Cycle(
            id = "3",
            startDate = LocalDate.parse("2026-02-26"),
            isExcludedFromEstimates = true,
            exclusionReason = CycleExclusionReason.ILLNESS
        )
        val c4 = Cycle(id = "4", startDate = LocalDate.parse("2026-04-12"))

        val result = CycleEstimateCalculator.evaluate(listOf(c1, c2, c3, c4))
        assertTrue(result is CycleEstimateResult.Available)
        val estimate = (result as CycleEstimateResult.Available).estimate

        // Only interval C1->C2 (28 days) is valid and included; central date = C4 + 28 days
        assertEquals(LocalDate.parse("2026-05-10"), estimate.centralDate)
    }
}
