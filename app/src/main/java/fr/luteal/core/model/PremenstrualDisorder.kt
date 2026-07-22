package fr.luteal.core.model

/**
 * User-selected tracking contexts only. These values must never be inferred or
 * presented as diagnoses.
 */
enum class PremenstrualDisorder(
    val id: String,
    val trackedByDefault: Boolean
) {
    PMDD(id = "pmdd", trackedByDefault = false),
    PMS(id = "pms", trackedByDefault = false),
    ENDOMETRIOSIS(id = "endometriosis", trackedByDefault = false),
    PCOS(id = "pcos", trackedByDefault = false),
    CUSTOM(id = "custom", trackedByDefault = false);

    companion object {
        fun fromId(id: String): PremenstrualDisorder? = entries.find { it.id == id }
    }
}
