package fr.luteal.core.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Whether a phase comes from a recorded observation or a calculation. */
enum class PhaseCertainty {
    RECORDED,
    ESTIMATED
}

/** Why Luteal declines to name a current phase. */
enum class PhaseIndeterminateReason {
    NO_CURRENT_CYCLE,
    EARLY_CYCLE_WITHOUT_BLEEDING_DETAIL,
    NEEDS_MORE_HISTORY,
    INTERVALS_OUT_OF_RANGE,
    PHASE_TRANSITION,
    NEXT_PERIOD_WINDOW,
    ESTIMATE_EXPIRED,
    ESTIMATE_NOT_SHARED,
    HORMONAL_CONTRACEPTION
}

sealed interface CurrentCyclePhase {
    data class Available(
        val phase: CyclePhase,
        val certainty: PhaseCertainty
    ) : CurrentCyclePhase

    data class Indeterminate(
        val reason: PhaseIndeterminateReason
    ) : CurrentCyclePhase
}

/**
 * Derives a conservative current phase from recorded bleeding and the existing
 * next-period estimate.
 *
 * Menstruation is recorded, never inferred from an average duration. The other
 * phases are estimates. `earliestDate` is an uncertainty bound on the next
 * period, not a hard phase cutoff: luteal runs from after the ovulation
 * transition band until two days before `centralDate`, or until recorded period
 * flow. When next-period uncertainty grows wide, the ovulation transition band
 * widens proportionally, and extreme uncertainty (`lateRadius > 14` days)
 * suppresses estimated follicular and luteal labels altogether.
 *
 * Calendar data cannot confirm ovulation, so the ovulatory label is limited to
 * the central low-confidence date after a strict history gate; the surrounding
 * estimate remains indeterminate.
 *
 * Research basis is recorded in `docs/research/SOURCE_REGISTER.md`: Mihm et al.
 * (phase physiology), Soumpasis et al. (luteal distribution), Fehring et al.
 * (phase variability), and NHS Periods.
 */
object CurrentCyclePhaseCalculator {
    /** NHS: periods generally last 2 to 7 days. Missing data in this span stays unknown. */
    private const val EARLY_CYCLE_DAYS = 7

    /** Backward luteal anchor supported by Bull 2019 (12.4d), Soumpasis 2020 (12.77d), and Mihm 2011 (12-14d). */
    private const val LUTEAL_ANCHOR_DAYS = 13L

    /** Minimum Fehring variability margin around the ovulation anchor. */
    private const val MIN_OVULATION_EXTRA_RADIUS_DAYS = 2L

    /** Maximum scaled ovulation transition margin before high-uncertainty suppression kicks in. */
    private const val MAX_OVULATION_EXTRA_RADIUS_DAYS = 5L

    /**
     * When the late next-period uncertainty exceeds two weeks (for example age
     * 50+, strong PCOS uncertainty, or severe STRAW+10 swings), decline to name
     * follicular or luteal phases rather than presenting false precision.
     */
    private const val MAX_PHASE_LABEL_LATE_RADIUS_DAYS = 14L

    /** Estimated luteal ends this many days before `centralDate`. */
    private const val PRE_PERIOD_LEAD_DAYS = 2L

    /** Former canonical moderate-confidence gate; ovulation itself remains low confidence. */
    private const val STABLE_HISTORY_INTERVALS = 6
    private const val STABLE_HISTORY_VARIABILITY_DAYS = 7

    fun evaluate(
        today: LocalDate,
        currentCycle: Cycle?,
        todayEntry: DailyEntry?,
        estimateResult: CycleEstimateResult,
        isPillRegimenActive: Boolean = false
    ): CurrentCyclePhase {
        val cycle = currentCycle
            ?.takeIf { !today.isBefore(it.startDate) }
            ?: return CurrentCyclePhase.Indeterminate(
                PhaseIndeterminateReason.NO_CURRENT_CYCLE
            )

        val dayIndex = ChronoUnit.DAYS.between(cycle.startDate, today).toInt()
        val canonicalPeriodDay = cycle.periodDays.firstOrNull { it.date == today }
        val observedFlow = canonicalPeriodDay?.bleedingIntensity ?: todayEntry?.bleedingIntensity

        val isExplicitNonPeriodFlow = observedFlow == BleedingIntensity.NONE || observedFlow == BleedingIntensity.SPOTTING
        if ((dayIndex == 0 && !isExplicitNonPeriodFlow) || observedFlow?.isPeriodFlow() == true) {
            return CurrentCyclePhase.Available(CyclePhase.MENSTRUAL, PhaseCertainty.RECORDED)
        }

        if (isPillRegimenActive) {
            return CurrentCyclePhase.Indeterminate(
                PhaseIndeterminateReason.HORMONAL_CONTRACEPTION
            )
        }

        if (dayIndex < EARLY_CYCLE_DAYS) {
            if (observedFlow == null || isExplicitNonPeriodFlow) {
                return CurrentCyclePhase.Indeterminate(
                    PhaseIndeterminateReason.EARLY_CYCLE_WITHOUT_BLEEDING_DETAIL
                )
            }
        }

        val estimate = when (estimateResult) {
            CycleEstimateResult.NeedsMoreHistory -> return CurrentCyclePhase.Indeterminate(
                PhaseIndeterminateReason.NEEDS_MORE_HISTORY
            )
            CycleEstimateResult.IntervalsOutOfRange -> return CurrentCyclePhase.Indeterminate(
                PhaseIndeterminateReason.INTERVALS_OUT_OF_RANGE
            )
            is CycleEstimateResult.Available -> estimateResult.estimate
        }

        if (today.isAfter(estimate.latestDate)) {
            return CurrentCyclePhase.Indeterminate(PhaseIndeterminateReason.ESTIMATE_EXPIRED)
        }

        val periodWindowStart = estimate.centralDate.minusDays(PRE_PERIOD_LEAD_DAYS)
        if (!today.isBefore(periodWindowStart)) {
            return CurrentCyclePhase.Indeterminate(PhaseIndeterminateReason.NEXT_PERIOD_WINDOW)
        }

        val earlyRadius = ChronoUnit.DAYS.between(estimate.earliestDate, estimate.centralDate).coerceAtLeast(0L)
        val lateRadius = ChronoUnit.DAYS.between(estimate.centralDate, estimate.latestDate).coerceAtLeast(0L)
        if (lateRadius > MAX_PHASE_LABEL_LATE_RADIUS_DAYS) {
            return CurrentCyclePhase.Indeterminate(PhaseIndeterminateReason.PHASE_TRANSITION)
        }

        val effectiveHalfWidth = (earlyRadius + lateRadius) / 2L
        val ovulationExtraRadius = maxOf(
            MIN_OVULATION_EXTRA_RADIUS_DAYS,
            effectiveHalfWidth / 2L
        ).coerceAtMost(MAX_OVULATION_EXTRA_RADIUS_DAYS)

        val follicularOpensOn = cycle.startDate.plusDays(EARLY_CYCLE_DAYS.toLong())
        val ovulationCentral = estimate.centralDate.minusDays(LUTEAL_ANCHOR_DAYS)
        val ovulationEarliest = maxOf(
            ovulationCentral.minusDays(ovulationExtraRadius),
            follicularOpensOn
        )
        val ovulationLatest = maxOf(
            ovulationCentral.plusDays(ovulationExtraRadius),
            ovulationEarliest
        )

        return when {
            today.isBefore(ovulationEarliest) -> CurrentCyclePhase.Available(
                CyclePhase.FOLLICULAR,
                PhaseCertainty.ESTIMATED
            )
            today.isAfter(ovulationLatest) -> CurrentCyclePhase.Available(
                CyclePhase.LUTEAL,
                PhaseCertainty.ESTIMATED
            )
            today == ovulationCentral && estimate.hasStableHistory() ->
                CurrentCyclePhase.Available(
                    CyclePhase.OVULATORY,
                    PhaseCertainty.ESTIMATED
                )
            else -> CurrentCyclePhase.Indeterminate(
                PhaseIndeterminateReason.PHASE_TRANSITION
            )
        }
    }

    private fun CycleEstimate.hasStableHistory(): Boolean =
        cycleCount >= STABLE_HISTORY_INTERVALS &&
            variabilityDays <= STABLE_HISTORY_VARIABILITY_DAYS
}
