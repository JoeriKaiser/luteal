package fr.luteal.core.model

data class Symptom(
    val id: String,
    val category: SymptomCategory,
    val iconName: String
) {
    companion object {
        val DEFAULT_SYMPTOMS: List<Symptom> = listOf(
            Symptom("cramps", SymptomCategory.PAIN, "cramps"),
            Symptom("headache", SymptomCategory.PAIN, "headache"),
            Symptom("mood_changes", SymptomCategory.MOOD, "mood"),
            Symptom("anxiety", SymptomCategory.MOOD, "anxiety"),
            Symptom("fatigue", SymptomCategory.ENERGY, "fatigue"),
            Symptom("bloating", SymptomCategory.PHYSICAL, "bloating"),
            Symptom("acne", SymptomCategory.PHYSICAL, "acne"),
            Symptom("breast_tenderness", SymptomCategory.PHYSICAL, "tenderness"),
            // Offered only when endometriosis is declared; see
            // [ObservationCatalog]. Safe to add client-side because the catalog
            // is client-owned under E2EE and unknown keys survive sync as
            // customs (see SymptomCatalogAdopter).
            Symptom("pelvic_pain_outside_period", SymptomCategory.PAIN, "pelvic_pain")
        )
    }
}
