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
    @SerialName("biomarker_observations") val biomarkerObservations: List<BiomarkerBackupDto> = emptyList(),
    @SerialName("preferences") val preferences: UserPreferencesBackupDto = UserPreferencesBackupDto()
)

@Serializable
data class CycleBackupDto(
    @SerialName("id") val id: String,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("average_length_days") val averageLengthDays: Int = 28,
    @SerialName("luteal_phase_length_days") val lutealPhaseLengthDays: Int = 14,
    @SerialName("period_days") val periodDays: List<PeriodDayBackupDto> = emptyList(),
    @SerialName("is_excluded_from_estimates") val isExcludedFromEstimates: Boolean = false,
    @SerialName("exclusion_reason") val exclusionReason: String? = null
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
data class BiomarkerBackupDto(
    @SerialName("date") val date: String,
    @SerialName("bbt_celsius") val bbtCelsius: Double? = null,
    @SerialName("bbt_time") val bbtTime: String? = null,
    @SerialName("bbt_quality") val bbtQuality: String = "normal",
    @SerialName("bbt_disturbances") val bbtDisturbances: List<String> = emptyList(),
    @SerialName("cervical_sensation") val cervicalSensation: String? = null,
    @SerialName("cervical_texture") val cervicalTexture: String? = null,
    @SerialName("lh_test_result") val lhTestResult: String? = null,
    @SerialName("hcg_test_result") val hcgTestResult: String? = null,
    @SerialName("notes") val notes: String = "",
    @SerialName("updated_at") val updatedAt: String
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
    @SerialName("age_band") val ageBand: String? = null,
    @SerialName("temperature_unit") val temperatureUnit: String = "CELSIUS"
)

enum class ImportStrategy {
    MERGE_UPSERT,
    REPLACE_ALL
}

data class LutealBackupPreview(
    val schemaVersion: Int,
    val exportedAt: String,
    val appVersion: String,
    val cycleCount: Int,
    val earliestCycleDate: String?,
    val latestCycleDate: String?,
    val dailyEntryCount: Int,
    val earliestEntryDate: String?,
    val latestEntryDate: String?,
    val symptomLogCount: Int,
    val biomarkerCount: Int = 0,
    val preferences: UserPreferencesBackupDto
)

data class ImportSummary(
    val cyclesImported: Int,
    val dailyEntriesImported: Int,
    val symptomLogsImported: Int,
    val biomarkersImported: Int = 0,
    val preferencesRestored: Boolean
)

sealed class DataImportError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data object InvalidJsonSyntax : DataImportError("Invalid JSON syntax")
    data class UnsupportedSchemaVersion(val version: Int) : DataImportError("Unsupported schema version: $version")
    data class CorruptedPayload(val detail: String) : DataImportError("Corrupted payload: $detail")
    data class IoError(val detail: String) : DataImportError("I/O error: $detail")
}
