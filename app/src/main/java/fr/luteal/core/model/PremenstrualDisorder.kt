package fr.luteal.core.model

enum class PremenstrualDisorder(
    val id: String,
    val displayName: String,
    val description: String,
    val trackedByDefault: Boolean
) {
    PMDD(
        id = "pmdd",
        displayName = "Trouble Dysphorique Prémenstruel (TDP)",
        description = "Forme sévère du syndrome prémenstruel caractérisée par une détresse émotionnelle importante",
        trackedByDefault = true
    ),
    PMS(
        id = "pms",
        displayName = "Syndrome Prémenstruel (SPM)",
        description = "Ensemble de symptômes physiques et émotionnels survenant avant les règles",
        trackedByDefault = true
    ),
    ENDOMETRIOSIS(
        id = "endometriosis",
        displayName = "Endométriose",
        description = "Affection gynécologique caractérisée par la présence de tissu endométrial hors de l'utérus",
        trackedByDefault = true
    ),
    PCOS(
        id = "pcos",
        displayName = "SOPK - Syndrome des Ovaires Polykystiques",
        description = "Trouble hormonal entraînant des cycles irréguliers ou absents",
        trackedByDefault = true
    ),
    CUSTOM(
        id = "custom",
        displayName = "Autre affection",
        description = "Suivi personnalisé de symptômes spécifiques",
        trackedByDefault = false
    );

    companion object {
        fun fromId(id: String): PremenstrualDisorder? = entries.find { it.id == id }
    }
}
