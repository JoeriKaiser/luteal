package fr.luteal.core.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

data class LongitudinalCycleItem(
    val cycleId: String,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val lengthDays: Int,
    val bleedingDaysCount: Int,
    val isCurrent: Boolean,
    val isExcluded: Boolean,
    val exclusionReason: CycleExclusionReason?,
    val hasStrawSwing: Boolean
)

data class LongitudinalCycleStats(
    val totalCyclesCount: Int,
    val completedCyclesCount: Int,
    val excludedCyclesCount: Int,
    val rollingMedianDays: Double?,
    val rollingMeanDays: Double?,
    val items: List<LongitudinalCycleItem>
)

object LongitudinalCycleStatsCalculator {
    fun calculate(cycles: List<Cycle>): LongitudinalCycleStats {
        val sorted = cycles.sortedBy { it.startDate }
        val items = mutableListOf<LongitudinalCycleItem>()
        val completedLengths = mutableListOf<Int>()

        var prevCompletedLength: Int? = null

        for (i in sorted.indices) {
            val cycle = sorted[i]
            val nextStart = if (i < sorted.size - 1) sorted[i + 1].startDate else null
            val isCurrent = nextStart == null && cycle.endDate == null
            val length = if (nextStart != null) {
                ChronoUnit.DAYS.between(cycle.startDate, nextStart).toInt()
            } else if (cycle.endDate != null) {
                ChronoUnit.DAYS.between(cycle.startDate, cycle.endDate).toInt() + 1
            } else {
                ChronoUnit.DAYS.between(cycle.startDate, LocalDate.now()).toInt() + 1
            }

            val bleedingDays = cycle.periodDays.count { it.bleedingIntensity != BleedingIntensity.NONE }
            val hasStrawSwing = if (!isCurrent && prevCompletedLength != null) {
                abs(length - prevCompletedLength) >= 7
            } else {
                false
            }

            if (!isCurrent) {
                prevCompletedLength = length
                if (!cycle.isExcludedFromEstimates && length in CycleEstimateCalculator.plausibleCycleDays) {
                    completedLengths.add(length)
                }
            }

            items.add(
                LongitudinalCycleItem(
                    cycleId = cycle.id,
                    startDate = cycle.startDate,
                    endDate = cycle.endDate ?: nextStart?.minusDays(1),
                    lengthDays = length,
                    bleedingDaysCount = bleedingDays,
                    isCurrent = isCurrent,
                    isExcluded = cycle.isExcludedFromEstimates,
                    exclusionReason = cycle.exclusionReason,
                    hasStrawSwing = hasStrawSwing
                )
            )
        }

        val rollingMedian = if (completedLengths.isNotEmpty()) {
            val s = completedLengths.sorted()
            if (s.size % 2 == 1) {
                s[s.size / 2].toDouble()
            } else {
                (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
            }
        } else null

        val rollingMean = if (completedLengths.isNotEmpty()) {
            completedLengths.average()
        } else null

        return LongitudinalCycleStats(
            totalCyclesCount = items.size,
            completedCyclesCount = items.count { !it.isCurrent },
            excludedCyclesCount = items.count { it.isExcluded },
            rollingMedianDays = rollingMedian,
            rollingMeanDays = rollingMean,
            items = items.reversed() // Most recent first for display
        )
    }
}
