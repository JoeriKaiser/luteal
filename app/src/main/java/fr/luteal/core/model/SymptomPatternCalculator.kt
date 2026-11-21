package fr.luteal.core.model

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
 * phase distribution without making clinical inferences, screening, or
 * predictions.
 *
 * Aligns with [CurrentCyclePhaseCalculator]:
 * - Menstruation is recorded (period flow or cycle start day without explicit
 *   non-period flow), never inferred from early cycle days without bleeding.
 * - Spotting does not classify a day as menstrual.
 * - Unmatched dates, transition days around `cycleLength - 13 +/- 2`, open
 *   cycles without completed history, and non-menstrual days under active
 *   hormonal contraception stay unclassified rather than being forced into
 *   follicular or ovulatory buckets.
 * - [SymptomPattern.mostFrequentPhase] requires at least 3 total occurrences
 *   across at least 2 cycles and a strict unique maximum (no arbitrary tie
 *   breaking).
 */
object SymptomPatternCalculator {
    private const val EARLY_CYCLE_DAYS = 7
    private const val LUTEAL_ANCHOR_DAYS = 13
    private const val OVULATION_TRANSITION_RADIUS_DAYS = 2
    private const val MIN_PATTERN_OCCURRENCES = 3
    private const val MIN_PATTERN_CYCLES = 2

    fun calculate(
        cycles: List<Cycle>,
        entries: List<DailyEntry>,
        isPillRegimenActive: Boolean = false
    ): List<SymptomPattern> {
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
            val (cycle, phase) = resolveCycleAndPhase(entry, sortedCycles, isPillRegimenActive)
            val cycleIdentifier = cycle?.id ?: "unassigned_${entry.date.year}_${entry.date.monthValue}"

            for (symptomId in entry.symptomIds) {
                val acc = accumulators.getOrPut(symptomId) { SymptomAccumulator() }
                acc.totalOccurrences++
                acc.cycleIds.add(cycleIdentifier)
                if (phase != null) {
                    acc.phaseCounts[phase] = (acc.phaseCounts[phase] ?: 0) + 1
                }
            }
        }

        return accumulators.map { (symptomId, acc) ->
            val maxPhaseCount = acc.phaseCounts.values.maxOrNull() ?: 0
            val hasUniqueMaximum = maxPhaseCount > 0 &&
                acc.phaseCounts.values.count { it == maxPhaseCount } == 1
            val topPhase = if (
                acc.totalOccurrences >= MIN_PATTERN_OCCURRENCES &&
                acc.cycleIds.size >= MIN_PATTERN_CYCLES &&
                hasUniqueMaximum
            ) {
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
        sortedCycles: List<Cycle>,
        isPillRegimenActive: Boolean
    ): Pair<Cycle?, CyclePhase?> {
        val cycleIndex = sortedCycles.indexOfLast { !it.startDate.isAfter(entry.date) }
        val matchingCycle = sortedCycles.getOrNull(cycleIndex)?.takeIf { cycle ->
            cycle.endDate == null || !entry.date.isAfter(cycle.endDate)
        }

        // Recorded period flow (light/medium/heavy, never spotting) is menstrual.
        if (entry.bleedingIntensity?.isPeriodFlow() == true) {
            return matchingCycle to CyclePhase.MENSTRUAL
        }

        val cycle = matchingCycle ?: return null to null
        val dayIndex = ChronoUnit.DAYS.between(cycle.startDate, entry.date).toInt()
        val isExplicitNonPeriodFlow = entry.bleedingIntensity == BleedingIntensity.NONE ||
            entry.bleedingIntensity == BleedingIntensity.SPOTTING
        if (dayIndex == 0 && !isExplicitNonPeriodFlow) {
            return cycle to CyclePhase.MENSTRUAL
        }

        if (isPillRegimenActive) {
            return cycle to null
        }

        if (dayIndex < EARLY_CYCLE_DAYS) {
            return cycle to null
        }

        val nextCycle = sortedCycles.getOrNull(cycleIndex + 1)
        val completedLengths = sortedCycles
            .filter {
                it.endDate != null &&
                    !it.isExcludedFromEstimates &&
                    it.lengthInDays in CycleEstimateCalculator.plausibleCycleDays
            }
            .map { it.lengthInDays }
        val typicalCycleLength = if (completedLengths.isNotEmpty()) {
            val sorted = completedLengths.sorted()
            sorted[sorted.size / 2]
        } else {
            null
        }

        val lengthOrNull = when {
            cycle.endDate != null -> ChronoUnit.DAYS.between(cycle.startDate, cycle.endDate).toInt() + 1
            nextCycle != null -> ChronoUnit.DAYS.between(cycle.startDate, nextCycle.startDate).toInt()
            else -> typicalCycleLength
        }

        if (lengthOrNull == null || lengthOrNull !in CycleEstimateCalculator.plausibleCycleDays) {
            return cycle to null
        }

        val ovulationCentral = lengthOrNull - LUTEAL_ANCHOR_DAYS
        val ovulationEarliest = maxOf(EARLY_CYCLE_DAYS, ovulationCentral - OVULATION_TRANSITION_RADIUS_DAYS)
        val ovulationLatest = maxOf(ovulationEarliest, ovulationCentral + OVULATION_TRANSITION_RADIUS_DAYS)

        val phase = when {
            dayIndex < ovulationEarliest -> CyclePhase.FOLLICULAR
            dayIndex > ovulationLatest -> CyclePhase.LUTEAL
            else -> null
        }
        return cycle to phase
    }
}
