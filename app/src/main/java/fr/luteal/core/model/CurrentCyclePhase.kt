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
    ESTIMATE_EXPIRED
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
 * period, not a phase cutoff: luteal runs from after the ovulation band until
 * two days before `centralDate`, or until recorded period flow.
 *
 * Calendar data cannot confirm ovulation, so the ovulatory label is limited to
 * the central low-confidence date after a strict history gate; the surrounding
 * estimate remains indeterminate.
 *
 * Research basis is recorded in `docs/research/SOURCE_REGISTER.md`: Mihm et al.
 * (phase physiology), Fehring et al. (phase variability), and NHS Periods.
 */
object CurrentCyclePhaseCalculator {
    /** NHS: periods generally last 2 to 7 days. Missing data in this span stays unknown. */
    private const val EARLY_CYCLE_DAYS = 7

    /** Canonical pre-E2EE cyclecalc anchor, based on the 12 to 14 day luteal range. */
    private const val LUTEAL_ANCHOR_DAYS = 13L

    /** Fehring variability margin around the ovulation anchor. */
    private const val OVULATION_EXTRA_RADIUS_DAYS = 2L

    /** Estimated luteal ends this many days before `centralDate`. */
    private const val PRE_PERIOD_LEAD_DAYS = 2L

    /** Former canonical moderate-confidence gate; ovulation itself remains low confidence. */
    private const val STABLE_HISTORY_INTERVALS = 6
    private const val STABLE_HISTORY_VARIABILITY_DAYS = 7

    fun evaluate(
        today: LocalDate,
        currentCycle: Cycle?,
        todayEntry: DailyEntry?,
        estimateResult: CycleEstimateResult
    ): CurrentCyclePhase {
        val cycle = currentCycle
            ?.takeIf { !today.isBefore(it.startDate) }
            ?: return CurrentCyclePhase.Indeterminate(
                PhaseIndeterminateReason.NO_CURRENT_CYCLE
            )

        val dayIndex = ChronoUnit.DAYS.between(cycle.startDate, today).toInt()
        val canonicalPeriodDay = cycle.periodDays.firstOrNull { it.date == today }
        val observedFlow = canonicalPeriodDay?.bleedingIntensity ?: todayEntry?.bleedingIntensity

        if (dayIndex == 0 || observedFlow?.isPeriodFlow() == true) {
            return CurrentCyclePhase.Available(CyclePhase.MENSTRUAL, PhaseCertainty.RECORDED)
        }

        if (dayIndex < EARLY_CYCLE_DAYS) {
            if (observedFlow == null || observedFlow == BleedingIntensity.SPOTTING) {
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

        val follicularOpensOn = cycle.startDate.plusDays(EARLY_CYCLE_DAYS.toLong())
        val ovulationCentral = estimate.centralDate.minusDays(LUTEAL_ANCHOR_DAYS)
        val ovulationEarliest = maxOf(
            ovulationCentral.minusDays(OVULATION_EXTRA_RADIUS_DAYS),
            follicularOpensOn
        )
        val ovulationLatest = maxOf(
            ovulationCentral.plusDays(OVULATION_EXTRA_RADIUS_DAYS),
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
