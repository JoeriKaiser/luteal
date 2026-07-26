package fr.luteal.core.model

import java.time.LocalDate

/**
 * A sourced population fact about menstrual cycles.
 *
 * [source] and [url] are citations rather than copy, so they are held here next
 * to each other and are not translated. The French text lives in string
 * resources, keyed by [id].
 */
data class CycleFact(
    val id: String,
    val source: String,
    val url: String
)

/**
 * The facts Luteal is willing to show, and which one belongs to a given day.
 *
 * Every entry is drawn from a source already recorded as Reviewed or
 * Implemented in docs/research/SOURCE_REGISTER.md. Nothing here is written from
 * memory, and no citation is approximated.
 *
 * **Scope, deliberately narrow.** These are population facts about cycle length
 * and variability. They are not advice, they say nothing about the reader, and
 * they are never selected from the reader's own data - a fact chosen because
 * someone is on cycle day 24 would be an inference about their phase, which the
 * app must not make.
 *
 * **Condition prevalence is excluded on purpose.** The register's recorded
 * scope limit for the WHO PCOS fact sheet is explicit that "up to 70% of
 * affected people are undiagnosed" is a reason to make irregular cycles work
 * well, not a reason to surface screening prompts. A daily card announcing how
 * many people have an undiagnosed condition is exactly such a prompt.
 */
object CycleFacts {

    private const val GRIEGER = "Grieger & Norman, J Med Internet Res, 2020"
    private const val GRIEGER_URL = "https://pmc.ncbi.nlm.nih.gov/articles/PMC7381001/"
    private const val APPLE = "Li et al., npj Digital Medicine, 2023"
    private const val APPLE_URL = "https://pmc.ncbi.nlm.nih.gov/articles/PMC10226714/"
    private const val BULL = "Bull et al., npj Digital Medicine, 2019"
    private const val BULL_URL = "https://pmc.ncbi.nlm.nih.gov/articles/PMC6710244/"
    private const val NHS_PERIODS = "NHS, Periods"
    private const val NHS_PERIODS_URL = "https://www.nhs.uk/conditions/periods/"
    private const val NHS_IRREGULAR = "NHS, Irregular periods"
    private const val NHS_IRREGULAR_URL = "https://www.nhs.uk/conditions/irregular-periods/"
    private const val WHO = "Organisation mondiale de la santé, 2022"
    private const val WHO_URL =
        "https://www.who.int/news/item/22-06-2022-who-statement-on-menstrual-health-and-rights"

    val ALL: List<CycleFact> = listOf(
        // --- Grieger & Norman, 1,579,819 women -----------------------------
        // 16.32% had a median 28-day cycle.
        CycleFact("no_single_normal", GRIEGER, GRIEGER_URL),
        // 91.13% fell within 21-35 days.
        CycleFact("most_cycles_in_range", GRIEGER, GRIEGER_URL),
        // Only 69% of 1,086,923 varied by under 6 days.
        CycleFact("variation_is_common", GRIEGER, GRIEGER_URL),
        // 8.60% exceeded 35 days.
        CycleFact("long_cycles_common", GRIEGER, GRIEGER_URL),
        // 0.17% were under 21 days.
        CycleFact("short_cycles_rare", GRIEGER, GRIEGER_URL),
        // Day 14 accounted for only 13.08% of 18,761 LH-confirmed cycles.
        CycleFact("ovulation_not_day_14", GRIEGER, GRIEGER_URL),
        // Luteal phase 15 days 16.96%, 14 days 16.17% of 21,788 cycles.
        CycleFact("luteal_varies", GRIEGER, GRIEGER_URL),
        // 27-day cycles: 18.48% at 40+ versus 9.55% at 18-24.
        CycleFact("cycles_shorten_with_age", GRIEGER, GRIEGER_URL),
        // 25.37% varied by 0-1.5 days.
        CycleFact("quarter_very_regular", GRIEGER, GRIEGER_URL),

        // --- Li et al., 163,275 cycles -------------------------------------
        // Mean 28.7 days, 5th-95th percentile 22-38 days.
        CycleFact("mean_length", APPLE, APPLE_URL),
        // Within-person SD lowest at 35-39 (3.79 days).
        CycleFact("variation_by_age", APPLE, APPLE_URL),
        // 11.19 days above 50 versus 3.79 at 35-39, a ratio near three.
        CycleFact("variation_after_50", APPLE, APPLE_URL),
        // Median 28 days, IQR 26-30.
        CycleFact("median_iqr", APPLE, APPLE_URL),
        // 5.42 at 45-49 versus 4.70 at 25-29.
        CycleFact("variation_late_forties", APPLE, APPLE_URL),

        // --- Bull et al., 612,613 cycles -----------------------------------
        // Mean per-user variation 2.6 days.
        CycleFact("same_person_varies", BULL, BULL_URL),
        // Mean cycle length 29.3 days.
        CycleFact("large_cohort_mean", BULL, BULL_URL),

        // --- NHS -----------------------------------------------------------
        // "between 2 and 7 days, but it will usually last for about 5 days".
        CycleFact("period_duration", NHS_PERIODS, NHS_PERIODS_URL),
        // "about 20 to 90ml (about 1 to 5 tablespoons) of blood".
        CycleFact("blood_volume", NHS_PERIODS, NHS_PERIODS_URL),
        // "usually begin at around the age of 12".
        CycleFact("menarche_age", NHS_PERIODS, NHS_PERIODS_URL),
        // "mid-40s to mid-50s".
        CycleFact("menopause_age", NHS_PERIODS, NHS_PERIODS_URL),
        // "about 12 to 16 days before the start of your next period" - counted
        // backwards from the next period, not forwards to a fixed day.
        CycleFact("ovulation_counts_backwards", NHS_PERIODS, NHS_PERIODS_URL),
        // Irregular is a gap under 21 or over 35 days.
        CycleFact("irregular_definition", NHS_IRREGULAR, NHS_IRREGULAR_URL),

        // --- WHO -----------------------------------------------------------
        // "recognize and frame menstruation as a health issue, not a hygiene
        // issue".
        CycleFact("health_not_hygiene", WHO, WHO_URL),
        // "an environment in which menstruation is seen as positive and healthy
        // not something to be ashamed of".
        CycleFact("not_shameful", WHO, WHO_URL)
    )

    /**
     * The fact for [date]. Stable for the whole day and identical across
     * relaunches, because it is derived from the date rather than drawn at
     * random: a card that changed every time the app opened would read as
     * noise rather than as something worth reading.
     *
     * The multiplier scatters consecutive days across the list so the sequence
     * does not visibly walk it in order.
     */
    fun forDate(date: LocalDate): CycleFact {
        val scattered = date.toEpochDay() * 2_654_435_761L
        val index = Math.floorMod(scattered, ALL.size.toLong()).toInt()
        return ALL[index]
    }
}
