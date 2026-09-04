package fr.luteal.core.model

import java.time.LocalDate

/** Sourced guidance selected only after a phase has been established. */
data class PhaseTip(
    val id: String,
    val phase: CyclePhase,
    val targetContext: TrackingContext? = null,
    val targetSymptoms: Set<String> = emptySet(),
    val source: String,
    val url: String
)

object PhaseTips {
    private const val GRIEGER = "Grieger & Norman, J Med Internet Res, 2020"
    private const val GRIEGER_URL = "https://pmc.ncbi.nlm.nih.gov/articles/PMC7381001/"
    private const val HAS_ENDO = "HAS, Prise en charge de l'endométriose, 2017"
    private const val HAS_ENDO_URL = "https://www.has-sante.fr/jcms/c_2819733/fr/prise-en-charge-de-l-endometriose"
    private const val ACOG_PREMENSTRUAL_GUIDELINE = "ACOG, Management of Premenstrual Disorders, 2023"
    private const val ACOG_PREMENSTRUAL_GUIDELINE_URL = "https://pubmed.ncbi.nlm.nih.gov/37973069/"
    private const val GUNGOR_THYROID = "Güngör Semiz & Hekimsoy, Cureus, 2024"
    private const val GUNGOR_THYROID_URL = "https://pmc.ncbi.nlm.nih.gov/articles/PMC11259460/"

    val ALL: List<PhaseTip> = listOf(
        // Menstrual phase
        PhaseTip("menstrual_warmth", CyclePhase.MENSTRUAL, source = ClinicalSources.NHS_PERIOD_PAIN, url = ClinicalSources.NHS_PERIOD_PAIN_URL),
        PhaseTip("menstrual_movement", CyclePhase.MENSTRUAL, source = ClinicalSources.NHS_PERIOD_PAIN, url = ClinicalSources.NHS_PERIOD_PAIN_URL),
        PhaseTip("menstrual_pain_support", CyclePhase.MENSTRUAL, source = ClinicalSources.NHS_PERIOD_PAIN, url = ClinicalSources.NHS_PERIOD_PAIN_URL),
        PhaseTip("menstrual_hydration", CyclePhase.MENSTRUAL, source = ClinicalSources.NHS_PERIODS, url = ClinicalSources.NHS_PERIODS_URL),
        PhaseTip(
            "menstrual_endo_pelvic_rest",
            CyclePhase.MENSTRUAL,
            targetContext = TrackingContext.ENDOMETRIOSIS,
            targetSymptoms = setOf("pelvic_pain_outside_period", "cramps"),
            source = HAS_ENDO,
            url = HAS_ENDO_URL
        ),
        PhaseTip(
            "menstrual_endo_fatigue_pacing",
            CyclePhase.MENSTRUAL,
            targetContext = TrackingContext.ENDOMETRIOSIS,
            targetSymptoms = setOf("fatigue"),
            source = ClinicalSources.CNGOF_PAIN,
            url = ClinicalSources.CNGOF_PAIN_URL
        ),
        PhaseTip(
            "menstrual_endo_radiating_pain",
            CyclePhase.MENSTRUAL,
            targetContext = TrackingContext.ENDOMETRIOSIS,
            targetSymptoms = setOf("pelvic_pain_outside_period", "backache"),
            source = HAS_ENDO,
            url = HAS_ENDO_URL
        ),

        // Follicular phase
        PhaseTip("follicular_varies", CyclePhase.FOLLICULAR, source = ClinicalSources.MIHM, url = ClinicalSources.MIHM_URL),
        PhaseTip("follicular_own_history", CyclePhase.FOLLICULAR, source = ClinicalSources.MIHM, url = ClinicalSources.MIHM_URL),
        PhaseTip("follicular_no_fixed_day", CyclePhase.FOLLICULAR, source = GRIEGER, url = GRIEGER_URL),
        PhaseTip(
            "follicular_pcos_elongation",
            CyclePhase.FOLLICULAR,
            targetContext = TrackingContext.PCOS,
            source = ClinicalSources.MONASH_PCOS,
            url = ClinicalSources.MONASH_PCOS_URL
        ),
        PhaseTip(
            "follicular_pcos_movement",
            CyclePhase.FOLLICULAR,
            targetContext = TrackingContext.PCOS,
            source = ClinicalSources.MONASH_PCOS,
            url = ClinicalSources.MONASH_PCOS_URL
        ),
        PhaseTip(
            "follicular_thyroid_fatigue",
            CyclePhase.FOLLICULAR,
            targetContext = TrackingContext.THYROID,
            targetSymptoms = setOf("fatigue"),
            source = GUNGOR_THYROID,
            url = GUNGOR_THYROID_URL
        ),
        PhaseTip(
            "follicular_perimeno_fluctuation",
            CyclePhase.FOLLICULAR,
            targetContext = TrackingContext.PERIMENOPAUSE,
            source = ClinicalSources.BMS_PERIMENO,
            url = ClinicalSources.BMS_PERIMENO_URL
        ),

        // Ovulatory phase
        PhaseTip("ovulatory_not_confirmed", CyclePhase.OVULATORY, source = ClinicalSources.FEHRING, url = ClinicalSources.FEHRING_URL),
        PhaseTip("ovulatory_counts_back", CyclePhase.OVULATORY, source = ClinicalSources.NHS_PERIODS, url = ClinicalSources.NHS_PERIODS_URL),
        PhaseTip("ovulatory_not_day_14", CyclePhase.OVULATORY, source = GRIEGER, url = GRIEGER_URL),
        PhaseTip("ovulatory_hydration_mucus", CyclePhase.OVULATORY, source = ClinicalSources.ACOG_PMS, url = ClinicalSources.ACOG_PMS_URL),

        // Luteal phase
        PhaseTip("luteal_diary", CyclePhase.LUTEAL, source = ACOG_PREMENSTRUAL_GUIDELINE, url = ACOG_PREMENSTRUAL_GUIDELINE_URL),
        PhaseTip("luteal_daily_support", CyclePhase.LUTEAL, source = ClinicalSources.NHS_PMS, url = ClinicalSources.NHS_PMS_URL),
        PhaseTip("luteal_seek_support", CyclePhase.LUTEAL, source = ClinicalSources.NHS_PMS, url = ClinicalSources.NHS_PMS_URL),
        PhaseTip("luteal_hydration", CyclePhase.LUTEAL, source = ClinicalSources.NHS_PMS, url = ClinicalSources.NHS_PMS_URL),
        PhaseTip(
            "luteal_sodium_bloating",
            CyclePhase.LUTEAL,
            targetSymptoms = setOf("bloating", "breast_tenderness"),
            source = ClinicalSources.ACOG_PMS,
            url = ClinicalSources.ACOG_PMS_URL
        ),
        PhaseTip(
            "luteal_complex_carbs",
            CyclePhase.LUTEAL,
            targetSymptoms = setOf("fatigue", "mood_changes"),
            source = ClinicalSources.ACOG_PMS,
            url = ClinicalSources.ACOG_PMS_URL
        ),
        PhaseTip(
            "luteal_digestive_comfort",
            CyclePhase.LUTEAL,
            targetSymptoms = setOf("nausea", "digestive_changes"),
            source = ClinicalSources.ACOG_PMS,
            url = ClinicalSources.ACOG_PMS_URL
        ),
        PhaseTip(
            "luteal_pmdd_neuro_validation",
            CyclePhase.LUTEAL,
            targetContext = TrackingContext.PMDD,
            source = ClinicalSources.INSERM_PMDD,
            url = ClinicalSources.INSERM_PMDD_URL
        ),
        PhaseTip(
            "luteal_pmdd_pacing",
            CyclePhase.LUTEAL,
            targetContext = TrackingContext.PMDD,
            targetSymptoms = setOf("mood_changes", "anxiety"),
            source = ACOG_PREMENSTRUAL_GUIDELINE,
            url = ACOG_PREMENSTRUAL_GUIDELINE_URL
        ),
        PhaseTip(
            "luteal_sleep_routine",
            CyclePhase.LUTEAL,
            targetSymptoms = setOf("sleep_issue"),
            source = ACOG_PREMENSTRUAL_GUIDELINE,
            url = ACOG_PREMENSTRUAL_GUIDELINE_URL
        ),
        PhaseTip(
            "luteal_endo_pelvic_tension",
            CyclePhase.LUTEAL,
            targetContext = TrackingContext.ENDOMETRIOSIS,
            targetSymptoms = setOf("pelvic_pain_outside_period", "abdominal_pain"),
            source = HAS_ENDO,
            url = HAS_ENDO_URL
        ),
        PhaseTip(
            "luteal_perimeno_sleep",
            CyclePhase.LUTEAL,
            targetContext = TrackingContext.PERIMENOPAUSE,
            targetSymptoms = setOf("sleep_issue"),
            source = ClinicalSources.BMS_PERIMENO,
            url = ClinicalSources.BMS_PERIMENO_URL
        )
    )

    fun forDate(
        phase: CyclePhase,
        date: LocalDate,
        declaredContexts: Set<TrackingContext> = emptySet(),
        recentSymptomIds: Set<String> = emptySet()
    ): PhaseTip {
        val phaseCandidates = ALL.filter { it.phase == phase }
        check(phaseCandidates.isNotEmpty()) { "No tips registered for $phase" }

        val eligible = phaseCandidates.filter { tip ->
            tip.targetContext == null || tip.targetContext in declaredContexts
        }

        val scored = eligible.map { tip ->
            var score = 0
            if (tip.targetContext != null && tip.targetContext in declaredContexts) {
                score += 3
            }
            if (tip.targetSymptoms.isNotEmpty() && tip.targetSymptoms.any { it in recentSymptomIds }) {
                score += 2
            }
            tip to score
        }

        val maxScore = scored.maxOf { it.second }
        val bestCandidates = scored.filter { it.second == maxScore }.map { it.first }

        val scattered = date.toEpochDay() * 2_654_435_761L
        val index = Math.floorMod(scattered, bestCandidates.size.toLong()).toInt()
        return bestCandidates[index]
    }
}
