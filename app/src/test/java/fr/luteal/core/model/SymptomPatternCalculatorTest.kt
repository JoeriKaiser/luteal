package fr.luteal.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SymptomPatternCalculatorTest {

    @Test
    fun `empty entries returns empty list`() {
        val cycles = listOf(
            Cycle(id = "c1", startDate = LocalDate.of(2026, 1, 1), endDate = LocalDate.of(2026, 1, 28))
        )
        val result = SymptomPatternCalculator.calculate(cycles, emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `entries without symptoms returns empty list`() {
        val cycles = listOf(
            Cycle(id = "c1", startDate = LocalDate.of(2026, 1, 1), endDate = LocalDate.of(2026, 1, 28))
        )
        val entries = listOf(
            DailyEntry(date = LocalDate.of(2026, 1, 5), painLevel = 2)
        )
        val result = SymptomPatternCalculator.calculate(cycles, entries)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `aggregates single symptom in menstrual phase`() {
        val cycles = listOf(
            Cycle(id = "c1", startDate = LocalDate.of(2026, 1, 1), endDate = LocalDate.of(2026, 1, 28))
        )
        val entries = listOf(
            DailyEntry(
                date = LocalDate.of(2026, 1, 2),
                bleedingIntensity = BleedingIntensity.MEDIUM,
                symptomIds = setOf("cramps")
            )
        )
        val result = SymptomPatternCalculator.calculate(cycles, entries)
        assertEquals(1, result.size)
        val cramps = result.first()
        assertEquals("cramps", cramps.symptomId)
        assertEquals(1, cramps.totalOccurrences)
        assertEquals(1, cramps.cycleCount)
        assertEquals(1, cramps.phaseBreakdown[CyclePhase.MENSTRUAL])
        assertEquals(0, cramps.phaseBreakdown[CyclePhase.FOLLICULAR])
        assertEquals(0, cramps.phaseBreakdown[CyclePhase.OVULATORY])
        assertEquals(0, cramps.phaseBreakdown[CyclePhase.LUTEAL])
        assertEquals(CyclePhase.MENSTRUAL, cramps.mostFrequentPhase)
    }

    @Test
    fun `aggregates luteal phase symptoms correctly`() {
        val cycles = listOf(
            Cycle(id = "c1", startDate = LocalDate.of(2026, 1, 1), endDate = LocalDate.of(2026, 1, 28))
        )
        // Day 20 of a 28-day cycle is luteal (dayIndex 19 >= lutealStart 14)
        val entries = listOf(
            DailyEntry(date = LocalDate.of(2026, 1, 20), symptomIds = setOf("nausea", "bloating")),
            DailyEntry(date = LocalDate.of(2026, 1, 22), symptomIds = setOf("nausea"))
        )
        val result = SymptomPatternCalculator.calculate(cycles, entries)
        assertEquals(2, result.size)

        val nausea = result.first { it.symptomId == "nausea" }
        assertEquals(2, nausea.totalOccurrences)
        assertEquals(1, nausea.cycleCount)
        assertEquals(2, nausea.phaseBreakdown[CyclePhase.LUTEAL])
        assertEquals(CyclePhase.LUTEAL, nausea.mostFrequentPhase)

        val bloating = result.first { it.symptomId == "bloating" }
        assertEquals(1, bloating.totalOccurrences)
        assertEquals(1, bloating.cycleCount)
        assertEquals(1, bloating.phaseBreakdown[CyclePhase.LUTEAL])
    }

    @Test
    fun `tracks symptom across multiple cycles and sorts by frequency descending`() {
        val cycles = listOf(
            Cycle(id = "c1", startDate = LocalDate.of(2026, 1, 1), endDate = LocalDate.of(2026, 1, 28)),
            Cycle(id = "c2", startDate = LocalDate.of(2026, 1, 29), endDate = LocalDate.of(2026, 2, 25))
        )
        val entries = listOf(
            DailyEntry(date = LocalDate.of(2026, 1, 2), symptomIds = setOf("fatigue")),
            DailyEntry(date = LocalDate.of(2026, 1, 20), symptomIds = setOf("fatigue", "headache")),
            DailyEntry(date = LocalDate.of(2026, 2, 5), symptomIds = setOf("fatigue"))
        )
        val result = SymptomPatternCalculator.calculate(cycles, entries)
        assertEquals(2, result.size)
        // fatigue has 3 occurrences across 2 cycles, headache has 1
        assertEquals("fatigue", result[0].symptomId)
        assertEquals(3, result[0].totalOccurrences)
        assertEquals(2, result[0].cycleCount)

        assertEquals("headache", result[1].symptomId)
        assertEquals(1, result[1].totalOccurrences)
        assertEquals(1, result[1].cycleCount)
    }

    @Test
    fun `bleeding overrides date-based phase to menstrual`() {
        val cycles = listOf(
            Cycle(id = "c1", startDate = LocalDate.of(2026, 1, 1), endDate = LocalDate.of(2026, 1, 28))
        )
        // Day 10 without bleeding would be follicular, but with bleeding it is menstrual
        val entries = listOf(
            DailyEntry(
                date = LocalDate.of(2026, 1, 10),
                bleedingIntensity = BleedingIntensity.LIGHT,
                symptomIds = setOf("cramps")
            )
        )
        val result = SymptomPatternCalculator.calculate(cycles, entries)
        assertEquals(1, result.first().phaseBreakdown[CyclePhase.MENSTRUAL])
        assertEquals(0, result.first().phaseBreakdown[CyclePhase.FOLLICULAR])
        assertEquals(CyclePhase.MENSTRUAL, result.first().mostFrequentPhase)
    }
}
