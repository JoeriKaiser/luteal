package fr.luteal.core.model

/**
 * Optional age band, used only to pick a variability prior for cycle estimates.
 *
 * Each band carries the within-individual standard deviation of menstrual cycle
 * length measured for that band in the Apple Women's Health Study (Table 4;
 * 163,275 cycles from 11,040 participants). See
 * docs/research/CONDITION_CYCLE_IMPACTS.md, Finding 3.
 *
 * The relationship is U-shaped rather than linear: variability falls through
 * the twenties and thirties, bottoms out at 35-39, and rises steeply after 45.
 * A single population constant cannot represent that, which is why the band is
 * worth asking for at all.
 *
 * Declaring a band is optional. Nothing in the app requires it, and it is never
 * used for anything but the width of an estimate.
 */
enum class AgeBand(
    val id: String,
    val variationSdDays: Double
) {
    UNDER_20("under_20", 5.33),
    AGE_20_24("age_20_24", 5.07),
    AGE_25_29("age_25_29", 4.70),
    AGE_30_34("age_30_34", 4.28),
    AGE_35_39("age_35_39", 3.79),
    AGE_40_44("age_40_44", 3.99),
    AGE_45_49("age_45_49", 5.42),
    AGE_50_PLUS("age_50_plus", 11.19);

    companion object {
        fun fromId(id: String?): AgeBand? = entries.find { it.id == id }

        /**
         * Prior used when no band has been declared.
         *
         * The unweighted mean of the six bands covering ages 20-49 (5.07, 4.70,
         * 4.28, 3.79, 3.99, 5.42). Derived from the cited table rather than
         * reported by it, and chosen so that an undeclared user is not assigned
         * the lowest-variability band by default: understating uncertainty is
         * the failure mode that matters here.
         */
        const val UNDECLARED_VARIATION_SD_DAYS = 4.54
    }
}
