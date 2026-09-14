package fr.luteal.core.model

import java.time.LocalDate

data class PartnerPhaseTip(
    val id: String,
    val phase: CyclePhase,
    val targetContext: TrackingContext? = null,
    val source: String,
    val url: String
)

object PartnerPhaseTips {
    private const val WHO = "WHO, Menstrual health and rights"
    private const val WHO_URL = "https://www.who.int/news/item/22-06-2022-who-statement-on-menstrual-health-and-rights"

    val ALL: List<PartnerPhaseTip> = listOf(
        PartnerPhaseTip("partner_menstrual_comfort", CyclePhase.MENSTRUAL, source = ClinicalSources.NHS_PERIOD_PAIN, url = ClinicalSources.NHS_PERIOD_PAIN_URL),
        PartnerPhaseTip("partner_menstrual_space", CyclePhase.MENSTRUAL, source = ClinicalSources.NHS_PERIODS, url = ClinicalSources.NHS_PERIODS_URL),
        PartnerPhaseTip("partner_menstrual_listen", CyclePhase.MENSTRUAL, source = WHO, url = WHO_URL),
        PartnerPhaseTip(
            "partner_menstrual_endo_support",
            CyclePhase.MENSTRUAL,
            targetContext = TrackingContext.ENDOMETRIOSIS,
            source = ClinicalSources.CNGOF_PAIN,
            url = ClinicalSources.CNGOF_PAIN_URL
        ),
        PartnerPhaseTip("partner_follicular_energy_varies", CyclePhase.FOLLICULAR, source = ClinicalSources.MIHM, url = ClinicalSources.MIHM_URL),
        PartnerPhaseTip("partner_follicular_no_script", CyclePhase.FOLLICULAR, source = WHO, url = WHO_URL),
        PartnerPhaseTip(
            "partner_follicular_pcos_support",
            CyclePhase.FOLLICULAR,
            targetContext = TrackingContext.PCOS,
            source = ClinicalSources.MONASH_PCOS,
            url = ClinicalSources.MONASH_PCOS_URL
        ),
        PartnerPhaseTip("partner_ovulatory_not_certain", CyclePhase.OVULATORY, source = ClinicalSources.FEHRING, url = ClinicalSources.FEHRING_URL),
        PartnerPhaseTip("partner_ovulatory_ask", CyclePhase.OVULATORY, source = WHO, url = WHO_URL),
        PartnerPhaseTip("partner_luteal_progesterone", CyclePhase.LUTEAL, source = ClinicalSources.NHS_PMS, url = ClinicalSources.NHS_PMS_URL),
        PartnerPhaseTip("partner_luteal_communication", CyclePhase.LUTEAL, source = ClinicalSources.NHS_PMS, url = ClinicalSources.NHS_PMS_URL),
        PartnerPhaseTip("partner_luteal_practical", CyclePhase.LUTEAL, source = ClinicalSources.ACOG_PMS, url = ClinicalSources.ACOG_PMS_URL),
        PartnerPhaseTip(
            "partner_luteal_pmdd_space",
            CyclePhase.LUTEAL,
            targetContext = TrackingContext.PMDD,
            source = ClinicalSources.INSERM_PMDD,
            url = ClinicalSources.INSERM_PMDD_URL
        ),
        PartnerPhaseTip(
            "partner_perimeno_support",
            CyclePhase.LUTEAL,
            targetContext = TrackingContext.PERIMENOPAUSE,
            source = ClinicalSources.BMS_PERIMENO,
            url = ClinicalSources.BMS_PERIMENO_URL
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
        if (cycleDay == null || cycleDay < 1) {
            return CurrentCyclePhase.Indeterminate(PhaseIndeterminateReason.NO_CURRENT_CYCLE)
        }
        val cycle = Cycle(
            id = "partner",
            startDate = today.minusDays((cycleDay - 1).toLong())
        )
        return CurrentCyclePhaseCalculator.evaluate(
            today = today,
            currentCycle = cycle,
            todayEntry = null,
            estimateResult = projection.toEstimateResult()
        )
    }

    private fun DuoProjection.toEstimateResult(): CycleEstimateResult {
        val shared = periodEstimate ?: return CycleEstimateResult.NeedsMoreHistory
        val central = shared.centralDate.toLocalDate()
            ?: return CycleEstimateResult.NeedsMoreHistory
        val earliest = shared.windowStart.toLocalDate()
            ?: return CycleEstimateResult.NeedsMoreHistory
        val latest = shared.windowEnd.toLocalDate()
            ?: return CycleEstimateResult.NeedsMoreHistory
        return CycleEstimateResult.Available(
            CycleEstimate(
                earliestDate = earliest,
                centralDate = central,
                latestDate = latest,
                cycleCount = shared.cycleCount ?: 0,
                variabilityDays = shared.variabilityDays ?: Int.MAX_VALUE
            )
        )
    }

    private fun String?.toLocalDate(): LocalDate? =
        this?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}
