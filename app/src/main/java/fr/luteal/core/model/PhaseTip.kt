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
    private const val GRIEGER = "Grieger & Norman, J Med Internet Res, 2020"
    private const val GRIEGER_URL = "https://pmc.ncbi.nlm.nih.gov/articles/PMC7381001/"
    private const val ACOG_PMS = "ACOG, Premenstrual syndrome"
    private const val ACOG_PMS_URL = "https://www.acog.org/womens-health/faqs/premenstrual-syndrome-pms"
    private const val HAS_ENDO = "HAS, Prise en charge de l'endométriose, 2017"
    private const val HAS_ENDO_URL = "https://www.has-sante.fr/jcms/c_2819733/fr/prise-en-charge-de-l-endometriose"
    private const val CNGOF_PAIN = "CNGOF / Convergences PP, Douleurs pelviennes, 2025"
    private const val CNGOF_PAIN_URL = "https://www.cngof.fr"
    private const val INSERM_PMDD = "Inserm, Syndrome prémenstruel et TDPM, 2023"
    private const val INSERM_PMDD_URL = "https://www.inserm.fr/c-est-quoi/payetoncycle-cest-quoi-le-syndrome-premenstruel/"
    private const val ACOG_PREMENSTRUAL_GUIDELINE = "ACOG, Management of Premenstrual Disorders, 2023"
    private const val ACOG_PREMENSTRUAL_GUIDELINE_URL = "https://pubmed.ncbi.nlm.nih.gov/37973069/"
    private const val MONASH_PCOS = "Monash University / ESHRE, Guideline for PCOS, 2023"
    private const val MONASH_PCOS_URL = "https://www.monash.edu/medicine/mchri/pcos/guideline"
    private const val BMS_PERIMENO = "British Menopause Society, Consensus Statement, 2023"
    private const val BMS_PERIMENO_URL = "https://thebms.org.uk/publications/consensus-statements/"
    private const val GUNGOR_THYROID = "Güngör Semiz & Hekimsoy, Cureus, 2024"
    private const val GUNGOR_THYROID_URL = "https://pmc.ncbi.nlm.nih.gov/articles/PMC11259460/"

    val ALL: List<PhaseTip> = listOf(
        // Menstrual phase
        PhaseTip("menstrual_warmth", CyclePhase.MENSTRUAL, source = NHS_PERIOD_PAIN, url = NHS_PERIOD_PAIN_URL),
        PhaseTip("menstrual_movement", CyclePhase.MENSTRUAL, source = NHS_PERIOD_PAIN, url = NHS_PERIOD_PAIN_URL),
        PhaseTip("menstrual_pain_support", CyclePhase.MENSTRUAL, source = NHS_PERIOD_PAIN, url = NHS_PERIOD_PAIN_URL),
        PhaseTip("menstrual_hydration", CyclePhase.MENSTRUAL, source = NHS_PERIODS, url = NHS_PERIODS_URL),
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
            source = CNGOF_PAIN,
            url = CNGOF_PAIN_URL
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
        PhaseTip("follicular_varies", CyclePhase.FOLLICULAR, source = MIHM, url = MIHM_URL),
        PhaseTip("follicular_own_history", CyclePhase.FOLLICULAR, source = MIHM, url = MIHM_URL),
        PhaseTip("follicular_no_fixed_day", CyclePhase.FOLLICULAR, source = GRIEGER, url = GRIEGER_URL),
        PhaseTip(
            "follicular_pcos_elongation",
            CyclePhase.FOLLICULAR,
            targetContext = TrackingContext.PCOS,
            source = MONASH_PCOS,
            url = MONASH_PCOS_URL
        ),
        PhaseTip(
            "follicular_pcos_movement",
            CyclePhase.FOLLICULAR,
            targetContext = TrackingContext.PCOS,
            source = MONASH_PCOS,
            url = MONASH_PCOS_URL
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
            source = BMS_PERIMENO,
            url = BMS_PERIMENO_URL
        ),

        // Ovulatory phase
        PhaseTip("ovulatory_not_confirmed", CyclePhase.OVULATORY, source = FEHRING, url = FEHRING_URL),
        PhaseTip("ovulatory_counts_back", CyclePhase.OVULATORY, source = NHS_PERIODS, url = NHS_PERIODS_URL),
        PhaseTip("ovulatory_not_day_14", CyclePhase.OVULATORY, source = GRIEGER, url = GRIEGER_URL),
        PhaseTip("ovulatory_hydration_mucus", CyclePhase.OVULATORY, source = ACOG_PMS, url = ACOG_PMS_URL),

        // Luteal phase
        PhaseTip("luteal_diary", CyclePhase.LUTEAL, source = ACOG_PREMENSTRUAL_GUIDELINE, url = ACOG_PREMENSTRUAL_GUIDELINE_URL),
        PhaseTip("luteal_daily_support", CyclePhase.LUTEAL, source = NHS_PMS, url = NHS_PMS_URL),
        PhaseTip("luteal_seek_support", CyclePhase.LUTEAL, source = NHS_PMS, url = NHS_PMS_URL),
        PhaseTip("luteal_hydration", CyclePhase.LUTEAL, source = NHS_PMS, url = NHS_PMS_URL),
        PhaseTip(
            "luteal_sodium_bloating",
            CyclePhase.LUTEAL,
            targetSymptoms = setOf("bloating", "breast_tenderness"),
            source = ACOG_PMS,
            url = ACOG_PMS_URL
        ),
        PhaseTip(
            "luteal_complex_carbs",
            CyclePhase.LUTEAL,
            targetSymptoms = setOf("fatigue", "mood_changes"),
            source = ACOG_PMS,
            url = ACOG_PMS_URL
        ),
        PhaseTip(
            "luteal_pmdd_neuro_validation",
            CyclePhase.LUTEAL,
            targetContext = TrackingContext.PMDD,
            source = INSERM_PMDD,
            url = INSERM_PMDD_URL
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
            source = BMS_PERIMENO,
            url = BMS_PERIMENO_URL
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
