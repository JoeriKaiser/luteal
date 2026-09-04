package fr.luteal.core.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class SymptomPattern(
    val symptomId: String,
    val totalOccurrences: Int,
    val cycleCount: Int,
    val phaseBreakdown: Map<CyclePhase, Int>,
    val mostFrequentPhase: CyclePhase?
)

/**
 * Pure, deterministic calculator aggregating user-recorded symptoms across
 * cycle phases. Purely descriptive: provides factual occurrence counts and
 * phase distribution without making clinical inferences, screening, or predictions.
 */
object SymptomPatternCalculator {

    fun calculate(cycles: List<Cycle>, entries: List<DailyEntry>): List<SymptomPattern> {
        val entriesWithSymptoms = entries.filter { it.symptomIds.isNotEmpty() }
        if (entriesWithSymptoms.isEmpty()) return emptyList()

        val sortedCycles = cycles.sortedBy { it.startDate }

        class SymptomAccumulator {
            var totalOccurrences = 0
            val cycleIds = mutableSetOf<String>()
            val phaseCounts = mutableMapOf<CyclePhase, Int>().apply {
                CyclePhase.entries.forEach { this[it] = 0 }
            }
        }

        val accumulators = mutableMapOf<String, SymptomAccumulator>()

        for (entry in entriesWithSymptoms) {
            val (cycle, phase) = resolveCycleAndPhase(entry, sortedCycles)
            val cycleIdentifier = cycle?.id ?: "unassigned_${entry.date.year}_${entry.date.monthValue}"

            for (symptomId in entry.symptomIds) {
                val acc = accumulators.getOrPut(symptomId) { SymptomAccumulator() }
                acc.totalOccurrences++
                acc.cycleIds.add(cycleIdentifier)
                acc.phaseCounts[phase] = (acc.phaseCounts[phase] ?: 0) + 1
            }
        }

        return accumulators.map { (symptomId, acc) ->
            val maxPhaseCount = acc.phaseCounts.values.maxOrNull() ?: 0
            val topPhase = if (maxPhaseCount > 0) {
                acc.phaseCounts.entries.firstOrNull { it.value == maxPhaseCount }?.key
            } else {
                null
            }
            SymptomPattern(
                symptomId = symptomId,
                totalOccurrences = acc.totalOccurrences,
                cycleCount = acc.cycleIds.size,
                phaseBreakdown = acc.phaseCounts.toMap(),
                mostFrequentPhase = topPhase
            )
        }.sortedWith(
            compareByDescending<SymptomPattern> { it.totalOccurrences }
                .thenBy { it.symptomId }
        )
    }

    private fun resolveCycleAndPhase(
        entry: DailyEntry,
        sortedCycles: List<Cycle>
    ): Pair<Cycle?, CyclePhase> {
        // Bleeding overrides phase to MENSTRUAL
        if (entry.bleedingIntensity != null && entry.bleedingIntensity != BleedingIntensity.NONE) {
            val matchingCycle = sortedCycles.lastOrNull { !it.startDate.isAfter(entry.date) }
            return matchingCycle to CyclePhase.MENSTRUAL
        }

        // Find cycle containing this date
        val cycleIndex = sortedCycles.indexOfLast { !it.startDate.isAfter(entry.date) }
        if (cycleIndex == -1) {
            return null to CyclePhase.FOLLICULAR
        }

        val cycle = sortedCycles[cycleIndex]
        val nextCycle = sortedCycles.getOrNull(cycleIndex + 1)
        if (nextCycle != null && !entry.date.isBefore(nextCycle.startDate)) {
            return null to CyclePhase.FOLLICULAR
        }

        val cycleLength = when {
            cycle.endDate != null -> ChronoUnit.DAYS.between(cycle.startDate, cycle.endDate).toInt() + 1
            nextCycle != null -> ChronoUnit.DAYS.between(cycle.startDate, nextCycle.startDate).toInt()
            else -> 28
        }.coerceAtLeast(10)

        val dayIndex = ChronoUnit.DAYS.between(cycle.startDate, entry.date).toInt()

        val lutealStart = maxOf(6, cycleLength - 14)
        val ovulatoryStart = maxOf(5, lutealStart - 2)

        val phase = when {
            dayIndex < 5 -> CyclePhase.MENSTRUAL
            dayIndex in ovulatoryStart until lutealStart -> CyclePhase.OVULATORY
            dayIndex >= lutealStart -> CyclePhase.LUTEAL
            else -> CyclePhase.FOLLICULAR
        }

        return cycle to phase
    }
}
