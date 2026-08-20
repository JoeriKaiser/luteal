package fr.luteal.core.data

import fr.luteal.core.data.datastore.UserPreferencesDataStore
import fr.luteal.core.data.local.BiomarkerDao
import fr.luteal.core.data.local.CycleDao
import fr.luteal.core.data.local.DailyEntryDao
import fr.luteal.core.data.local.SymptomDao
import fr.luteal.core.model.BiomarkerBackupDto
import fr.luteal.core.model.CycleBackupDto
import fr.luteal.core.model.DailyEntryBackupDto
import fr.luteal.core.model.LutealBackupPayload
import fr.luteal.core.model.PeriodDayBackupDto
import fr.luteal.core.model.SymptomLogBackupDto
import fr.luteal.core.model.UserPreferencesBackupDto
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import java.io.OutputStream
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataExportManager @Inject constructor(
    private val cycleDao: CycleDao,
    private val dailyEntryDao: DailyEntryDao,
    private val symptomDao: SymptomDao,
    private val biomarkerDao: BiomarkerDao,
    private val userPreferencesDataStore: UserPreferencesDataStore
) {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    suspend fun createBackupPayload(appVersion: String = "1.2.0"): LutealBackupPayload {
        val cycles = cycleDao.getAllCyclesOnce().map { entity ->
            val periodDays = mutableListOf<PeriodDayBackupDto>()
            if (entity.periodDaysJson.isNotBlank()) {
                runCatching {
                    val arr = JSONArray(entity.periodDaysJson)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val symptomsList = mutableListOf<String>()
                        val symptomsArr = obj.optJSONArray("symptomIds")
                        if (symptomsArr != null) {
                            for (j in 0 until symptomsArr.length()) {
                                symptomsList.add(symptomsArr.getString(j))
                            }
                        }
                        periodDays.add(
                            PeriodDayBackupDto(
                                date = obj.getString("date"),
                                bleedingIntensity = obj.getString("bleedingIntensity"),
                                notes = obj.optString("notes", ""),
                                symptomIds = symptomsList
                            )
                        )
                    }
                }
            }
            CycleBackupDto(
                id = entity.id,
                startDate = entity.startDate,
                endDate = entity.endDate,
                averageLengthDays = entity.averageLengthDays,
                lutealPhaseLengthDays = entity.lutealPhaseLengthDays,
                periodDays = periodDays,
                isExcludedFromEstimates = entity.isExcludedFromEstimates,
                exclusionReason = entity.exclusionReason
            )
        }

        val dailyEntries = dailyEntryDao.getAllEntriesOnce().map { entity ->
            val symptomIds = mutableListOf<String>()
            if (entity.symptomIdsJson.isNotBlank()) {
                runCatching {
                    val arr = JSONArray(entity.symptomIdsJson)
                    for (i in 0 until arr.length()) {
                        symptomIds.add(arr.getString(i))
                    }
                }
            }
            DailyEntryBackupDto(
                date = entity.date,
                bleedingIntensity = entity.bleedingIntensity,
                painLevel = entity.painLevel,
                moodLevel = entity.moodLevel,
                energyLevel = entity.energyLevel,
                symptomIds = symptomIds,
                notes = entity.notes,
                updatedAt = Instant.ofEpochMilli(entity.updatedAtEpochMillis).toString()
            )
        }

        val symptomLogs = symptomDao.getAllSymptomLogsOnce().map { entity ->
            SymptomLogBackupDto(
                id = entity.id,
                timestamp = Instant.ofEpochMilli(entity.timestampEpochMillis).toString(),
                date = entity.date,
                symptomId = entity.symptomId,
                severity = entity.severity,
                notes = entity.notes
            )
        }

        val biomarkerObservations = biomarkerDao.getAllObservationsOnce().map { entity ->
            val disturbances = mutableListOf<String>()
            if (entity.bbtDisturbancesJson.isNotBlank()) {
                runCatching {
                    val arr = JSONArray(entity.bbtDisturbancesJson)
                    for (i in 0 until arr.length()) {
                        disturbances.add(arr.getString(i))
                    }
                }
            }
            BiomarkerBackupDto(
                date = entity.date,
                bbtCelsius = entity.bbtCelsius,
                bbtTime = entity.bbtTime,
                bbtQuality = entity.bbtQuality,
                bbtDisturbances = disturbances,
                cervicalSensation = entity.cervicalSensation,
                cervicalTexture = entity.cervicalTexture,
                lhTestResult = entity.lhTestResult,
                hcgTestResult = entity.hcgTestResult,
                notes = entity.notes,
                updatedAt = Instant.ofEpochMilli(entity.updatedAtEpochMillis).toString()
            )
        }

        val prefs = userPreferencesDataStore.userPreferencesFlow.first()
        val preferencesDto = UserPreferencesBackupDto(
            userRole = prefs.userRole,
            locale = prefs.locale,
            trackPmdd = prefs.trackPmdd,
            trackPms = prefs.trackPms,
            trackEndometriosis = prefs.trackEndometriosis,
            trackPcos = prefs.trackPcos,
            trackPerimenopause = prefs.trackPerimenopause,
            trackThyroid = prefs.trackThyroid,
            ageBand = prefs.ageBand,
            temperatureUnit = prefs.temperatureUnit
        )

        return LutealBackupPayload(
            schemaVersion = 1,
            exportedAt = Instant.now().toString(),
            appVersion = appVersion,
            cycles = cycles,
            dailyEntries = dailyEntries,
            symptomLogs = symptomLogs,
            biomarkerObservations = biomarkerObservations,
            preferences = preferencesDto
        )
    }

    suspend fun exportToJsonString(appVersion: String = "1.2.0"): String {
        val payload = createBackupPayload(appVersion)
        return json.encodeToString(payload)
    }

    suspend fun exportToStream(outputStream: OutputStream, appVersion: String = "1.2.0") {
        val jsonString = exportToJsonString(appVersion)
        outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(jsonString)
            writer.flush()
        }
    }
}
