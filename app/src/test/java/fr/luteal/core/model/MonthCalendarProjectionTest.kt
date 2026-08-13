package fr.luteal.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class MonthCalendarProjectionTest {

    @Test
    fun projectsStandardMonthWithMondayStart() {
        val targetMonth = YearMonth.of(2026, 8) // August 2026: Aug 1 is Saturday
        val today = LocalDate.of(2026, 8, 13)

        val projection = MonthCalendarProjectionCalculator.project(
            targetMonth = targetMonth,
            today = today,
            cycles = emptyList(),
            entries = emptyList(),
            estimateResult = CycleEstimateResult.NeedsMoreHistory
        )

        assertEquals(targetMonth, projection.yearMonth)
        // Check all weeks have exactly 7 days
        assertTrue(projection.weeks.isNotEmpty())
        projection.weeks.forEach { week ->
            assertEquals(7, week.size)
        }

        // First day in grid should be Monday July 27, 2026
        val firstDay = projection.weeks.first().first()
        assertEquals(DayOfWeek.MONDAY, firstDay.date.dayOfWeek)
        assertEquals(LocalDate.of(2026, 7, 27), firstDay.date)
        assertFalse(firstDay.isCurrentMonth)

        // Aug 13 should be today
        val aug13 = projection.weeks.flatten().first { it.date == today }
        assertTrue(aug13.isToday)
        assertTrue(aug13.isCurrentMonth)
    }

    @Test
    fun projectsRecordedBleedingAndObservations() {
        val targetMonth = YearMonth.of(2026, 8)
        val today = LocalDate.of(2026, 8, 13)

        val cycleStart = LocalDate.of(2026, 8, 1)
        val cycles = listOf(Cycle(id = "c1", startDate = cycleStart))

        val entries = listOf(
            DailyEntry(
                date = LocalDate.of(2026, 8, 1),
                bleedingIntensity = BleedingIntensity.HEAVY
            ),
            DailyEntry(
                date = LocalDate.of(2026, 8, 2),
                bleedingIntensity = BleedingIntensity.MEDIUM
            ),
            DailyEntry(
                date = LocalDate.of(2026, 8, 3),
                bleedingIntensity = BleedingIntensity.LIGHT
            ),
            DailyEntry(
                date = LocalDate.of(2026, 8, 10),
                painLevel = 3,
                symptomIds = setOf("headache")
            )
        )

        val projection = MonthCalendarProjectionCalculator.project(
            targetMonth = targetMonth,
            today = today,
            cycles = cycles,
            entries = entries,
            estimateResult = CycleEstimateResult.NeedsMoreHistory
        )

        assertEquals(3, projection.recordedPeriodDaysCount)

        val aug1 = projection.weeks.flatten().first { it.date == LocalDate.of(2026, 8, 1) }
        assertTrue(aug1.isCycleStart)
        assertTrue(aug1.hasBleeding)
        assertEquals(BleedingIntensity.HEAVY, aug1.bleedingIntensity)

        val aug10 = projection.weeks.flatten().first { it.date == LocalDate.of(2026, 8, 10) }
        assertFalse(aug10.hasBleeding)
        assertTrue(aug10.hasObservations)
    }

    @Test
    fun projectsEstimatedPeriodWindow() {
        val targetMonth = YearMonth.of(2026, 8)
        val today = LocalDate.of(2026, 8, 13)

        val estimate = CycleEstimate(
            earliestDate = LocalDate.of(2026, 8, 26),
            centralDate = LocalDate.of(2026, 8, 28),
            latestDate = LocalDate.of(2026, 8, 30),
            cycleCount = 4,
            variabilityDays = 4
        )

        val projection = MonthCalendarProjectionCalculator.project(
            targetMonth = targetMonth,
            today = today,
            cycles = emptyList(),
            entries = emptyList(),
            estimateResult = CycleEstimateResult.Available(estimate)
        )

        assertTrue(projection.hasEstimatedPeriodInMonth)

        val aug27 = projection.weeks.flatten().first { it.date == LocalDate.of(2026, 8, 27) }
        assertTrue(aug27.isEstimatedPeriodWindow)

        val aug20 = projection.weeks.flatten().first { it.date == LocalDate.of(2026, 8, 20) }
        assertFalse(aug20.isEstimatedPeriodWindow)
    }

    @Test
    fun projectsLeapYearFebruary() {
        val targetMonth = YearMonth.of(2028, 2)
        val today = LocalDate.of(2028, 2, 15)

        val projection = MonthCalendarProjectionCalculator.project(
            targetMonth = targetMonth,
            today = today,
            cycles = emptyList(),
            entries = emptyList(),
            estimateResult = CycleEstimateResult.NeedsMoreHistory
        )

        val feb29 = projection.weeks.flatten().first { it.date == LocalDate.of(2028, 2, 29) }
        assertTrue(feb29.isCurrentMonth)
    }
}
