package fr.luteal.core.model

/**
 * Optional age band, used only to pick a variability prior for cycle estimates.
 *
 * Each band carries the within-individual standard deviation of menstrual cycle
 * length measured for that band in the Apple Women's Health Study (Li et al.
 * 2023, Table 4; 163,275 cycles from 11,040 participants). See
 * docs/research/CONDITION_CYCLE_IMPACTS.md, Finding 3.
 *
 * Each band also carries the relative PCOS-to-regular variability multiplier
 * derived from Mortimer et al. 2026 (PMC12915291, Table 4; 160,206 cycles
 * across 15,586 AWHS participants), clamped at >= 1.0 so a PCOS declaration
 * never narrows the uncertainty window. Using the relative ratio keeps the
 * multiplier compatible with Li et al.'s artifact-filtered baseline. See
 * docs/research/CONDITION_CYCLE_IMPACTS.md, Finding 6.
 *
 * The baseline relationship is U-shaped rather than linear: variability falls
 * through the twenties and thirties, bottoms out at 35-39, and rises steeply
 * after 45. Declaring a band is optional. Nothing in the app requires it, and
 * it is never used for anything but the width of an estimate.
 */
enum class AgeBand(
    val id: String,
    val variationSdDays: Double,
    val pcosSdMultiplier: Double
) {
    UNDER_20("under_20", 5.33, 1.72),
    AGE_20_24("age_20_24", 5.07, 1.72),
    AGE_25_29("age_25_29", 4.70, 1.58),
    AGE_30_34("age_30_34", 4.28, 1.51),
    AGE_35_39("age_35_39", 3.79, 1.54),
    AGE_40_44("age_40_44", 3.99, 1.27),
    AGE_45_49("age_45_49", 5.42, 1.00),
    AGE_50_PLUS("age_50_plus", 11.19, 1.00);

    companion object {
        fun fromId(id: String?): AgeBand? = entries.find { it.id == id }

        /**
         * Prior used when no band has been declared.
         *
         * The unweighted mean of the six bands covering ages 20-49 (5.07, 4.70,
         * 4.28, 3.79, 3.99, 5.42). Derived from Li et al. 2023 Table 4 and
         * chosen so an undeclared user is not assigned the narrowest band by
         * default.
         */
        const val UNDECLARED_VARIATION_SD_DAYS = 4.54

        /**
         * PCOS multiplier used when no age band has been declared.
         *
         * Unweighted mean of the six 20-49 age-band ratios from Mortimer et al.
         * 2026 Table 4 (1.72, 1.58, 1.51, 1.54, 1.27, 1.00).
         */
        const val UNDECLARED_PCOS_SD_MULTIPLIER = 1.44
    }
}
