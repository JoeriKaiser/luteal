package fr.luteal.core.model

/**
 * Which observations the daily editor offers, given what the user declared.
 *
 * This is what the onboarding copy promises: "Sélectionnez les sujets que vous
 * souhaitez suivre particulièrement pour adapter les catégories d'observation."
 * Until now the declaration was stored and never read, so the promise was not
 * kept.
 *
 * Only [ContextGroup.OBSERVATION] contexts appear here. Timing contexts widen
 * estimation uncertainty instead, and adding vocabulary for them would need
 * sourced observation terms that do not yet exist in the register.
 *
 * Every addition below is limited to vocabulary a reviewed source supports.
 * A declared context is not licence to invent symptom terms, and nothing here
 * may read as screening: these are things the user may choose to record, never
 * things the app suggests they have.
 */
object ObservationCatalog {

    /** Offered to everyone, whatever they declared. */
    private val BASE = listOf(
        "cramps",
        "headache",
        "fatigue",
        "bloating"
    )

    /**
     * NHS pre-menstrual syndrome page, recorded in SOURCE_REGISTER.md as
     * "observation vocabulary only (mood, fatigue, bloating, cramping, breast
     * tenderness, headache, skin)". Fatigue, bloating, cramping and headache
     * are already in [BASE]; these are the remainder.
     */
    private val PREMENSTRUAL = listOf(
        "breast_tenderness",
        "mood_changes",
        "acne"
    )

    /**
     * WHO endometriosis fact sheet: "chronic pelvic pain (pain that does not go
     * away when the menstrual cycle ends)". Severe menstrual pain and heavy
     * bleeding are already representable through cramps and the bleeding scale;
     * pain persisting outside menstruation was not.
     */
    private val ENDOMETRIOSIS = listOf(
        "pelvic_pain_outside_period"
    )

    private val BY_CONTEXT: Map<TrackingContext, List<String>> = mapOf(
        TrackingContext.PMS to PREMENSTRUAL,
        TrackingContext.PMDD to PREMENSTRUAL,
        TrackingContext.ENDOMETRIOSIS to ENDOMETRIOSIS
    )

    /**
     * Symptom ids to offer, base first then context additions, de-duplicated
     * and order-stable so the editor does not reshuffle between sessions.
     */
    fun symptomIdsFor(contexts: Set<TrackingContext>): List<String> {
        val additions = TrackingContext.entries
            .filter { it in contexts }
            .flatMap { BY_CONTEXT[it].orEmpty() }

        return (BASE + additions).distinct()
    }
}
