package fr.luteal.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LutealBackupPayload(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    @SerialName("exported_at") val exportedAt: String,
    @SerialName("app_version") val appVersion: String,
    @SerialName("cycles") val cycles: List<CycleBackupDto> = emptyList(),
    @SerialName("daily_entries") val dailyEntries: List<DailyEntryBackupDto> = emptyList(),
    @SerialName("symptom_logs") val symptomLogs: List<SymptomLogBackupDto> = emptyList(),
    @SerialName("preferences") val preferences: UserPreferencesBackupDto = UserPreferencesBackupDto()
)

@Serializable
data class CycleBackupDto(
    @SerialName("id") val id: String,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("average_length_days") val averageLengthDays: Int = 28,
    @SerialName("luteal_phase_length_days") val lutealPhaseLengthDays: Int = 14,
    @SerialName("period_days") val periodDays: List<PeriodDayBackupDto> = emptyList()
)

@Serializable
data class PeriodDayBackupDto(
    @SerialName("date") val date: String,
    @SerialName("bleeding_intensity") val bleedingIntensity: String,
    @SerialName("notes") val notes: String = "",
    @SerialName("symptom_ids") val symptomIds: List<String> = emptyList()
)

@Serializable
data class DailyEntryBackupDto(
    @SerialName("date") val date: String,
    @SerialName("bleeding_intensity") val bleedingIntensity: String? = null,
    @SerialName("pain_level") val painLevel: Int? = null,
    @SerialName("mood_level") val moodLevel: Int? = null,
    @SerialName("energy_level") val energyLevel: Int? = null,
    @SerialName("symptom_ids") val symptomIds: List<String> = emptyList(),
    @SerialName("notes") val notes: String = "",
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
data class SymptomLogBackupDto(
    @SerialName("id") val id: String,
    @SerialName("timestamp") val timestamp: String,
    @SerialName("date") val date: String,
    @SerialName("symptom_id") val symptomId: String,
    @SerialName("severity") val severity: Int,
    @SerialName("notes") val notes: String = ""
)

@Serializable
data class UserPreferencesBackupDto(
    @SerialName("user_role") val userRole: String = "PRIMARY_TRACKER",
    @SerialName("locale") val locale: String = "fr",
    @SerialName("track_pmdd") val trackPmdd: Boolean = false,
    @SerialName("track_pms") val trackPms: Boolean = false,
    @SerialName("track_endometriosis") val trackEndometriosis: Boolean = false,
    @SerialName("track_pcos") val trackPcos: Boolean = false,
    @SerialName("track_perimenopause") val trackPerimenopause: Boolean = false,
    @SerialName("track_thyroid") val trackThyroid: Boolean = false,
    @SerialName("age_band") val ageBand: String? = null
)
