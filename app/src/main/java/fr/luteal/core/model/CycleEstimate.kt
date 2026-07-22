package fr.luteal.core.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.roundToInt

data class CycleEstimate(
    val earliestDate: LocalDate,
    val centralDate: LocalDate,
    val latestDate: LocalDate,
    val cycleCount: Int,
    val variabilityDays: Int
)

object CycleEstimateCalculator {
    private const val MINIMUM_INTERVALS = 1
    private const val MINIMUM_CYCLE_DAYS = 15
    private const val MAXIMUM_CYCLE_DAYS = 90
    private const val MINIMUM_RANGE_RADIUS_DAYS = 2
    private const val SINGLE_INTERVAL_RADIUS_DAYS = 5

    fun estimateNextPeriod(cycles: List<Cycle>): CycleEstimate? {
        val starts = cycles
            .map(Cycle::startDate)
            .distinct()
            .sorted()

        if (starts.size < MINIMUM_INTERVALS + 1) return null

        val lengths = starts.zipWithNext { first, second ->
            ChronoUnit.DAYS.between(first, second).toInt()
        }.filter { it in MINIMUM_CYCLE_DAYS..MAXIMUM_CYCLE_DAYS }

        if (lengths.size < MINIMUM_INTERVALS) return null

        val recentLengths = lengths.takeLast(6)
        val averageLength = recentLengths.average().roundToInt()
        val variability = (recentLengths.maxOrNull()!! - recentLengths.minOrNull()!!)

        // A single interval carries no variability signal, so the normal
        // radius formula would understate uncertainty. Force a wide radius
        // to keep the window honest (S04, S09).
        val singleInterval = recentLengths.size == 1
        val rangeRadius = if (singleInterval) {
            SINGLE_INTERVAL_RADIUS_DAYS
        } else {
            maxOf(
                MINIMUM_RANGE_RADIUS_DAYS,
                ceil(variability / 2.0).toInt()
            )
        }
        val centralDate = starts.last().plusDays(averageLength.toLong())

        return CycleEstimate(
            earliestDate = centralDate.minusDays(rangeRadius.toLong()),
            centralDate = centralDate,
            latestDate = centralDate.plusDays(rangeRadius.toLong()),
            cycleCount = recentLengths.size,
            variabilityDays = variability
        )
    }
}
