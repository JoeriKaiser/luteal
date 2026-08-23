package fr.luteal.core.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class PartnerPhaseTip(
    val id: String,
    val phase: CyclePhase,
    val targetContext: TrackingContext? = null,
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
    private const val CNGOF_PAIN = "CNGOF / Convergences PP, Douleurs pelviennes, 2025"
    private const val CNGOF_PAIN_URL = "https://www.cngof.fr"
    private const val INSERM_PMDD = "Inserm, Syndrome prémenstruel et TDPM, 2023"
    private const val INSERM_PMDD_URL = "https://www.inserm.fr/c-est-quoi/payetoncycle-cest-quoi-le-syndrome-premenstruel/"
    private const val MONASH_PCOS = "Monash University / ESHRE, Guideline for PCOS, 2023"
    private const val MONASH_PCOS_URL = "https://www.monash.edu/medicine/mchri/pcos/guideline"
    private const val BMS_PERIMENO = "British Menopause Society, Consensus Statement, 2023"
    private const val BMS_PERIMENO_URL = "https://thebms.org.uk/publications/consensus-statements/"

    val ALL: List<PartnerPhaseTip> = listOf(
        PartnerPhaseTip("partner_menstrual_comfort", CyclePhase.MENSTRUAL, source = NHS_PERIOD_PAIN, url = NHS_PERIOD_PAIN_URL),
        PartnerPhaseTip("partner_menstrual_space", CyclePhase.MENSTRUAL, source = NHS_PERIODS, url = NHS_PERIODS_URL),
        PartnerPhaseTip("partner_menstrual_listen", CyclePhase.MENSTRUAL, source = WHO, url = WHO_URL),
        PartnerPhaseTip(
            "partner_menstrual_endo_support",
            CyclePhase.MENSTRUAL,
            targetContext = TrackingContext.ENDOMETRIOSIS,
            source = CNGOF_PAIN,
            url = CNGOF_PAIN_URL
        ),
        PartnerPhaseTip("partner_follicular_energy_varies", CyclePhase.FOLLICULAR, source = MIHM, url = MIHM_URL),
        PartnerPhaseTip("partner_follicular_no_script", CyclePhase.FOLLICULAR, source = WHO, url = WHO_URL),
        PartnerPhaseTip(
            "partner_follicular_pcos_support",
            CyclePhase.FOLLICULAR,
            targetContext = TrackingContext.PCOS,
            source = MONASH_PCOS,
            url = MONASH_PCOS_URL
        ),
        PartnerPhaseTip("partner_ovulatory_not_certain", CyclePhase.OVULATORY, source = FEHRING, url = FEHRING_URL),
        PartnerPhaseTip("partner_ovulatory_ask", CyclePhase.OVULATORY, source = WHO, url = WHO_URL),
        PartnerPhaseTip("partner_luteal_progesterone", CyclePhase.LUTEAL, source = NHS_PMS, url = NHS_PMS_URL),
        PartnerPhaseTip("partner_luteal_communication", CyclePhase.LUTEAL, source = NHS_PMS, url = NHS_PMS_URL),
        PartnerPhaseTip("partner_luteal_practical", CyclePhase.LUTEAL, source = ACOG_PMS, url = ACOG_PMS_URL),
        PartnerPhaseTip(
            "partner_luteal_pmdd_space",
            CyclePhase.LUTEAL,
            targetContext = TrackingContext.PMDD,
            source = INSERM_PMDD,
            url = INSERM_PMDD_URL
        ),
        PartnerPhaseTip(
            "partner_perimeno_support",
            CyclePhase.LUTEAL,
            targetContext = TrackingContext.PERIMENOPAUSE,
            source = BMS_PERIMENO,
            url = BMS_PERIMENO_URL
        )
    )

    fun forDate(
        phase: CyclePhase,
        date: LocalDate,
        declaredContexts: Set<TrackingContext> = emptySet()
    ): PartnerPhaseTip {
        val phaseCandidates = ALL.filter { it.phase == phase }
        check(phaseCandidates.isNotEmpty()) { "No partner tips registered for $phase" }

        val eligible = phaseCandidates.filter { tip ->
            tip.targetContext == null || tip.targetContext in declaredContexts
        }
        val scored = eligible.map { tip ->
            val score = if (tip.targetContext != null && tip.targetContext in declaredContexts) 3 else 0
            tip to score
        }
        val maxScore = scored.maxOf { it.second }
        val bestCandidates = scored.filter { it.second == maxScore }.map { it.first }

        val scattered = date.toEpochDay() * 2_654_435_761L
        val index = Math.floorMod(scattered, bestCandidates.size.toLong()).toInt()
        return bestCandidates[index]
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
