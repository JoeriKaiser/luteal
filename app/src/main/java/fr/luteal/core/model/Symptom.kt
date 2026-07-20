package fr.luteal.core.model

data class Symptom(
    val id: String,
    val name: String,
    val category: SymptomCategory,
    val iconName: String
) {
    companion object {
        val DEFAULT_SYMPTOMS: List<Symptom> = listOf(
            Symptom(
                id = "cramps",
                name = "Crampes",
                category = SymptomCategory.PAIN,
                iconName = "ic_symptom_cramps"
            ),
            Symptom(
                id = "migraine",
                name = "Migraine",
                category = SymptomCategory.PAIN,
                iconName = "ic_symptom_migraine"
            ),
            Symptom(
                id = "mood_swings",
                name = "Sautes d'humeur",
                category = SymptomCategory.MOOD,
                iconName = "ic_symptom_mood_swings"
            ),
            Symptom(
                id = "anxiety",
                name = "Anxiété",
                category = SymptomCategory.MOOD,
                iconName = "ic_symptom_anxiety"
            ),
            Symptom(
                id = "fatigue",
                name = "Fatigue",
                category = SymptomCategory.ENERGY,
                iconName = "ic_symptom_fatigue"
            ),
            Symptom(
                id = "bloating",
                name = "Ballonnements",
                category = SymptomCategory.PHYSICAL,
                iconName = "ic_symptom_bloating"
            ),
            Symptom(
                id = "acne",
                name = "Acné",
                category = SymptomCategory.PHYSICAL,
                iconName = "ic_symptom_acne"
            ),
            Symptom(
                id = "breast_tenderness",
                name = "Sensibilité mammaire",
                category = SymptomCategory.PHYSICAL,
                iconName = "ic_symptom_breast_tenderness"
            )
        )
    }
}
