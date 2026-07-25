package fr.luteal.core.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class CycleEstimate(
    val earliestDate: LocalDate,
    val centralDate: LocalDate,
    val latestDate: LocalDate,
    val cycleCount: Int,
    val variabilityDays: Int
)

/**
 * Why an estimate is or is not available. The distinction matters: a user with
 * consistently long cycles has recorded plenty of history, and telling them
 * they have not is false.
 */
sealed interface CycleEstimateResult {
    data class Available(val estimate: CycleEstimate) : CycleEstimateResult

    /** Fewer than two recorded cycle starts. */
    data object NeedsMoreHistory : CycleEstimateResult

    /**
     * Cycle starts exist, but no interval between them falls inside the range
     * this calculator can model. Common with SOPK, perimenopause, or long
     * gaps in recording.
     */
    data object IntervalsOutOfRange : CycleEstimateResult
}

object CycleEstimateCalculator {
    private const val MINIMUM_INTERVALS = 1
    private const val MINIMUM_CYCLE_DAYS = 15
    private const val MAXIMUM_CYCLE_DAYS = 90
    private const val RECENT_INTERVAL_WINDOW = 6

    /**
     * Cycle lengths this calculator treats as plausible. Exposed so that
     * summary statistics elsewhere in the UI apply the same filter and cannot
     * contradict the estimate shown beside them.
     */
    val plausibleCycleDays: IntRange = MINIMUM_CYCLE_DAYS..MAXIMUM_CYCLE_DAYS

    /**
     * Population prior for within-person cycle length variation, in days.
     *
     * Sourced from real-world tracker data rather than the textbook 28-day
     * model: mean per-user variation (one SD of that user's own cycle lengths)
     * is 2.6 days across 612,613 cycles.
     * See docs/research/SOURCE_REGISTER.md (Bull et al., npj Digital Medicine
     * 2019).
     */
    private const val POPULATION_VARIATION_SD_DAYS = 2.6

    /**
     * Weight of the population prior, expressed in pseudo-observations. With
     * one or two recorded intervals the sample SD carries almost no signal, so
     * the prior dominates and the window stays honestly wide. It washes out as
     * real history accumulates.
     */
    private const val PRIOR_WEIGHT = 2.0

    /** Half-width multiplier: approximately a 95% window under normality. */
    private const val WINDOW_Z = 1.96

    /** Never claim a window tighter than this, whatever the recorded history. */
    private const val MINIMUM_RANGE_RADIUS_DAYS = 3

    /** Beyond this the window stops being informative; keep it bounded. */
    private const val MAXIMUM_RANGE_RADIUS_DAYS = 14

    fun evaluate(cycles: List<Cycle>): CycleEstimateResult {
        val starts = cycles
            .map(Cycle::startDate)
            .distinct()
            .sorted()

        if (starts.size < MINIMUM_INTERVALS + 1) return CycleEstimateResult.NeedsMoreHistory

        val lengths = starts.zipWithNext { first, second ->
            ChronoUnit.DAYS.between(first, second).toInt()
        }.filter { it in MINIMUM_CYCLE_DAYS..MAXIMUM_CYCLE_DAYS }

        if (lengths.size < MINIMUM_INTERVALS) return CycleEstimateResult.IntervalsOutOfRange

        val recentLengths = lengths.takeLast(RECENT_INTERVAL_WINDOW)
        val averageLength = recentLengths.average().roundToInt()

        val rangeRadius = rangeRadiusDays(recentLengths)
        val centralDate = starts.last().plusDays(averageLength.toLong())

        return CycleEstimateResult.Available(
            CycleEstimate(
                earliestDate = centralDate.minusDays(rangeRadius.toLong()),
                centralDate = centralDate,
                latestDate = centralDate.plusDays(rangeRadius.toLong()),
                cycleCount = recentLengths.size,
                variabilityDays = recentLengths.maxOrNull()!! - recentLengths.minOrNull()!!
            )
        )
    }

    fun estimateNextPeriod(cycles: List<Cycle>): CycleEstimate? =
        (evaluate(cycles) as? CycleEstimateResult.Available)?.estimate

    /**
     * Half-width of the estimated window.
     *
     * The previous implementation used `ceil(range / 2)`, which is not a
     * dispersion estimate: converting a range to an SD needs a factor that
     * depends on the sample size (about 1.13 at n=2, 2.53 at n=6). Dividing by
     * a constant 2 therefore understated uncertainty most severely when the
     * fewest cycles had been recorded, which is exactly when it is highest.
     *
     * Instead: shrink the sample variance towards the population prior, then
     * take a ~95% window. Range is also outlier-fragile, and a single mistyped
     * cycle start should not dominate the window.
     */
    private fun rangeRadiusDays(lengths: List<Int>): Int {
        val n = lengths.size
        val sampleVariance = if (n < 2) {
            0.0
        } else {
            val mean = lengths.average()
            lengths.sumOf { (it - mean) * (it - mean) } / (n - 1)
        }

        val priorVariance = POPULATION_VARIATION_SD_DAYS * POPULATION_VARIATION_SD_DAYS
        val shrunkVariance =
            (n * sampleVariance + PRIOR_WEIGHT * priorVariance) / (n + PRIOR_WEIGHT)

        val radius = ceil(WINDOW_Z * sqrt(shrunkVariance)).toInt()
        return radius.coerceIn(MINIMUM_RANGE_RADIUS_DAYS, MAXIMUM_RANGE_RADIUS_DAYS)
    }
}
