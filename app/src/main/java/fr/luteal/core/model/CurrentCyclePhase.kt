package fr.luteal.core.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max

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
 * phases are estimates and are returned only where the plausible date ranges
 * do not overlap. Calendar data cannot confirm ovulation, so the ovulatory
 * label is limited to the central low-confidence date after a strict history
 * gate; the surrounding estimate remains indeterminate.
 *
 * Research basis is recorded in `docs/research/SOURCE_REGISTER.md`: Mihm et al.
 * (phase physiology), Fehring et al. (phase variability), and NHS Periods.
 */
object CurrentCyclePhaseCalculator {
    /** NHS: periods generally last 2 to 7 days. Missing data in this span stays unknown. */
    private const val EARLY_CYCLE_DAYS = 7

    /** Canonical pre-E2EE cyclecalc anchor, based on the 12 to 14 day luteal range. */
    private const val LUTEAL_ANCHOR_DAYS = 13L

    /** Fehring variability margin used by the former canonical estimate engine. */
    private const val OVULATION_EXTRA_RADIUS_DAYS = 2L

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

        if (dayIndex == 0 || canonicalPeriodDay?.bleedingIntensity.isPeriodFlow()) {
            return CurrentCyclePhase.Available(CyclePhase.MENSTRUAL, PhaseCertainty.RECORDED)
        }

        if (dayIndex < EARLY_CYCLE_DAYS) {
            if (observedFlow.isPeriodFlow()) {
                return CurrentCyclePhase.Available(
                    CyclePhase.MENSTRUAL,
                    PhaseCertainty.RECORDED
                )
            }
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

        if (!today.isBefore(estimate.earliestDate)) {
            val reason = if (today.isAfter(estimate.latestDate)) {
                PhaseIndeterminateReason.ESTIMATE_EXPIRED
            } else {
                PhaseIndeterminateReason.NEXT_PERIOD_WINDOW
            }
            return CurrentCyclePhase.Indeterminate(reason)
        }

        val nextPeriodRadius = max(
            ChronoUnit.DAYS.between(estimate.earliestDate, estimate.centralDate),
            ChronoUnit.DAYS.between(estimate.centralDate, estimate.latestDate)
        )
        val ovulationCentral = estimate.centralDate.minusDays(LUTEAL_ANCHOR_DAYS)
        val ovulationRadius = nextPeriodRadius + OVULATION_EXTRA_RADIUS_DAYS
        val ovulationEarliest = ovulationCentral.minusDays(ovulationRadius)
        val ovulationLatest = ovulationCentral.plusDays(ovulationRadius)

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

    private fun BleedingIntensity?.isPeriodFlow(): Boolean = when (this) {
        BleedingIntensity.LIGHT,
        BleedingIntensity.MEDIUM,
        BleedingIntensity.HEAVY -> true
        BleedingIntensity.NONE,
        BleedingIntensity.SPOTTING,
        null -> false
    }

    private fun CycleEstimate.hasStableHistory(): Boolean =
        cycleCount >= STABLE_HISTORY_INTERVALS &&
            variabilityDays <= STABLE_HISTORY_VARIABILITY_DAYS
}
