package fr.luteal.core.model

/**
 * What a declared context is allowed to change.
 *
 * The distinction is the whole point of the taxonomy: the contexts a user can
 * declare do not behave alike, and treating them as one undifferentiated list
 * is why the onboarding step previously had no coherent effect to implement.
 * See docs/research/CONDITION_CYCLE_IMPACTS.md, Finding 1.
 */
enum class ContextGroup {
    /**
     * Affects when cycles arrive. Widens estimation uncertainty; never moves
     * the central predicted date.
     */
    TIMING,

    /**
     * Affects what bleeding and pain are like, not when they arrive. Adapts the
     * observation vocabulary only, and must not touch estimation.
     */
    OBSERVATION
}

/**
 * User-selected tracking contexts.
 *
 * These are statements a user makes about themselves. They are never
 * diagnoses, are never inferred, and must never be presented as conclusions.
 *
 * Replaces the former `PremenstrualDisorder` enum, which was unreferenced and
 * whose name no longer described the set once perimenopause and thyroid
 * dysfunction were added.
 */
enum class TrackingContext(
    val id: String,
    val group: ContextGroup
) {
    PMS("pms", ContextGroup.OBSERVATION),
    PMDD("pmdd", ContextGroup.OBSERVATION),

    /**
     * Endometriosis is deliberately an OBSERVATION context. The meta-analytic
     * association with cycle length runs the other way - short cycles are a
     * risk factor for endometriosis, not a consequence of it - so using it to
     * widen estimates would invert the inference, and treating short cycles as
     * a risk signal would be screening. See Finding 2 of the research note.
     */
    ENDOMETRIOSIS("endometriosis", ContextGroup.OBSERVATION),

    PCOS("pcos", ContextGroup.TIMING),
    PERIMENOPAUSE("perimenopause", ContextGroup.TIMING),
    THYROID("thyroid", ContextGroup.TIMING);

    companion object {
        fun fromId(id: String): TrackingContext? = entries.find { it.id == id }

        /** Ids whose declaration widens estimation uncertainty. */
        val timingIds: Set<String> =
            entries.filter { it.group == ContextGroup.TIMING }.map { it.id }.toSet()
    }
}
