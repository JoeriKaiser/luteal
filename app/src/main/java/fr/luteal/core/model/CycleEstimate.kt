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
     * Attenuated prior weight once three to five intervals are recorded. Real
     * history begins to carry signal, reducing reliance on the population prior.
     */
    private const val PRIOR_WEIGHT_MODERATE = 1.4

    /**
     * Attenuated prior weight once at least six intervals are recorded. Six
     * consistent intervals provide enough evidence to let the user's history
     * drive the estimate without penalizing regular cycles.
     */
    private const val PRIOR_WEIGHT_ESTABLISHED = 0.8

    /**
     * Prior weight once the recorded history shows persistent variability.
     *
     * A population prior describes a population. When this user's own record
     * contradicts it, the record is the better evidence, so the prior is
     * nearly withdrawn rather than allowed to shrink a genuinely wide history
     * back toward a typical one.
     */
    private const val PRIOR_WEIGHT_HIGH_VARIABILITY = 0.5

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

    /** Base half-width multiplier: approximately a 95% window under normality. */
    private const val WINDOW_Z = 1.96

    /**
     * Right-skew asymmetry factors derived from Li et al. 2023 (PMC10226714):
     * median cycle length 28 days, 5th-95th percentile 22-38 days (-6 early,
     * +10 late: 37.5% early / 62.5% late of total span). Applying 0.75 early
     * and 1.25 late preserves the total 95% coverage width while reflecting
     * real-world right-skewed cycle tails.
     */
    private const val EARLY_ASYMMETRY_FACTOR = 0.75
    private const val LATE_ASYMMETRY_FACTOR = 1.25

    /** Never claim a bound tighter than this on either side. */
    private const val MINIMUM_RANGE_RADIUS_DAYS = 3

    /**
     * Asymmetric upper caps preserving the 44-day maximum total span (2 * 22
     * from Li et al.'s top age-band SD of 11.19 days).
     */
    private const val MAXIMUM_EARLY_RADIUS_DAYS = 16
    private const val MAXIMUM_LATE_RADIUS_DAYS = 28

    /**
     * Interval lengths the estimate actually uses: start-to-start, skipping
     * excluded cycles, splitting at gaps above 90 days, last six in range.
     */
    fun recentIntervalLengths(cycles: List<Cycle>): List<Int> =
        recordedIntervalLengths(cycles).takeLast(RECENT_INTERVAL_WINDOW)

    /**
     * @param ageBand optional declared age band. Selects the variability prior
     *   and PCOS multiplier; see [AgeBand]. Null uses
     *   [AgeBand.UNDECLARED_VARIATION_SD_DAYS].
     * @param hasTimingContext whether the user has declared any
     *   [ContextGroup.TIMING] tracking context. Widens uncertainty via a
     *   variance floor; never moves the central date. Yields to observed
     *   regularity across 10 consecutive swing-free cycles.
     * @param declaredContexts full set of declared [TrackingContext] values.
     *   When [TrackingContext.PCOS] is present, applies the age-stratified
     *   relative PCOS multiplier from Mortimer et al. 2026 (PMC12915291).
     */
    fun evaluate(
        cycles: List<Cycle>,
        ageBand: AgeBand? = null,
        hasTimingContext: Boolean = false,
        declaredContexts: Set<TrackingContext> = emptySet()
    ): CycleEstimateResult {
        val sortedCycles = cycles.sortedBy { it.startDate }
        val starts = sortedCycles
            .map(Cycle::startDate)
            .distinct()

        if (starts.size < MINIMUM_INTERVALS + 1) return CycleEstimateResult.NeedsMoreHistory

        val lengths = recordedIntervalLengths(sortedCycles)
        if (lengths.size < MINIMUM_INTERVALS) return CycleEstimateResult.IntervalsOutOfRange

        val recentLengths = lengths.takeLast(RECENT_INTERVAL_WINDOW)
        val averageLength = recentLengths.average().roundToInt()

        // Deliberately evaluated over the wider STRAW window rather than the
        // six intervals used for the average, because persistence is defined
        // across ten cycles.
        val highVariability = hasPersistentVariability(lengths)
        val hasProvenRegularity = lengths.size >= PERSISTENCE_CYCLE_WINDOW &&
            !highVariability &&
            hasNoRecentSwings(lengths.takeLast(PERSISTENCE_CYCLE_WINDOW), VARIABILITY_SWING_DAYS)

        val effectiveHasTimingContext = hasTimingContext ||
            declaredContexts.any { it.group == ContextGroup.TIMING }
        val hasPcosContext = TrackingContext.PCOS in declaredContexts
        val priorSdDays = ageBand?.variationSdDays ?: AgeBand.UNDECLARED_VARIATION_SD_DAYS
        val pcosMultiplier = ageBand?.pcosSdMultiplier ?: AgeBand.UNDECLARED_PCOS_SD_MULTIPLIER

        val (earlyRadius, lateRadius) = rangeRadiiDays(
            lengths = recentLengths,
            highVariability = highVariability,
            hasTimingContext = effectiveHasTimingContext,
            hasPcosContext = hasPcosContext,
            priorSdDays = priorSdDays,
            pcosMultiplier = pcosMultiplier,
            hasProvenRegularity = hasProvenRegularity
        )
        val lastCycle = sortedCycles.last()
        val centralDate = lastCycle.startDate.plusDays(averageLength.toLong())
        val computedEarliest = centralDate.minusDays(earlyRadius.toLong())
        val earliestDate = maxOf(computedEarliest, earliestFloor(lastCycle))

        return CycleEstimateResult.Available(
            CycleEstimate(
                earliestDate = earliestDate,
                centralDate = centralDate,
                latestDate = centralDate.plusDays(lateRadius.toLong()),
                cycleCount = recentLengths.size,
                variabilityDays = recentLengths.maxOrNull()!! - recentLengths.minOrNull()!!
            )
        )
    }

    fun estimateNextPeriod(
        cycles: List<Cycle>,
        ageBand: AgeBand? = null,
        hasTimingContext: Boolean = false,
        declaredContexts: Set<TrackingContext> = emptySet()
    ): CycleEstimate? =
        (evaluate(cycles, ageBand, hasTimingContext, declaredContexts) as? CycleEstimateResult.Available)?.estimate

    private fun recordedIntervalLengths(cycles: List<Cycle>): List<Int> {
        val sorted = cycles.sortedBy { it.startDate }
        val lengths = mutableListOf<Int>()
        for (i in 0 until sorted.size - 1) {
            val curr = sorted[i]
            val next = sorted[i + 1]
            val days = ChronoUnit.DAYS.between(curr.startDate, next.startDate).toInt()
            if (days > MAXIMUM_CYCLE_DAYS) {
                lengths.clear()
                continue
            }
            if (curr.isExcludedFromEstimates) continue
            if (days in MINIMUM_CYCLE_DAYS..MAXIMUM_CYCLE_DAYS) {
                lengths.add(days)
            }
        }
        return lengths
    }

    private fun earliestFloor(cycle: Cycle): LocalDate {
        val afterBleeding = cycle.periodDays
            .filter { it.bleedingIntensity.isPeriodFlow() }
            .maxOfOrNull { it.date }
            ?.plusDays(1)
        return if (afterBleeding != null && afterBleeding.isAfter(cycle.startDate)) {
            afterBleeding
        } else {
            cycle.startDate
        }
    }

    /**
     * Asymmetric early and late radii for the estimated next-period window.
     *
     * Shrinks the sample variance towards the population prior, enforces a
     * variance floor when a timing context (or age-stratified PCOS multiplier)
     * is declared without 10-cycle proven regularity, then splits the ~95%
     * window asymmetrically (0.75 early / 1.25 late) to reflect right-skewed
     * cycle length distributions.
     */
    private fun rangeRadiiDays(
        lengths: List<Int>,
        highVariability: Boolean,
        hasTimingContext: Boolean,
        hasPcosContext: Boolean,
        priorSdDays: Double,
        pcosMultiplier: Double,
        hasProvenRegularity: Boolean = false
    ): Pair<Int, Int> {
        val n = lengths.size
        val priorVariance = priorSdDays * priorSdDays
        val shrunkVariance = if (n < 2) {
            priorVariance
        } else {
            val mean = lengths.average()
            val sampleVariance = lengths.sumOf { (it - mean) * (it - mean) } / (n - 1)
            val priorWeight = when {
                highVariability -> PRIOR_WEIGHT_HIGH_VARIABILITY
                lengths.size >= 6 -> PRIOR_WEIGHT_ESTABLISHED
                lengths.size >= 3 -> PRIOR_WEIGHT_MODERATE
                else -> PRIOR_WEIGHT
            }
            val sampleWeight = (n - 1).toDouble()
            (sampleWeight * sampleVariance + priorWeight * priorVariance) /
                (sampleWeight + priorWeight)
        }

        val flooredVariance = when {
            hasPcosContext && !hasProvenRegularity -> {
                val pcosSd = priorSdDays * pcosMultiplier
                max(shrunkVariance, pcosSd * pcosSd)
            }
            hasTimingContext && !hasProvenRegularity -> {
                max(shrunkVariance, priorVariance)
            }
            else -> shrunkVariance
        }

        val baseHalfWidth = WINDOW_Z * sqrt(flooredVariance)
        val earlyRadius = ceil(baseHalfWidth * EARLY_ASYMMETRY_FACTOR)
            .toInt()
            .coerceIn(MINIMUM_RANGE_RADIUS_DAYS, MAXIMUM_EARLY_RADIUS_DAYS)
        val lateRadius = ceil(baseHalfWidth * LATE_ASYMMETRY_FACTOR)
            .toInt()
            .coerceIn(MINIMUM_RANGE_RADIUS_DAYS, MAXIMUM_LATE_RADIUS_DAYS)
        return earlyRadius to lateRadius
    }

    /**
     * Whether all consecutive cycle differences in [lengths] stay strictly
     * below [thresholdDays]. Used to verify longitudinal stability across
     * [PERSISTENCE_CYCLE_WINDOW] intervals before trusting regular history
     * over a declared timing context.
     */
    private fun hasNoRecentSwings(lengths: List<Int>, thresholdDays: Int): Boolean {
        for (i in 0 until lengths.size - 1) {
            if (abs(lengths[i + 1] - lengths[i]) >= thresholdDays) {
                return false
            }
        }
        return true
    }

    /**
     * Whether recorded history shows the STRAW+10 variability pattern: a swing
     * of seven days or more between consecutive cycle lengths, occurring more
     * than once inside a ten-cycle window.
     *
     * A spike and return (+N then -N) is one unusual month, not two. Requiring
     * two separate excursions is what makes this a property of the cycles
     * rather than of one event.
     */
    private fun hasPersistentVariability(lengths: List<Int>): Boolean {
        val diffs = lengths
            .takeLast(PERSISTENCE_CYCLE_WINDOW)
            .zipWithNext { previous, next -> next - previous }
        var excursions = 0
        var i = 0
        while (i < diffs.size) {
            if (abs(diffs[i]) >= VARIABILITY_SWING_DAYS) {
                excursions++
                val next = diffs.getOrNull(i + 1)
                if (next != null &&
                    abs(next) >= VARIABILITY_SWING_DAYS &&
                    diffs[i] > 0 != next > 0
                ) {
                    i += 2
                    continue
                }
            }
            i++
        }
        return excursions >= PERSISTENCE_MIN_OCCURRENCES
    }
}
