package fr.luteal.core.model

import java.time.LocalDate

/** Sourced guidance selected only after a phase has been established. */
data class PhaseTip(
    val id: String,
    val phase: CyclePhase,
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

    val ALL: List<PhaseTip> = listOf(
        PhaseTip("menstrual_warmth", CyclePhase.MENSTRUAL, NHS_PERIOD_PAIN, NHS_PERIOD_PAIN_URL),
        PhaseTip("menstrual_movement", CyclePhase.MENSTRUAL, NHS_PERIOD_PAIN, NHS_PERIOD_PAIN_URL),
        PhaseTip("menstrual_pain_support", CyclePhase.MENSTRUAL, NHS_PERIOD_PAIN, NHS_PERIOD_PAIN_URL),
        PhaseTip("follicular_varies", CyclePhase.FOLLICULAR, MIHM, MIHM_URL),
        PhaseTip("follicular_own_history", CyclePhase.FOLLICULAR, MIHM, MIHM_URL),
        PhaseTip("follicular_no_fixed_day", CyclePhase.FOLLICULAR, GRIEGER, GRIEGER_URL),
        PhaseTip("ovulatory_not_confirmed", CyclePhase.OVULATORY, FEHRING, FEHRING_URL),
        PhaseTip("ovulatory_counts_back", CyclePhase.OVULATORY, NHS_PERIODS, NHS_PERIODS_URL),
        PhaseTip("ovulatory_not_day_14", CyclePhase.OVULATORY, GRIEGER, GRIEGER_URL),
        PhaseTip("luteal_diary", CyclePhase.LUTEAL, NHS_PMS, NHS_PMS_URL),
        PhaseTip("luteal_daily_support", CyclePhase.LUTEAL, NHS_PMS, NHS_PMS_URL),
        PhaseTip("luteal_seek_support", CyclePhase.LUTEAL, NHS_PMS, NHS_PMS_URL)
    )

    fun forDate(phase: CyclePhase, date: LocalDate): PhaseTip {
        val candidates = ALL.filter { it.phase == phase }
        check(candidates.isNotEmpty()) { "No tips registered for $phase" }
        val scattered = date.toEpochDay() * 2_654_435_761L
        val index = Math.floorMod(scattered, candidates.size.toLong()).toInt()
        return candidates[index]
    }
}
