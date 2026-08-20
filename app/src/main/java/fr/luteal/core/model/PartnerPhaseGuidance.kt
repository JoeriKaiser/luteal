package fr.luteal.core.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class PartnerPhaseTip(
    val id: String,
    val phase: CyclePhase,
    val source: String,
    val url: String
)

object PartnerPhaseTips {
    private const val NHS_PERIOD_PAIN = "NHS, Period pain"
    private const val NHS_PERIOD_PAIN_URL = "https://www.nhs.uk/symptoms/period-pain/"
    private const val NHS_PMS = "NHS, Premenstrual syndrome"
    private const val NHS_PMS_URL = "https://www.nhs.uk/conditions/pre-menstrual-syndrome/"
    private const val NHS_PERIODS = "NHS, Periods"
    private const val NHS_PERIODS_URL = "https://www.nhs.uk/conditions/periods/"
    private const val MIHM = "Mihm et al., Animal Reproduction Science, 2011"
    private const val MIHM_URL = "https://doi.org/10.1016/j.anireprosci.2010.08.030"
    private const val FEHRING = "Fehring et al., JOGNN, 2006"
    private const val FEHRING_URL = "https://doi.org/10.1111/j.1552-6909.2006.00051.x"
    private const val WHO = "WHO, Menstrual health and rights"
    private const val WHO_URL = "https://www.who.int/news/item/22-06-2022-who-statement-on-menstrual-health-and-rights"
    private const val ACOG_PMS = "ACOG, Premenstrual syndrome"
    private const val ACOG_PMS_URL = "https://www.acog.org/womens-health/faqs/premenstrual-syndrome-pms"

    val ALL: List<PartnerPhaseTip> = listOf(
        PartnerPhaseTip("partner_menstrual_comfort", CyclePhase.MENSTRUAL, NHS_PERIOD_PAIN, NHS_PERIOD_PAIN_URL),
        PartnerPhaseTip("partner_menstrual_space", CyclePhase.MENSTRUAL, NHS_PERIODS, NHS_PERIODS_URL),
        PartnerPhaseTip("partner_menstrual_listen", CyclePhase.MENSTRUAL, WHO, WHO_URL),
        PartnerPhaseTip("partner_follicular_energy_varies", CyclePhase.FOLLICULAR, MIHM, MIHM_URL),
        PartnerPhaseTip("partner_follicular_no_script", CyclePhase.FOLLICULAR, WHO, WHO_URL),
        PartnerPhaseTip("partner_ovulatory_not_certain", CyclePhase.OVULATORY, FEHRING, FEHRING_URL),
        PartnerPhaseTip("partner_ovulatory_ask", CyclePhase.OVULATORY, WHO, WHO_URL),
        PartnerPhaseTip("partner_luteal_progesterone", CyclePhase.LUTEAL, NHS_PMS, NHS_PMS_URL),
        PartnerPhaseTip("partner_luteal_communication", CyclePhase.LUTEAL, NHS_PMS, NHS_PMS_URL),
        PartnerPhaseTip("partner_luteal_practical", CyclePhase.LUTEAL, ACOG_PMS, ACOG_PMS_URL)
    )

    fun forDate(phase: CyclePhase, date: LocalDate): PartnerPhaseTip {
        val candidates = ALL.filter { it.phase == phase }
        check(candidates.isNotEmpty()) { "No partner tips registered for $phase" }
        val scattered = date.toEpochDay() * 2_654_435_761L
        val index = Math.floorMod(scattered, candidates.size.toLong()).toInt()
        return candidates[index]
    }
}

object PartnerPhaseResolver {
    fun resolve(projection: DuoProjection?, today: LocalDate): CurrentCyclePhase {
        if (projection == null) {
            return CurrentCyclePhase.Indeterminate(PhaseIndeterminateReason.NO_CURRENT_CYCLE)
        }
        val cycleDay = projection.cycleDay
        val earliest = projection.periodEstimate?.windowStart
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        if (cycleDay != null && cycleDay in 1..5) {
            return CurrentCyclePhase.Available(
                phase = CyclePhase.MENSTRUAL,
                certainty = PhaseCertainty.ESTIMATED
            )
        }

        if (earliest != null) {
            val daysUntilWindow = ChronoUnit.DAYS.between(today, earliest)
            return when {
                daysUntilWindow in 1..12 -> CurrentCyclePhase.Available(
                    phase = CyclePhase.LUTEAL,
                    certainty = PhaseCertainty.ESTIMATED
                )
                daysUntilWindow in 13..16 -> CurrentCyclePhase.Indeterminate(
                    PhaseIndeterminateReason.PHASE_TRANSITION
                )
                daysUntilWindow > 16 -> CurrentCyclePhase.Available(
                    phase = CyclePhase.FOLLICULAR,
                    certainty = PhaseCertainty.ESTIMATED
                )
                else -> CurrentCyclePhase.Indeterminate(
                    PhaseIndeterminateReason.NEXT_PERIOD_WINDOW
                )
            }
        }

        return if (cycleDay != null) {
            CurrentCyclePhase.Indeterminate(PhaseIndeterminateReason.NEEDS_MORE_HISTORY)
        } else {
            CurrentCyclePhase.Indeterminate(PhaseIndeterminateReason.NO_CURRENT_CYCLE)
        }
    }
}
