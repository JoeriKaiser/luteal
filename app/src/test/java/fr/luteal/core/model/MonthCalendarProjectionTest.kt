package fr.luteal.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun projectsStandardMonthWithSundayStart() {
        val targetMonth = YearMonth.of(2026, 8) // August 2026: Aug 1 is Saturday
        val today = LocalDate.of(2026, 8, 13)

        val projection = MonthCalendarProjectionCalculator.project(
            targetMonth = targetMonth,
            today = today,
            cycles = emptyList(),
            entries = emptyList(),
            estimateResult = CycleEstimateResult.NeedsMoreHistory,
            firstDayOfWeek = DayOfWeek.SUNDAY
        )

        // With a Sunday start, the grid begins on Sunday July 26, 2026.
        val firstDay = projection.weeks.first().first()
        assertEquals(DayOfWeek.SUNDAY, firstDay.date.dayOfWeek)
        assertEquals(LocalDate.of(2026, 7, 26), firstDay.date)
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

    @Test
    fun `past dates in estimate window do not show as estimated period window`() {
        val targetMonth = YearMonth.of(2026, 8)
        val today = LocalDate.of(2026, 9, 5)

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

        val aug27 = projection.weeks.flatten().first { it.date == LocalDate.of(2026, 8, 27) }
        assertFalse(aug27.isEstimatedPeriodWindow)
    }

    @Test
    fun `recorded bleeding on future estimated date suppresses isEstimatedPeriodWindow`() {
        val targetMonth = YearMonth.of(2026, 8)
        val today = LocalDate.of(2026, 8, 20)

        val estimate = CycleEstimate(
            earliestDate = LocalDate.of(2026, 8, 26),
            centralDate = LocalDate.of(2026, 8, 28),
            latestDate = LocalDate.of(2026, 8, 30),
            cycleCount = 4,
            variabilityDays = 4
        )

        val entries = listOf(
            DailyEntry(
                date = LocalDate.of(2026, 8, 27),
                bleedingIntensity = BleedingIntensity.MEDIUM
            )
        )

        val projection = MonthCalendarProjectionCalculator.project(
            targetMonth = targetMonth,
            today = today,
            cycles = emptyList(),
            entries = entries,
            estimateResult = CycleEstimateResult.Available(estimate)
        )

        val aug27 = projection.weeks.flatten().first { it.date == LocalDate.of(2026, 8, 27) }
        assertFalse(aug27.isEstimatedPeriodWindow)
        assertFalse(aug27.isEstimatedPeriodTarget)
        assertTrue(aug27.hasBleeding)

        val aug28 = projection.weeks.flatten().first { it.date == LocalDate.of(2026, 8, 28) }
        assertTrue(aug28.isEstimatedPeriodTarget)
        assertFalse(aug28.isEstimatedPeriodWindow)

        val aug29 = projection.weeks.flatten().first { it.date == LocalDate.of(2026, 8, 29) }
        assertTrue(aug29.isEstimatedPeriodWindow)
        assertFalse(aug29.isEstimatedPeriodTarget)
    }

    @Test
    fun projectsFourPhasesOnCompletedCycle() {
        val targetMonth = YearMonth.of(2026, 8)
        val today = LocalDate.of(2026, 8, 31)

        val cycle = Cycle(
            id = "c1",
            startDate = LocalDate.of(2026, 8, 1),
            endDate = LocalDate.of(2026, 8, 30)
        )

        val entries = listOf(
            DailyEntry(date = LocalDate.of(2026, 8, 1), bleedingIntensity = BleedingIntensity.HEAVY),
            DailyEntry(date = LocalDate.of(2026, 8, 2), bleedingIntensity = BleedingIntensity.HEAVY),
            DailyEntry(date = LocalDate.of(2026, 8, 3), bleedingIntensity = BleedingIntensity.MEDIUM),
            DailyEntry(date = LocalDate.of(2026, 8, 4), bleedingIntensity = BleedingIntensity.LIGHT)
        )

        val projection = MonthCalendarProjectionCalculator.project(
            targetMonth = targetMonth,
            today = today,
            cycles = listOf(cycle),
            entries = entries,
            estimateResult = CycleEstimateResult.NeedsMoreHistory
        )

        val daysMap = projection.weeks.flatten().associateBy { it.date }

        for (dayNum in 1..4) {
            val day = daysMap[LocalDate.of(2026, 8, dayNum)]!!
            assertEquals(dayNum, day.cycleDayNumber)
            assertEquals(CyclePhase.MENSTRUAL, day.cyclePhase)
            assertEquals(PhaseCertainty.RECORDED, day.phaseCertainty)
        }

        val day8 = daysMap[LocalDate.of(2026, 8, 8)]!!
        assertEquals(8, day8.cycleDayNumber)
        assertEquals(CyclePhase.FOLLICULAR, day8.cyclePhase)
        assertEquals(PhaseCertainty.ESTIMATED, day8.phaseCertainty)

        val day14 = daysMap[LocalDate.of(2026, 8, 14)]!!
        assertEquals(14, day14.cycleDayNumber)
        assertEquals(CyclePhase.OVULATORY, day14.cyclePhase)
        assertEquals(PhaseCertainty.ESTIMATED, day14.phaseCertainty)

        val day22 = daysMap[LocalDate.of(2026, 8, 22)]!!
        assertEquals(22, day22.cycleDayNumber)
        assertEquals(CyclePhase.LUTEAL, day22.cyclePhase)
        assertEquals(PhaseCertainty.ESTIMATED, day22.phaseCertainty)
    }

    @Test
    fun projectsFourPhasesOnCurrentCycleWithEstimate() {
        val targetMonth = YearMonth.of(2026, 8)
        val today = LocalDate.of(2026, 8, 20)

        val cycle = Cycle(
            id = "c1",
            startDate = LocalDate.of(2026, 8, 1),
            endDate = null
        )

        val entries = listOf(
            DailyEntry(date = LocalDate.of(2026, 8, 1), bleedingIntensity = BleedingIntensity.HEAVY),
            DailyEntry(date = LocalDate.of(2026, 8, 2), bleedingIntensity = BleedingIntensity.HEAVY),
            DailyEntry(date = LocalDate.of(2026, 8, 3), bleedingIntensity = BleedingIntensity.MEDIUM)
        )

        val estimate = CycleEstimate(
            earliestDate = LocalDate.of(2026, 8, 27),
            centralDate = LocalDate.of(2026, 8, 29),
            latestDate = LocalDate.of(2026, 8, 31),
            cycleCount = 3,
            variabilityDays = 4
        )

        val projection = MonthCalendarProjectionCalculator.project(
            targetMonth = targetMonth,
            today = today,
            cycles = listOf(cycle),
            entries = entries,
            estimateResult = CycleEstimateResult.Available(estimate)
        )

        val daysMap = projection.weeks.flatten().associateBy { it.date }

        val day2 = daysMap[LocalDate.of(2026, 8, 2)]!!
        assertEquals(2, day2.cycleDayNumber)
        assertEquals(CyclePhase.MENSTRUAL, day2.cyclePhase)
        assertEquals(PhaseCertainty.RECORDED, day2.phaseCertainty)

        val day10 = daysMap[LocalDate.of(2026, 8, 10)]!!
        assertEquals(10, day10.cycleDayNumber)
        assertEquals(CyclePhase.FOLLICULAR, day10.cyclePhase)
        assertEquals(PhaseCertainty.ESTIMATED, day10.phaseCertainty)

        val day16 = daysMap[LocalDate.of(2026, 8, 16)]!!
        assertEquals(16, day16.cycleDayNumber)
        assertEquals(CyclePhase.OVULATORY, day16.cyclePhase)
        assertEquals(PhaseCertainty.ESTIMATED, day16.phaseCertainty)

        val day20 = daysMap[LocalDate.of(2026, 8, 20)]!!
        assertEquals(20, day20.cycleDayNumber)
        assertEquals(CyclePhase.LUTEAL, day20.cyclePhase)
        assertEquals(PhaseCertainty.ESTIMATED, day20.phaseCertainty)

        val day24 = daysMap[LocalDate.of(2026, 8, 24)]!!
        assertEquals(24, day24.cycleDayNumber)
        assertEquals(CyclePhase.LUTEAL, day24.cyclePhase)
        assertEquals(PhaseCertainty.ESTIMATED, day24.phaseCertainty)

        val day29 = daysMap[LocalDate.of(2026, 8, 29)]!!
        assertEquals(29, day29.cycleDayNumber)
        assertEquals(CyclePhase.MENSTRUAL, day29.cyclePhase)
        assertEquals(PhaseCertainty.ESTIMATED, day29.phaseCertainty)
    }

    @Test
    fun distinguishesSpottingFromPeriodFlow() {
        val targetMonth = YearMonth.of(2026, 8)
        val today = LocalDate.of(2026, 8, 20)

        val cycle = Cycle(
            id = "c1",
            startDate = LocalDate.of(2026, 8, 1)
        )

        val entries = listOf(
            DailyEntry(date = LocalDate.of(2026, 8, 1), bleedingIntensity = BleedingIntensity.MEDIUM),
            DailyEntry(date = LocalDate.of(2026, 8, 10), bleedingIntensity = BleedingIntensity.SPOTTING)
        )

        val projection = MonthCalendarProjectionCalculator.project(
            targetMonth = targetMonth,
            today = today,
            cycles = listOf(cycle),
            entries = entries,
            estimateResult = CycleEstimateResult.NeedsMoreHistory
        )

        val daysMap = projection.weeks.flatten().associateBy { it.date }

        val aug10 = daysMap[LocalDate.of(2026, 8, 10)]!!
        assertEquals(BleedingIntensity.SPOTTING, aug10.bleedingIntensity)
        assertTrue(aug10.isSpottingOnly)
        assertFalse(aug10.hasPeriodFlow)
        assertTrue(aug10.hasBleeding)

        val aug1 = daysMap[LocalDate.of(2026, 8, 1)]!!
        assertEquals(BleedingIntensity.MEDIUM, aug1.bleedingIntensity)
        assertFalse(aug1.isSpottingOnly)
        assertTrue(aug1.hasPeriodFlow)
        assertTrue(aug1.hasBleeding)
    }

    @Test
    fun projectsBiomarkersOnCalendarDays() {
        val targetMonth = YearMonth.of(2026, 8)
        val today = LocalDate.of(2026, 8, 20)

        val biomarkers = listOf(
            BiomarkerObservation(
                date = LocalDate.of(2026, 8, 15),
                bbt = BasalBodyTemperature(valueCelsius = 36.65),
                cervicalFluid = CervicalFluidObservation(
                    texture = CervicalMucusTexture.EGG_WHITE,
                    sensation = CervicalMucusSensation.SLIPPERY
                ),
                rapidTests = RapidTestLogs(lhTest = LhTestResult.PEAK_POSITIVE)
            )
        )

        val projection = MonthCalendarProjectionCalculator.project(
            targetMonth = targetMonth,
            today = today,
            cycles = emptyList(),
            entries = emptyList(),
            estimateResult = CycleEstimateResult.NeedsMoreHistory,
            biomarkers = biomarkers
        )

        val daysMap = projection.weeks.flatten().associateBy { it.date }

        val aug15 = daysMap[LocalDate.of(2026, 8, 15)]!!
        assertTrue(aug15.hasBiomarkers)
        assertEquals(36.65, aug15.bbtCelsius!!, 0.001)
        assertTrue(aug15.hasCervicalFluid)
        assertTrue(aug15.hasPositiveLh)
        assertNotNull(aug15.biomarker)
        assertEquals(36.65, aug15.biomarker?.bbt?.valueCelsius!!, 0.001)

        val aug16 = daysMap[LocalDate.of(2026, 8, 16)]!!
        assertFalse(aug16.hasBiomarkers)
        assertNull(aug16.bbtCelsius)
        assertFalse(aug16.hasCervicalFluid)
        assertFalse(aug16.hasPositiveLh)
        assertNull(aug16.biomarker)
    }
}
