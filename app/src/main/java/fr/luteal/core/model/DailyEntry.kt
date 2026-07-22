package fr.luteal.core.model

import java.time.Instant
import java.time.LocalDate

data class DailyEntry(
    val date: LocalDate,
    val bleedingIntensity: BleedingIntensity? = null,
    val painLevel: Int? = null,
    val moodLevel: Int? = null,
    val energyLevel: Int? = null,
    val symptomIds: Set<String> = emptySet(),
    val notes: String = "",
    val updatedAt: Instant = Instant.now()
) {
    init {
        require(painLevel == null || painLevel in TRACKING_SCALE)
        require(moodLevel == null || moodLevel in TRACKING_SCALE)
        require(energyLevel == null || energyLevel in TRACKING_SCALE)
    }

    val hasObservations: Boolean
        get() = bleedingIntensity != null ||
            painLevel != null ||
            moodLevel != null ||
            energyLevel != null ||
            symptomIds.isNotEmpty() ||
            notes.isNotBlank()

    companion object {
        val TRACKING_SCALE = 1..5
    }
}
