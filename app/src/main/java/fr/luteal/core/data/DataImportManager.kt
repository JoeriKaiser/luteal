package fr.luteal.core.data

import androidx.room.withTransaction
import fr.luteal.core.data.datastore.UserPreferencesDataStore
import fr.luteal.core.data.entity.BiomarkerObservationEntity
import fr.luteal.core.data.entity.CycleEntity
import fr.luteal.core.data.entity.DailyEntryEntity
import fr.luteal.core.data.entity.SyncStateEntity
import fr.luteal.core.data.entity.SymptomLogEntity
import fr.luteal.core.data.local.BiomarkerDao
import fr.luteal.core.data.local.CycleDao
import fr.luteal.core.data.local.DailyEntryDao
import fr.luteal.core.data.local.LutealDatabase
import fr.luteal.core.data.local.SymptomDao
import fr.luteal.core.data.local.SyncStateDao
import fr.luteal.core.model.DataImportError
import fr.luteal.core.model.ImportStrategy
import fr.luteal.core.model.ImportSummary
import fr.luteal.core.model.LutealBackupPayload
import fr.luteal.core.model.LutealBackupPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataImportManager @Inject constructor(
    private val database: LutealDatabase,
    private val cycleDao: CycleDao,
    private val dailyEntryDao: DailyEntryDao,
    private val symptomDao: SymptomDao,
    private val biomarkerDao: BiomarkerDao,
    private val syncStateDao: SyncStateDao,
    private val userPreferencesDataStore: UserPreferencesDataStore
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun inspectBackup(inputStream: InputStream): Result<Pair<LutealBackupPreview, LutealBackupPayload>> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val jsonString = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                if (jsonString.isBlank()) {
                    throw DataImportError.InvalidJsonSyntax
                }

                val payload = try {
                    json.decodeFromString<LutealBackupPayload>(jsonString)
                } catch (e: SerializationException) {
                    throw DataImportError.InvalidJsonSyntax
                } catch (e: IllegalArgumentException) {
                    throw DataImportError.InvalidJsonSyntax
                }

                if (payload.schemaVersion != 1) {
                    throw DataImportError.UnsupportedSchemaVersion(payload.schemaVersion)
                }

                val cycleDates = payload.cycles.mapNotNull { it.startDate.takeIf { d -> d.isNotBlank() } }.sorted()
                val entryDates = payload.dailyEntries.mapNotNull { it.date.takeIf { d -> d.isNotBlank() } }.sorted()

                val preview = LutealBackupPreview(
                    schemaVersion = payload.schemaVersion,
                    exportedAt = payload.exportedAt,
                    appVersion = payload.appVersion,
                    cycleCount = payload.cycles.size,
                    earliestCycleDate = cycleDates.firstOrNull(),
                    latestCycleDate = cycleDates.lastOrNull(),
                    dailyEntryCount = payload.dailyEntries.size,
                    earliestEntryDate = entryDates.firstOrNull(),
                    latestEntryDate = entryDates.lastOrNull(),
                    symptomLogCount = payload.symptomLogs.size,
                    biomarkerCount = payload.biomarkerObservations.size,
                    preferences = payload.preferences
                )

                Pair(preview, payload)
            }
        }
    }

    suspend fun restoreBackup(payload: LutealBackupPayload, strategy: ImportStrategy): Result<ImportSummary> {
        return withContext(Dispatchers.IO) {
            runCatching {
                if (payload.schemaVersion != 1) {
                    throw DataImportError.UnsupportedSchemaVersion(payload.schemaVersion)
                }

                var cyclesCount = 0
                var entriesCount = 0
                var symptomsCount = 0
                var biomarkersCount = 0

                database.withTransaction {
                    val previousStates = if (strategy == ImportStrategy.REPLACE_ALL) {
                        syncStateDao.getAllStates()
                    } else {
                        emptyList()
                    }
                    val previousCycleIds = if (strategy == ImportStrategy.REPLACE_ALL) {
                        (cycleDao.getAllCyclesOnce().map { it.id } +
                            previousStates.filter { it.entityType == SyncStateEntity.TYPE_CYCLE }.map { it.entityId })
                            .toSet()
                    } else {
                        emptySet()
                    }
                    val previousEntryDates = if (strategy == ImportStrategy.REPLACE_ALL) {
                        (dailyEntryDao.getAllEntriesOnce().map { it.date } +
                            previousStates.filter { it.entityType == SyncStateEntity.TYPE_DAILY_ENTRY }.map { it.entityId })
                            .toSet()
                    } else {
                        emptySet()
                    }
                    val previousSymptomIds = if (strategy == ImportStrategy.REPLACE_ALL) {
                        (symptomDao.getAllSymptomLogsOnce().map { it.id } +
                            previousStates.filter { it.entityType == SyncStateEntity.TYPE_SYMPTOM_LOG }.map { it.entityId })
                            .toSet()
                    } else {
                        emptySet()
                    }
                    val previousBiomarkerIds = if (strategy == ImportStrategy.REPLACE_ALL) {
                        (biomarkerDao.getAllObservationsOnce().map { SyncStateEntity.biomarkerEntityId(it.date) } +
                            previousStates.filter { it.entityType == SyncStateEntity.TYPE_BIOMARKER_OBSERVATION }.map { it.entityId })
                            .toSet()
                    } else {
                        emptySet()
                    }

                    if (strategy == ImportStrategy.REPLACE_ALL) {
                        cycleDao.deleteAllCycles()
                        dailyEntryDao.deleteAllEntries()
                        symptomDao.deleteAllSymptomLogs()
                        biomarkerDao.deleteAll()
                        syncStateDao.deleteAll()
                    }

                    val now = System.currentTimeMillis()

                    // Restore Cycles
                    for (cycleDto in payload.cycles) {
                        if (cycleDto.id.isBlank() || cycleDto.startDate.isBlank()) continue

                        val periodDaysJsonArr = JSONArray()
                        for (pd in cycleDto.periodDays) {
                            val obj = JSONObject()
                            obj.put("date", pd.date)
                            obj.put("bleedingIntensity", pd.bleedingIntensity)
                            obj.put("notes", pd.notes)
                            val symptomsArr = JSONArray()
                            for (s in pd.symptomIds) {
                                symptomsArr.put(s)
                            }
                            obj.put("symptomIds", symptomsArr)
                            periodDaysJsonArr.put(obj)
                        }

                        val entity = CycleEntity(
                            id = cycleDto.id,
                            startDate = cycleDto.startDate,
                            endDate = cycleDto.endDate,
                            averageLengthDays = cycleDto.averageLengthDays,
                            lutealPhaseLengthDays = cycleDto.lutealPhaseLengthDays,
                            periodDaysJson = periodDaysJsonArr.toString(),
                            isExcludedFromEstimates = cycleDto.isExcludedFromEstimates,
                            exclusionReason = cycleDto.exclusionReason
                        )

                        cycleDao.insertCycle(entity)

                        syncStateDao.upsert(
                            SyncStateEntity(
                                entityType = SyncStateEntity.TYPE_CYCLE,
                                entityId = entity.id,
                                clientRev = UUID.randomUUID().toString(),
                                createdAtEpochMillis = now,
                                updatedAtEpochMillis = now,
                                dirty = true,
                                lastPushError = null
                            )
                        )
                        cyclesCount++
                    }

                    // Restore Daily Entries
                    for (entryDto in payload.dailyEntries) {
                        if (entryDto.date.isBlank()) continue

                        val symptomsArr = JSONArray()
                        for (s in entryDto.symptomIds) {
                            symptomsArr.put(s)
                        }

                        val updatedAtMillis = runCatching {
                            Instant.parse(entryDto.updatedAt).toEpochMilli()
                        }.getOrDefault(now)

                        val entity = DailyEntryEntity(
                            date = entryDto.date,
                            bleedingIntensity = entryDto.bleedingIntensity,
                            painLevel = entryDto.painLevel?.coerceIn(1, 5),
                            moodLevel = entryDto.moodLevel?.coerceIn(1, 5),
                            energyLevel = entryDto.energyLevel?.coerceIn(1, 5),
                            symptomIdsJson = symptomsArr.toString(),
                            notes = entryDto.notes,
                            updatedAtEpochMillis = updatedAtMillis
                        )

                        if (strategy == ImportStrategy.MERGE_UPSERT) {
                            val existing = dailyEntryDao.getEntryOnce(entryDto.date)
                            if (existing == null || updatedAtMillis >= existing.updatedAtEpochMillis) {
                                dailyEntryDao.upsert(entity)
                                entriesCount++
                            }
                        } else {
                            dailyEntryDao.upsert(entity)
                            entriesCount++
                        }

                        syncStateDao.upsert(
                            SyncStateEntity(
                                entityType = SyncStateEntity.TYPE_DAILY_ENTRY,
                                entityId = entity.date,
                                clientRev = UUID.randomUUID().toString(),
                                createdAtEpochMillis = updatedAtMillis,
                                updatedAtEpochMillis = now,
                                dirty = true,
                                lastPushError = null
                            )
                        )
                    }

                    // Restore Symptom Logs
                    for (logDto in payload.symptomLogs) {
                        if (logDto.id.isBlank() || logDto.date.isBlank()) continue

                        val timestampMillis = runCatching {
                            Instant.parse(logDto.timestamp).toEpochMilli()
                        }.getOrDefault(now)

                         val entity = SymptomLogEntity(
                            id = logDto.id,
                            timestampEpochMillis = timestampMillis,
                            date = logDto.date,
                            symptomId = logDto.symptomId,
                            severity = logDto.severity.coerceIn(0, 5),
                            notes = logDto.notes
                        )

                        if (strategy == ImportStrategy.MERGE_UPSERT) {
                            val existing = symptomDao.getSymptomLogOnce(logDto.id)
                            if (existing != null && timestampMillis < existing.timestampEpochMillis) {
                                continue
                            }
                        }

                        symptomDao.insertSymptomLog(entity)

                        syncStateDao.upsert(
                            SyncStateEntity(
                                entityType = SyncStateEntity.TYPE_SYMPTOM_LOG,
                                entityId = entity.id,
                                clientRev = UUID.randomUUID().toString(),
                                createdAtEpochMillis = timestampMillis,
                                updatedAtEpochMillis = now,
                                dirty = true,
                                lastPushError = null
                            )
                        )
                        symptomsCount++
                    }

                    for (biomarkerDto in payload.biomarkerObservations) {
                        if (biomarkerDto.date.isBlank()) continue
                        val updatedAtMillis = runCatching {
                            Instant.parse(biomarkerDto.updatedAt).toEpochMilli()
                        }.getOrDefault(now)
                        val disturbances = JSONArray()
                        for (item in biomarkerDto.bbtDisturbances) {
                            disturbances.put(item)
                        }
                        val entity = BiomarkerObservationEntity(
                            date = biomarkerDto.date,
                            bbtCelsius = biomarkerDto.bbtCelsius,
                            bbtTime = biomarkerDto.bbtTime,
                            bbtQuality = biomarkerDto.bbtQuality,
                            bbtDisturbancesJson = disturbances.toString(),
                            cervicalSensation = biomarkerDto.cervicalSensation,
                            cervicalTexture = biomarkerDto.cervicalTexture,
                            lhTestResult = biomarkerDto.lhTestResult,
                            hcgTestResult = biomarkerDto.hcgTestResult,
                            notes = biomarkerDto.notes,
                            updatedAtEpochMillis = updatedAtMillis
                        )
                        if (strategy == ImportStrategy.MERGE_UPSERT) {
                            val existing = biomarkerDao.getObservationOnce(biomarkerDto.date)
                            if (existing == null || updatedAtMillis >= existing.updatedAtEpochMillis) {
                                biomarkerDao.upsert(entity)
                                biomarkersCount++
                            }
                        } else {
                            biomarkerDao.upsert(entity)
                            biomarkersCount++
                        }
                        syncStateDao.upsert(
                            SyncStateEntity(
                                entityType = SyncStateEntity.TYPE_BIOMARKER_OBSERVATION,
                                entityId = SyncStateEntity.biomarkerEntityId(entity.date),
                                clientRev = UUID.randomUUID().toString(),
                                createdAtEpochMillis = updatedAtMillis,
                                updatedAtEpochMillis = now,
                                dirty = true,
                                lastPushError = null
                            )
                        )
                    }

                    if (strategy == ImportStrategy.REPLACE_ALL) {
                        val importedCycleIds = payload.cycles.map { it.id }.toSet()
                        val importedEntryDates = payload.dailyEntries.map { it.date }.toSet()
                        val importedSymptomIds = payload.symptomLogs.map { it.id }.toSet()
                        val importedBiomarkerIds = payload.biomarkerObservations
                            .map { SyncStateEntity.biomarkerEntityId(it.date) }
                            .toSet()
                        suspend fun tombstone(entityId: String, entityType: String) {
                            syncStateDao.upsert(
                                SyncStateEntity(
                                    entityId = entityId,
                                    entityType = entityType,
                                    clientRev = UUID.randomUUID().toString(),
                                    createdAtEpochMillis = now,
                                    updatedAtEpochMillis = now,
                                    deletedAtEpochMillis = now,
                                    dirty = true,
                                    lastPushError = null
                                )
                            )
                        }
                        (previousCycleIds - importedCycleIds).forEach { tombstone(it, SyncStateEntity.TYPE_CYCLE) }
                        (previousEntryDates - importedEntryDates).forEach { tombstone(it, SyncStateEntity.TYPE_DAILY_ENTRY) }
                        (previousSymptomIds - importedSymptomIds).forEach { tombstone(it, SyncStateEntity.TYPE_SYMPTOM_LOG) }
                        (previousBiomarkerIds - importedBiomarkerIds).forEach {
                            tombstone(it, SyncStateEntity.TYPE_BIOMARKER_OBSERVATION)
                        }
                    }
                }

                var preferencesRestored = false
                if (strategy == ImportStrategy.REPLACE_ALL) {
                    val prefs = payload.preferences
                    userPreferencesDataStore.setUserRole(prefs.userRole)
                    userPreferencesDataStore.setLocale(prefs.locale)
                    userPreferencesDataStore.setDisorderTracking("pmdd", prefs.trackPmdd)
                    userPreferencesDataStore.setDisorderTracking("pms", prefs.trackPms)
                    userPreferencesDataStore.setDisorderTracking("endometriosis", prefs.trackEndometriosis)
                    userPreferencesDataStore.setDisorderTracking("pcos", prefs.trackPcos)
                    userPreferencesDataStore.setDisorderTracking("perimenopause", prefs.trackPerimenopause)
                    userPreferencesDataStore.setDisorderTracking("thyroid", prefs.trackThyroid)
                    userPreferencesDataStore.setAgeBand(prefs.ageBand)
                    userPreferencesDataStore.setTemperatureUnit(prefs.temperatureUnit)
                    userPreferencesDataStore.setCompletedOnboarding(true)
                    preferencesRestored = true
                }

                ImportSummary(
                    cyclesImported = cyclesCount,
                    dailyEntriesImported = entriesCount,
                    symptomLogsImported = symptomsCount,
                    biomarkersImported = biomarkersCount,
                    preferencesRestored = preferencesRestored
                )
            }
        }
    }
}
