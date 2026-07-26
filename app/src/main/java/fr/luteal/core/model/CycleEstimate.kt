package fr.luteal.core.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
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
     * Weight of the population prior, expressed in pseudo-observations. With
     * one or two recorded intervals the sample SD carries almost no signal, so
     * the prior dominates and the window stays honestly wide. It washes out as
     * real history accumulates.
     */
    private const val PRIOR_WEIGHT = 2.0

    /**
     * Prior weight once the recorded history shows persistent variability.
     *
     * A population prior describes a population. When this user's own record
     * contradicts it, the record is the better evidence, so the prior is
     * nearly withdrawn rather than allowed to shrink a genuinely wide history
     * back toward a typical one.
     */
    private const val PRIOR_WEIGHT_HIGH_VARIABILITY = 0.5

    // A declared timing context is handled as a floor on the variance rather
    // than a change to the prior's weight. Lowering the weight only widens the
    // window when the user's own variance already exceeds the prior; someone
    // who declares SOPK but has recorded three regular cycles would have got a
    // *narrower* window than someone who declared nothing, which inverts the
    // intent. The floor also needs no per-condition standard deviation, and no
    // usable one exists: the available PCOS figure is a rate of self-reported
    // irregularity, not a dispersion.
    // See docs/research/CONDITION_CYCLE_IMPACTS.md, Findings 1 and 6.

    /**
     * STRAW+10 threshold for increased cycle variability: "a persistent
     * difference of 7 days or more in the length of consecutive cycles", where
     * persistence is "recurrence within 10 cycles".
     *
     * This is used only to decide how far to trust the population prior for
     * this user. It must never be surfaced. The criterion comes from a
     * reproductive-aging staging framework, and telling someone they meet it
     * would be staging them, which is diagnosis by another name.
     *
     * See docs/research/CONDITION_CYCLE_IMPACTS.md, Finding 4.
     */
    private const val VARIABILITY_SWING_DAYS = 7
    private const val PERSISTENCE_CYCLE_WINDOW = 10
    private const val PERSISTENCE_MIN_OCCURRENCES = 2

    /** Half-width multiplier: approximately a 95% window under normality. */
    private const val WINDOW_Z = 1.96

    /** Never claim a window tighter than this, whatever the recorded history. */
    private const val MINIMUM_RANGE_RADIUS_DAYS = 3

    /**
     * Beyond this the window stops being informative; keep it bounded.
     *
     * 22 days is roughly the 95% half-width implied by the largest
     * within-person SD in the Apple study (11.19 days above age 50). The
     * previous cap of 14 truncated real uncertainty for precisely the users
     * who carry the most of it, which reported more precision than the
     * recorded history supported.
     */
    private const val MAXIMUM_RANGE_RADIUS_DAYS = 22

    /**
     * @param ageBand optional declared age band. Selects the variability prior;
     *   see [AgeBand]. Null uses [AgeBand.UNDECLARED_VARIATION_SD_DAYS].
     * @param hasTimingContext whether the user has declared any
     *   [ContextGroup.TIMING] tracking context. Widens the window by trusting
     *   the population prior less; it never moves the central date.
     */
    fun evaluate(
        cycles: List<Cycle>,
        ageBand: AgeBand? = null,
        hasTimingContext: Boolean = false
    ): CycleEstimateResult {
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

        // Deliberately evaluated over the wider STRAW window rather than the
        // six intervals used for the average, because persistence is defined
        // across ten cycles.
        val highVariability = hasPersistentVariability(lengths)
        val rangeRadius = rangeRadiusDays(
            lengths = recentLengths,
            highVariability = highVariability,
            hasTimingContext = hasTimingContext,
            priorSdDays = ageBand?.variationSdDays ?: AgeBand.UNDECLARED_VARIATION_SD_DAYS
        )
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

    fun estimateNextPeriod(
        cycles: List<Cycle>,
        ageBand: AgeBand? = null,
        hasTimingContext: Boolean = false
    ): CycleEstimate? =
        (evaluate(cycles, ageBand, hasTimingContext) as? CycleEstimateResult.Available)?.estimate

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
    private fun rangeRadiusDays(
        lengths: List<Int>,
        highVariability: Boolean,
        hasTimingContext: Boolean,
        priorSdDays: Double
    ): Int {
        val n = lengths.size
        val sampleVariance = if (n < 2) {
            0.0
        } else {
            val mean = lengths.average()
            lengths.sumOf { (it - mean) * (it - mean) } / (n - 1)
        }

        val priorWeight = if (highVariability) {
            PRIOR_WEIGHT_HIGH_VARIABILITY
        } else {
            PRIOR_WEIGHT
        }

        val priorVariance = priorSdDays * priorSdDays
        val shrunkVariance =
            (n * sampleVariance + priorWeight * priorVariance) / (n + priorWeight)

        // A declared timing context guarantees at least population-level
        // uncertainty. If the user's own cycles are more variable than that,
        // their record wins. This can only ever widen, never tighten.
        val flooredVariance = if (hasTimingContext) {
            max(shrunkVariance, priorVariance)
        } else {
            shrunkVariance
        }

        val radius = ceil(WINDOW_Z * sqrt(flooredVariance)).toInt()
        return radius.coerceIn(MINIMUM_RANGE_RADIUS_DAYS, MAXIMUM_RANGE_RADIUS_DAYS)
    }

    /**
     * Whether recorded history shows the STRAW+10 variability pattern: a swing
     * of seven days or more between consecutive cycle lengths, occurring more
     * than once inside a ten-cycle window.
     *
     * A single seven-day swing is one unusual month. Requiring recurrence is
     * what makes it a property of the user's cycles rather than of one event.
     */
    private fun hasPersistentVariability(lengths: List<Int>): Boolean {
        // Ten cycle lengths yield nine consecutive-pair differences.
        val swings = lengths
            .takeLast(PERSISTENCE_CYCLE_WINDOW)
            .zipWithNext { previous, next -> abs(next - previous) }

        return swings.count { it >= VARIABILITY_SWING_DAYS } >= PERSISTENCE_MIN_OCCURRENCES
    }
}
