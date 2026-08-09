package fr.luteal.core.model

data class DuoSharingPreferences(
    val shareCycleDay: Boolean = false,
    val sharePeriodEstimate: Boolean = false,
    val shareMood: Boolean = false,
    val shareEnergy: Boolean = false,
    val shareSupportRequests: Boolean = false
)

enum class DuoSharingField {
    CYCLE_DAY,
    PERIOD_ESTIMATE,
    MOOD,
    ENERGY,
    SUPPORT_REQUESTS
}
