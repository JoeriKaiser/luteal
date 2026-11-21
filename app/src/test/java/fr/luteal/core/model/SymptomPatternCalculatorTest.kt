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
    fun `single occurrence records menstrual count without declaring mostFrequentPhase`() {
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
        assertNull(cramps.mostFrequentPhase)
    }

    @Test
    fun `aggregates luteal phase symptoms across cycles and requires minimum threshold for top phase`() {
        val cycles = listOf(
            Cycle(id = "c1", startDate = LocalDate.of(2026, 1, 1), endDate = LocalDate.of(2026, 1, 28)),
            Cycle(id = "c2", startDate = LocalDate.of(2026, 1, 29), endDate = LocalDate.of(2026, 2, 25))
        )
        // Day 20 of a 28-day cycle (dayIndex 19 > ovulationLatest 17) is luteal
        val entries = listOf(
            DailyEntry(date = LocalDate.of(2026, 1, 20), symptomIds = setOf("nausea", "bloating")),
            DailyEntry(date = LocalDate.of(2026, 1, 22), symptomIds = setOf("nausea")),
            DailyEntry(date = LocalDate.of(2026, 2, 18), symptomIds = setOf("nausea"))
        )
        val result = SymptomPatternCalculator.calculate(cycles, entries)
        assertEquals(2, result.size)

        val nausea = result.first { it.symptomId == "nausea" }
        assertEquals(3, nausea.totalOccurrences)
        assertEquals(2, nausea.cycleCount)
        assertEquals(3, nausea.phaseBreakdown[CyclePhase.LUTEAL])
        assertEquals(CyclePhase.LUTEAL, nausea.mostFrequentPhase)

        val bloating = result.first { it.symptomId == "bloating" }
        assertEquals(1, bloating.totalOccurrences)
        assertEquals(1, bloating.cycleCount)
        assertEquals(1, bloating.phaseBreakdown[CyclePhase.LUTEAL])
        assertNull(bloating.mostFrequentPhase)
    }

    @Test
    fun `tied phase counts leave mostFrequentPhase null instead of breaking ties arbitrarily`() {
        val cycles = listOf(
            Cycle(id = "c1", startDate = LocalDate.of(2026, 1, 1), endDate = LocalDate.of(2026, 1, 28)),
            Cycle(id = "c2", startDate = LocalDate.of(2026, 1, 29), endDate = LocalDate.of(2026, 2, 25))
        )
        val entries = listOf(
            DailyEntry(date = LocalDate.of(2026, 1, 2), bleedingIntensity = BleedingIntensity.MEDIUM, symptomIds = setOf("fatigue")),
            DailyEntry(date = LocalDate.of(2026, 1, 20), symptomIds = setOf("fatigue")),
            DailyEntry(date = LocalDate.of(2026, 1, 30), bleedingIntensity = BleedingIntensity.MEDIUM, symptomIds = setOf("fatigue")),
            DailyEntry(date = LocalDate.of(2026, 2, 18), symptomIds = setOf("fatigue"))
        )
        val result = SymptomPatternCalculator.calculate(cycles, entries)
        val fatigue = result.first()
        assertEquals(4, fatigue.totalOccurrences)
        assertEquals(2, fatigue.phaseBreakdown[CyclePhase.MENSTRUAL])
        assertEquals(2, fatigue.phaseBreakdown[CyclePhase.LUTEAL])
        assertNull(fatigue.mostFrequentPhase)
    }

    @Test
    fun `period flow overrides date-based phase to menstrual while spotting does not`() {
        val cycles = listOf(
            Cycle(id = "c1", startDate = LocalDate.of(2026, 1, 1), endDate = LocalDate.of(2026, 1, 28))
        )
        val entries = listOf(
            DailyEntry(
                date = LocalDate.of(2026, 1, 10),
                bleedingIntensity = BleedingIntensity.LIGHT,
                symptomIds = setOf("cramps")
            ),
            DailyEntry(
                date = LocalDate.of(2026, 1, 10),
                bleedingIntensity = BleedingIntensity.SPOTTING,
                symptomIds = setOf("bloating")
            )
        )
        val result = SymptomPatternCalculator.calculate(cycles, entries)
        val cramps = result.first { it.symptomId == "cramps" }
        assertEquals(1, cramps.phaseBreakdown[CyclePhase.MENSTRUAL])
        assertEquals(0, cramps.phaseBreakdown[CyclePhase.FOLLICULAR])

        val bloating = result.first { it.symptomId == "bloating" }
        assertEquals(0, bloating.phaseBreakdown[CyclePhase.MENSTRUAL])
        assertEquals(1, bloating.phaseBreakdown[CyclePhase.FOLLICULAR])
    }

    @Test
    fun `early cycle without recorded bleeding does not infer menstrual phase`() {
        val cycles = listOf(
            Cycle(id = "c1", startDate = LocalDate.of(2026, 1, 1), endDate = LocalDate.of(2026, 1, 28))
        )
        val entries = listOf(
            DailyEntry(date = LocalDate.of(2026, 1, 3), bleedingIntensity = null, symptomIds = setOf("fatigue"))
        )
        val result = SymptomPatternCalculator.calculate(cycles, entries)
        val fatigue = result.first()
        assertEquals(1, fatigue.totalOccurrences)
        assertEquals(0, fatigue.phaseBreakdown[CyclePhase.MENSTRUAL])
        assertEquals(0, fatigue.phaseBreakdown[CyclePhase.FOLLICULAR])
    }

    @Test
    fun `active cycle uses median completed cycle length to delay luteal phase`() {
        val cycles = listOf(
            Cycle(id = "c1", startDate = LocalDate.of(2026, 1, 1), endDate = LocalDate.of(2026, 2, 4)),
            Cycle(id = "c2", startDate = LocalDate.of(2026, 2, 5), endDate = LocalDate.of(2026, 3, 11)),
            Cycle(id = "c3", startDate = LocalDate.of(2026, 3, 12), endDate = null)
        )
        val entries = listOf(
            DailyEntry(date = LocalDate.of(2026, 3, 27), symptomIds = setOf("headache"))
        )
        val result = SymptomPatternCalculator.calculate(cycles, entries)
        assertEquals(1, result.size)
        val headache = result.first()
        assertEquals("headache", headache.symptomId)
        assertEquals(1, headache.phaseBreakdown[CyclePhase.FOLLICULAR])
        assertEquals(0, headache.phaseBreakdown[CyclePhase.LUTEAL])
    }

    @Test
    fun `active cycle without completed history leaves mid-cycle symptoms unclassified`() {
        val cycles = listOf(
            Cycle(id = "c1", startDate = LocalDate.of(2026, 1, 1), endDate = null)
        )
        val entries = listOf(
            DailyEntry(date = LocalDate.of(2026, 1, 18), symptomIds = setOf("fatigue"))
        )
        val result = SymptomPatternCalculator.calculate(cycles, entries)
        val fatigue = result.first()
        assertEquals(1, fatigue.totalOccurrences)
        assertEquals(0, fatigue.phaseBreakdown[CyclePhase.FOLLICULAR])
        assertEquals(0, fatigue.phaseBreakdown[CyclePhase.LUTEAL])
        assertNull(fatigue.mostFrequentPhase)
    }

    @Test
    fun `entry in inter-cycle gap stays unclassified rather than fabricating follicular`() {
        val cycles = listOf(
            Cycle(id = "c1", startDate = LocalDate.of(2026, 1, 1), endDate = LocalDate.of(2026, 1, 20)),
            Cycle(id = "c2", startDate = LocalDate.of(2026, 2, 1), endDate = LocalDate.of(2026, 2, 28))
        )
        val entries = listOf(
            DailyEntry(date = LocalDate.of(2026, 1, 25), symptomIds = setOf("bloating"))
        )
        val result = SymptomPatternCalculator.calculate(cycles, entries)
        val bloating = result.first()
        assertEquals(1, bloating.totalOccurrences)
        assertEquals(0, bloating.phaseBreakdown[CyclePhase.FOLLICULAR])
        assertNull(bloating.mostFrequentPhase)
    }

    @Test
    fun `active pill regimen suppresses follicular and luteal symptom attribution`() {
        val cycles = listOf(
            Cycle(id = "c1", startDate = LocalDate.of(2026, 1, 1), endDate = LocalDate.of(2026, 1, 28))
        )
        val entries = listOf(
            DailyEntry(date = LocalDate.of(2026, 1, 20), symptomIds = setOf("headache"))
        )
        val result = SymptomPatternCalculator.calculate(cycles, entries, isPillRegimenActive = true)
        val headache = result.first()
        assertEquals(1, headache.totalOccurrences)
        assertEquals(0, headache.phaseBreakdown[CyclePhase.LUTEAL])
        assertEquals(0, headache.phaseBreakdown[CyclePhase.FOLLICULAR])
    }
}
