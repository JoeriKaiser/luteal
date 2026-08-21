package fr.luteal.core.network.sync

import fr.luteal.core.data.entity.SyncStateEntity
import fr.luteal.core.data.local.BiomarkerDao
import fr.luteal.core.data.local.DailyEntryDao
import fr.luteal.core.data.local.SyncStateDao
import fr.luteal.core.data.local.SymptomDao
import fr.luteal.core.data.entity.DailyEntryEntity
import fr.luteal.core.data.entity.SymptomLogEntity
import fr.luteal.core.data.repository.CycleRepository
import fr.luteal.core.model.Cycle
import fr.luteal.core.model.PeriodDay
import fr.luteal.core.network.FolicularApiException
import fr.luteal.core.network.FolicularApiClient
import fr.luteal.core.network.PushChangeWire
import fr.luteal.core.network.PullChangeWire
import fr.luteal.core.network.auth.SyncCredentialStore
import fr.luteal.core.network.crypto.RecordSealer
import fr.luteal.core.network.toPushChange
import fr.luteal.core.network.openPayload
import fr.luteal.core.network.openCurrent
import fr.luteal.core.network.auth.SyncCredentials
import fr.luteal.core.network.contract.models.CycleData
import fr.luteal.core.network.contract.models.Certainty
import fr.luteal.core.network.contract.models.RecordSource
import fr.luteal.core.network.contract.models.EntityType
import fr.luteal.core.network.mapping.SyncMeta
import fr.luteal.core.network.mapping.associatePeriodDays
import fr.luteal.core.network.mapping.fanOutBleeding
import fr.luteal.core.network.mapping.toCycle
import fr.luteal.core.network.mapping.toCycleData
import fr.luteal.core.network.mapping.toDailyEntryData
import fr.luteal.core.network.mapping.toSymptomLogData
import fr.luteal.core.network.mapping.toDailyEntry
import fr.luteal.core.network.mapping.toSymptomLog
import fr.luteal.core.network.mapping.toBleedingObservation
import fr.luteal.core.network.toBleedingObservationData
import fr.luteal.core.network.toCycleData
import fr.luteal.core.network.toDailyEntryData
import fr.luteal.core.network.toSymptomLogData
import fr.luteal.core.network.toBiomarkerObservationPayload
import fr.luteal.core.network.toJsonElement
import fr.luteal.core.network.mapping.deterministicId
import fr.luteal.core.network.mapping.toDomainObservation
import fr.luteal.core.network.mapping.toEntity
import fr.luteal.core.network.mapping.toPayload
import fr.luteal.core.model.DailyEntry
import fr.luteal.core.model.SymptomLog
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Creates an API client for a resolved base URL (dev targeting). */
fun interface FolicularApiClientFactory {
    fun create(baseUrl: String): FolicularApiClient
}

/**
 * Non-secret transport state the engine needs: the pull cursor and the base
 * URL. Backed by DataStore in production; faked in unit tests.
 */
interface SyncCursorStore {
    suspend fun getCursor(): Long
    suspend fun setCursor(cursor: Long)
    suspend fun getBaseUrl(): String

    /**
     * Stable, non-identifying label for this device, generated once and
     * persisted. Deliberately not the hardware model: see
     * [fr.luteal.core.network.auth.DeviceLabel].
     */
    suspend fun getDeviceLabel(): String
}

/** Outcome of one sync pass. Contains no credentials. */
data class SyncReport(
    val registered: Boolean,
    val pushedRecords: Int,
    val appliedByServer: Int,
    val rejectedDetails: List<String>,
    val conflictsAdopted: Int,
    val pulledChanges: Int,
    val recordsApplied: Int,
    val tombstonesApplied: Int,
    val cursor: Long
) {
    val hadRejections: Boolean get() = rejectedDetails.isNotEmpty()
}

/**
 * The server rejected the device token (HTTP 401). Deliberately terminal:
 * credentials stay stored — the account code they carry is the only recovery
 * credential — and reconnecting is an explicit user action via Settings
 * (recover account with the code). Auto-wiping here would silently orphan the
 * existing account and its history on the next register-if-needed pass.
 */
class SyncAuthException :
    RuntimeException("Device token rejected by server; manual reconnection required")

/**
 * Full-entity sync engine.
 *
 * Flow of one [sync] pass:
 *  1. register-if-needed (anonymous account; the device token is reused on
 *     later runs),
 *  2. push all dirty local records (cycles + fanned bleeding, daily entries,
 *     symptom logs), handling applied / rejected / conflicts,
 *  3. pull every change since the stored cursor and apply to Room,
 *  4. advance and persist the cursor.
 *
 * Local writes never go through this class; they land in Room directly and are
 * reconciled here in the background. The server is the source of truth: on
 * conflict we adopt its record, never silently drop.
 */
class CycleSyncEngine(
    private val cycleRepository: CycleRepository,
    private val syncStateDao: SyncStateDao,
    private val dailyEntryDao: DailyEntryDao,
    private val symptomDao: SymptomDao,
    private val biomarkerDao: BiomarkerDao,
    private val credentialStore: SyncCredentialStore,
    private val apiClientFactory: FolicularApiClientFactory,
    private val cursorStore: SyncCursorStore,
    private val recordSealer: RecordSealer
) {

    suspend fun sync(): SyncReport {
        val client = apiClientFactory.create(cursorStore.getBaseUrl())
        try {
            val (credentials, registered) = ensureRegistered(client)
            val token = credentials.deviceToken

            val push = pushDirty(client, token)
            val pull = pullAndApply(client, token)
            return SyncReport(
                registered = registered,
                pushedRecords = push.pushedRecords,
                appliedByServer = push.appliedByServer,
                rejectedDetails = push.rejectedDetails,
                conflictsAdopted = push.conflictsAdopted,
                pulledChanges = pull.pulledChanges,
                recordsApplied = pull.recordsApplied,
                tombstonesApplied = pull.tombstonesApplied,
                cursor = pull.cursor
            )
        } catch (e: FolicularApiException) {
            if (e.status == 401) throw SyncAuthException()
            throw e
        }
    }

    // --- 1. Register if needed ---------------------------------------------

    private suspend fun ensureRegistered(client: FolicularApiClient): Pair<SyncCredentials, Boolean> {
        credentialStore.load()?.let { return it to false }
        val response = client.register(cursorStore.getDeviceLabel())
        val credentials = SyncCredentials(
            accountId = response.account.id.toString(),
            accountCode = response.account.code,
            deviceToken = response.device.token
        )
        credentialStore.save(credentials)
        return credentials to true
    }

    // --- 2. Push all dirty records -----------------------------------------

    private data class PushOutcome(
        val pushedRecords: Int,
        val appliedByServer: Int,
        val rejectedDetails: List<String>,
        val conflictsAdopted: Int
    )

    private suspend fun pushDirty(client: FolicularApiClient, token: String): PushOutcome {
        val dirtyStates = syncStateDao.getDirtyStates()
        if (dirtyStates.isEmpty()) return PushOutcome(0, 0, emptyList(), 0)

        val changes = mutableListOf<PushChangeWire>()
        val pushedIds = mutableSetOf<String>()
        val localIdByWireId = mutableMapOf<UUID, String>()
        val pushedRevByWireId = mutableMapOf<UUID, String>()

        // Group dirty states by type for efficient lookup.
        val cyclesById = cycleRepository.getCyclesOnce().associateBy { it.id }

        for (state in dirtyStates) {
            val meta = state.toSyncMeta()
            when (state.entityType) {
                SyncStateEntity.TYPE_CYCLE -> {
                    val cycle = cyclesById[state.entityId]
                    if (cycle == null) {
                        pushCycleTombstone(state, meta, changes, pushedIds)
                    } else {
                        pushCycle(cycle, state, meta, changes, pushedIds)
                    }
                }
                SyncStateEntity.TYPE_DAILY_ENTRY -> {
                    pushDailyEntry(state, meta, changes, pushedIds)
                }
                SyncStateEntity.TYPE_SYMPTOM_LOG -> {
                    pushSymptomLog(state, meta, changes, pushedIds)
                }
                SyncStateEntity.TYPE_BIOMARKER_OBSERVATION -> {
                    pushBiomarker(state, meta, changes, pushedIds)
                }
                else -> {
                    // Unknown type (e.g. bleeding_observation pushed as part of
                    // a cycle fan-out): clean up orphaned state.
                    syncStateDao.delete(state.entityId)
                }
            }
        }

        if (changes.isEmpty()) return PushOutcome(0, 0, emptyList(), 0)
        for (state in dirtyStates) {
            wireIdFor(state)?.let {
                localIdByWireId[it] = state.entityId
                pushedRevByWireId[it] = state.clientRev
            }
        }

        // No runCatching here: a 401 propagates to [sync], which converts it
        // into a terminal SyncAuthException without touching stored
        // credentials.
        val result = client.syncPush(token, changes)

        var appliedCount = 0
        for (applied in result.applied) {
            val localId = localIdByWireId[applied.entityId] ?: applied.entityId.toString()
            val pushedRev = pushedRevByWireId[applied.entityId]
            val state = syncStateDao.getState(localId)
            if (state != null && pushedRev != null && state.clientRev == pushedRev) {
                // Envelope unchanged since the push snapshot: safe to ack.
                // A concurrent local edit writes a fresh clientRev and keeps
                // the envelope dirty, so the newer change is pushed on a
                // later pass instead of being stranded by markClean.
                if (state.deletedAtEpochMillis != null) {
                    syncStateDao.deleteIfRev(localId, pushedRev)
                } else {
                    syncStateDao.markCleanIfRev(localId, pushedRev)
                }
            }
            appliedCount++
        }

        val rejectedDetails = result.rejected.map { rejected ->
            val wireId = rejected.entityId
            val localId = wireId?.let { localIdByWireId[it] ?: it.toString() }
            if (localId != null) {
                syncStateDao.markRejected(localId, rejected.detail)
            }
            "${rejected.entityType.value}: ${rejected.detail}"
        }

        var conflictsAdopted = 0
        for (conflict in result.conflicts) {
            when (conflict.entityType) {
                EntityType.CYCLE -> {
                    if (conflict.currentDeleted) {
                        cycleRepository.deleteCycle(conflict.entityId.toString())
                        syncStateDao.delete(conflict.entityId.toString())
                        conflictsAdopted++
                        continue
                    }
                    val serverCycle = conflict.openCurrent(recordSealer)?.toCycleData() ?: continue
                    val localPeriodDays = cycleRepository.getCyclesOnce()
                        .firstOrNull { it.id == serverCycle.id.toString() }
                        ?.periodDays.orEmpty()
                    adoptCycle(serverCycle, localPeriodDays)
                    conflictsAdopted++
                }
                EntityType.DAILY_ENTRY -> {
                    if (conflict.currentDeleted) {
                        adoptDailyEntryTombstone(conflict.entityId)
                        conflictsAdopted++
                        continue
                    }
                    val serverEntry = conflict.openCurrent(recordSealer)?.toDailyEntryData() ?: continue
                    adoptDailyEntry(serverEntry)
                    conflictsAdopted++
                }
                EntityType.SYMPTOM_LOG -> {
                    if (conflict.currentDeleted) {
                        symptomDao.deleteSymptomLog(conflict.entityId.toString())
                        syncStateDao.delete(conflict.entityId.toString())
                        conflictsAdopted++
                        continue
                    }
                    val serverLog = conflict.openCurrent(recordSealer)?.toSymptomLogData() ?: continue
                    adoptSymptomLog(serverLog)
                    conflictsAdopted++
                }
                EntityType.BIOMARKER_OBSERVATION -> {
                    if (conflict.currentDeleted) {
                        adoptBiomarkerTombstone(conflict.entityId)
                        conflictsAdopted++
                        continue
                    }
                    val server = conflict.openCurrent(recordSealer)?.toBiomarkerObservationPayload() ?: continue
                    adoptBiomarker(server)
                    conflictsAdopted++
                }
                else -> { /* bleeding conflicts are resolved via cycle re-pull */ }
            }
        }

        return PushOutcome(pushedIds.size, appliedCount, rejectedDetails, conflictsAdopted)
    }

    private suspend fun pushCycleTombstone(
        state: SyncStateEntity, meta: SyncMeta,
        changes: MutableList<PushChangeWire>, pushedIds: MutableSet<String>
    ) {
        if (state.deletedAtEpochMillis != null) {
            val parsedId = runCatching { UUID.fromString(state.entityId) }.getOrNull()
            if (parsedId == null) {
                syncStateDao.delete(state.entityId)
                return
            }
            val tombstone = CycleData(
                id = parsedId, clientRev = meta.clientRev,
                createdAt = meta.createdAt, updatedAt = meta.updatedAt,
                deletedAt = meta.deletedAt,
                startDate = LocalDate.now(), endDate = null,
                lengthDays = null, bleedingDays = null,
                certainty = Certainty.RECORDED, source = RecordSource.MANUAL, notes = ""
            )
            changes += tombstone.toPushChange(recordSealer)
            pushedIds += state.entityId
        } else {
            syncStateDao.delete(state.entityId)
        }
    }

    private suspend fun pushCycle(
        cycle: Cycle, state: SyncStateEntity, meta: SyncMeta,
        changes: MutableList<PushChangeWire>, pushedIds: MutableSet<String>
    ) {
        val cycleId = runCatching { UUID.fromString(cycle.id) }.getOrNull()
        if (cycleId == null) {
            syncStateDao.markRejected(state.entityId, "identifiant de cycle invalide")
            return
        }
        val cycleData = cycle.toCycleData(meta)
        changes += cycleData.toPushChange(recordSealer)
        fanOutBleeding(cycle.id, cycle.periodDays, meta).forEach { observation ->
            changes += observation.toPushChange(recordSealer)
        }
        pushedIds += cycle.id
    }

    private suspend fun pushDailyEntry(
        state: SyncStateEntity, meta: SyncMeta,
        changes: MutableList<PushChangeWire>, pushedIds: MutableSet<String>
    ) {
        val date = runCatching { LocalDate.parse(state.entityId) }.getOrNull()
        if (date == null) {
            syncStateDao.delete(state.entityId)
            return
        }
        // Reconstruct a DailyEntry from the Room entity for mapping.
        // The DAO doesn't have a suspend getter, so we build from the state.
        // For tombstones, push a deleted record.
        if (state.deletedAtEpochMillis != null) {
            val entry = DailyEntry(date = date, updatedAt = Instant.ofEpochMilli(state.updatedAtEpochMillis))
            val data = entry.toDailyEntryData(meta)
            changes += data.toPushChange(recordSealer)
            pushedIds += state.entityId
            return
        }
        // Live entry: read from Room via the observable (blocking not ideal but
        // the DAO only exposes Flow; for sync we need a one-shot read).
        // We stored the date as entityId; the entry data was already written to
        // Room by the repository. Re-read is safe because Room is local.
        val entryEntity = dailyEntryDao.getEntryOnce(date.toString())
        if (entryEntity == null) {
            syncStateDao.delete(state.entityId)
            return
        }
        val entry = DailyEntry(
            date = LocalDate.parse(entryEntity.date),
            bleedingIntensity = entryEntity.bleedingIntensity?.let { name ->
                fr.luteal.core.model.BleedingIntensity.entries.firstOrNull { it.name == name }
            },
            painLevel = entryEntity.painLevel,
            moodLevel = entryEntity.moodLevel,
            energyLevel = entryEntity.energyLevel,
            notes = entryEntity.notes,
            updatedAt = Instant.ofEpochMilli(entryEntity.updatedAtEpochMillis)
        )
        val data = entry.toDailyEntryData(meta)
        changes += data.toPushChange(recordSealer)
        // Also fan out bleeding from the daily entry if it has one.
        entry.bleedingIntensity?.let {
            val bleeding = entry.toBleedingObservation(meta)
            if (bleeding != null) {
                changes += bleeding.toPushChange(recordSealer)
            }
        }
        pushedIds += state.entityId
    }

    private suspend fun pushSymptomLog(
        state: SyncStateEntity, meta: SyncMeta,
        changes: MutableList<PushChangeWire>, pushedIds: MutableSet<String>
    ) {
        val logId = runCatching { UUID.fromString(state.entityId) }.getOrNull()
        if (logId == null) {
            syncStateDao.delete(state.entityId)
            return
        }
        if (state.deletedAtEpochMillis != null) {
            // Tombstone: push a deleted symptom log.
            val log = SymptomLog(
                id = state.entityId, timestamp = Instant.ofEpochMilli(state.updatedAtEpochMillis),
                date = LocalDate.now(), symptomId = "unknown", severity = 1
            )
            val data = log.toSymptomLogData(meta)
            changes += data.toPushChange(recordSealer)
            pushedIds += state.entityId
            return
        }
        val logEntity = symptomDao.getSymptomLogOnce(state.entityId)
        if (logEntity == null) {
            syncStateDao.delete(state.entityId)
            return
        }
        val log = SymptomLog(
            id = logEntity.id,
            timestamp = Instant.ofEpochMilli(logEntity.timestampEpochMillis),
            date = LocalDate.parse(logEntity.date),
            symptomId = logEntity.symptomId,
            severity = logEntity.severity,
            notes = logEntity.notes
        )
        val data = log.toSymptomLogData(meta)
        changes += data.toPushChange(recordSealer)
        pushedIds += state.entityId
    }

    private suspend fun pushBiomarker(
        state: SyncStateEntity,
        meta: SyncMeta,
        changes: MutableList<PushChangeWire>,
        pushedIds: MutableSet<String>
    ) {
        val date = SyncStateEntity.biomarkerDateFromEntityId(state.entityId)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (date == null) {
            syncStateDao.delete(state.entityId)
            return
        }
        if (state.deletedAtEpochMillis != null) {
            val data = fr.luteal.core.model.BiomarkerObservation(date = date).toPayload(meta)
            changes += data.toPushChange(recordSealer)
            pushedIds += state.entityId
            return
        }
        val entity = biomarkerDao.getObservationOnce(date.toString())
        if (entity == null) {
            syncStateDao.delete(state.entityId)
            return
        }
        val observation = entity.toDomainObservation()
        changes += observation.toPayload(meta).toPushChange(recordSealer)
        pushedIds += state.entityId
    }

    // --- 3. Pull since cursor and apply ------------------------------------

    private data class PullOutcome(
        val pulledChanges: Int,
        val recordsApplied: Int,
        val tombstonesApplied: Int,
        val cursor: Long
    )

    private suspend fun pullAndApply(client: FolicularApiClient, token: String): PullOutcome {
        var since = cursorStore.getCursor()
        var pulledChanges = 0
        var recordsApplied = 0
        var tombstones = 0

        while (true) {
            // A 401 propagates to [sync] as a terminal auth failure;
            // credentials are never cleared here.
            val result = client.syncPull(token, since)

            val applied = applyPage(result.changes)
            recordsApplied += applied.recordsApplied
            tombstones += applied.tombstonesApplied
            pulledChanges += result.changes.size

            since = result.cursor
            cursorStore.setCursor(since)
            if (!result.hasMore) break
        }

        return PullOutcome(pulledChanges, recordsApplied, tombstones, since)
    }

    private data class PageOutcome(val recordsApplied: Int, val tombstonesApplied: Int)

    private suspend fun applyPage(changes: List<PullChangeWire>): PageOutcome {
        val localCyclesById = cycleRepository.getCyclesOnce().associateBy { it.id }

        // Decode live bleeding observations for cycle period-day association.
        val bleeding = changes
            .filter { it.entityType == EntityType.BLEEDING_OBSERVATION && !it.deleted }
            .mapNotNull {
                runCatching { it.openPayload(recordSealer)?.toBleedingObservationData() }.getOrNull()
            }

        var recordsApplied = 0
        var tombstones = 0

        for (change in changes) {
            when (change.entityType) {
                EntityType.CYCLE -> {
                    val cycleId = change.entityId.toString()
                    if (change.deleted) {
                        cycleRepository.deleteCycle(cycleId)
                        tombstones++
                    } else {
                        val cycleData = runCatching {
                            change.openPayload(recordSealer)?.toCycleData()
                        }.getOrNull() ?: continue
                        val periodDays = associatePeriodDays(
                            startDate = cycleData.startDate,
                            endDate = cycleData.endDate,
                            observations = bleeding,
                            fallback = localCyclesById[cycleId]?.periodDays.orEmpty()
                        )
                        adoptCycle(cycleData, periodDays, localCyclesById[cycleId])
                        recordsApplied++
                    }
                }
                EntityType.DAILY_ENTRY -> {
                    if (change.deleted) {
                        adoptDailyEntryTombstone(change.entityId)
                        tombstones++
                    } else {
                        val entryData = runCatching {
                            change.openPayload(recordSealer)?.toDailyEntryData()
                        }.getOrNull() ?: continue
                        adoptDailyEntry(entryData)
                        recordsApplied++
                    }
                }
                EntityType.SYMPTOM_LOG -> {
                    val logId = change.entityId.toString()
                    if (change.deleted) {
                        symptomDao.deleteSymptomLog(logId)
                        syncStateDao.delete(logId)
                        tombstones++
                    } else {
                        val logData = runCatching {
                            change.openPayload(recordSealer)?.toSymptomLogData()
                        }.getOrNull() ?: continue
                        adoptSymptomLog(logData)
                        recordsApplied++
                    }
                }
                EntityType.BIOMARKER_OBSERVATION -> {
                    if (change.deleted) {
                        adoptBiomarkerTombstone(change.entityId)
                        tombstones++
                    } else {
                        val payload = runCatching {
                            change.openPayload(recordSealer)?.toBiomarkerObservationPayload()
                        }.getOrNull() ?: continue
                        adoptBiomarker(payload)
                        recordsApplied++
                    }
                }
                else -> {
                    // BLEEDING_OBSERVATION and other types are handled via their
                    // parent entity (cycle period-day association above).
                }
            }
        }

        return PageOutcome(recordsApplied, tombstones)
    }

    // --- Adopt server-authoritative records --------------------------------

    private suspend fun adoptCycle(
        cycleData: CycleData,
        periodDays: List<PeriodDay>,
        displayHints: Cycle? = null
    ) {
        val cycle = cycleData.toCycle(periodDays).copy(
            averageLengthDays = displayHints?.averageLengthDays ?: 28,
            lutealPhaseLengthDays = displayHints?.lutealPhaseLengthDays ?: 14
        )
        cycleRepository.upsertCycle(cycle)
        syncStateDao.upsert(cycleData.toCleanState(SyncStateEntity.TYPE_CYCLE))
    }

    private suspend fun adoptDailyEntry(entryData: fr.luteal.core.network.contract.models.DailyEntryData) {
        val entry = entryData.toDailyEntry()
        dailyEntryDao.upsert(
            DailyEntryEntity(
                date = entry.date.toString(),
                bleedingIntensity = null, // bleeding comes via bleeding_observations
                painLevel = entry.painLevel,
                moodLevel = entry.moodLevel,
                energyLevel = entry.energyLevel,
                symptomIdsJson = "[]",
                notes = entry.notes,
                updatedAtEpochMillis = entry.updatedAt.toEpochMilli()
            )
        )
        syncStateDao.upsert(SyncStateEntity(
            entityId = entry.date.toString(),
            entityType = SyncStateEntity.TYPE_DAILY_ENTRY,
            clientRev = entryData.clientRev.toString(),
            createdAtEpochMillis = entryData.createdAt.toInstant().toEpochMilli(),
            updatedAtEpochMillis = entryData.updatedAt.toInstant().toEpochMilli(),
            deletedAtEpochMillis = entryData.deletedAt?.toInstant()?.toEpochMilli(),
            dirty = false,
            lastPushError = null
        ))
    }

    private suspend fun adoptSymptomLog(logData: fr.luteal.core.network.contract.models.SymptomLogData) {
        val log = logData.toSymptomLog()
        symptomDao.insertSymptomLog(
            SymptomLogEntity(
                id = log.id,
                date = log.date.toString(),
                timestampEpochMillis = log.timestamp.toEpochMilli(),
                symptomId = log.symptomId,
                severity = log.severity,
                notes = log.notes,
                isSynced = true
            )
        )
        syncStateDao.upsert(SyncStateEntity(
            entityId = logData.id.toString(),
            entityType = SyncStateEntity.TYPE_SYMPTOM_LOG,
            clientRev = logData.clientRev.toString(),
            createdAtEpochMillis = logData.createdAt.toInstant().toEpochMilli(),
            updatedAtEpochMillis = logData.updatedAt.toInstant().toEpochMilli(),
            deletedAtEpochMillis = logData.deletedAt?.toInstant()?.toEpochMilli(),
            dirty = false,
            lastPushError = null
        ))
    }

    private suspend fun adoptBiomarker(payload: fr.luteal.core.network.mapping.BiomarkerObservationPayload) {
        biomarkerDao.upsert(payload.toEntity())
        syncStateDao.upsert(
            SyncStateEntity(
                entityId = SyncStateEntity.biomarkerEntityId(payload.observedDate),
                entityType = SyncStateEntity.TYPE_BIOMARKER_OBSERVATION,
                clientRev = payload.clientRev,
                createdAtEpochMillis = payload.wireUpdatedAt.toInstant().toEpochMilli(),
                updatedAtEpochMillis = payload.wireUpdatedAt.toInstant().toEpochMilli(),
                deletedAtEpochMillis = payload.wireDeletedAt?.toInstant()?.toEpochMilli(),
                dirty = false,
                lastPushError = null
            )
        )
    }

    private suspend fun adoptBiomarkerTombstone(wireId: UUID) {
        val localId = resolveBiomarkerLocalId(wireId)
        val date = localId?.let { SyncStateEntity.biomarkerDateFromEntityId(it) }
            ?: biomarkerDao.getAllObservationsOnce().firstOrNull {
                deterministicId("biomarker", it.date) == wireId
            }?.date
        if (date != null) {
            biomarkerDao.deleteForDate(date)
            syncStateDao.delete(SyncStateEntity.biomarkerEntityId(date))
        } else if (localId != null) {
            syncStateDao.delete(localId)
        }
    }

    private suspend fun adoptDailyEntryTombstone(wireId: UUID) {
        val date = dailyEntryDao.getAllEntriesOnce().firstOrNull {
            deterministicId("daily-entry", it.date) == wireId
        }?.date
            ?: syncStateDao.getAllStates()
                .firstOrNull { it.entityType == SyncStateEntity.TYPE_DAILY_ENTRY && wireIdFor(it) == wireId }
                ?.entityId
        if (date != null) {
            dailyEntryDao.delete(date)
            syncStateDao.delete(date)
        }
        syncStateDao.delete(wireId.toString())
    }

    private suspend fun resolveBiomarkerLocalId(wireId: UUID): String? {
        syncStateDao.getAllStates()
            .firstOrNull { it.entityType == SyncStateEntity.TYPE_BIOMARKER_OBSERVATION && wireIdFor(it) == wireId }
            ?.entityId
            ?.let { return it }
        return biomarkerDao.getAllObservationsOnce().firstOrNull {
            deterministicId("biomarker", it.date) == wireId
        }?.let { SyncStateEntity.biomarkerEntityId(it.date) }
    }

    private fun wireIdFor(state: SyncStateEntity): UUID? = when (state.entityType) {
        SyncStateEntity.TYPE_CYCLE, SyncStateEntity.TYPE_SYMPTOM_LOG ->
            runCatching { UUID.fromString(state.entityId) }.getOrNull()
        SyncStateEntity.TYPE_DAILY_ENTRY ->
            runCatching { LocalDate.parse(state.entityId) }.getOrNull()
                ?.let { deterministicId("daily-entry", it.toString()) }
                ?: runCatching { UUID.fromString(state.entityId) }.getOrNull()
        SyncStateEntity.TYPE_BIOMARKER_OBSERVATION ->
            SyncStateEntity.biomarkerDateFromEntityId(state.entityId)
                ?.let { deterministicId("biomarker", it) }
        else -> null
    }

    // --- Envelope mapping ---------------------------------------------------

    private fun SyncStateEntity.toSyncMeta(): SyncMeta = SyncMeta(
        clientRev = UUID.fromString(clientRev),
        createdAt = createdAtEpochMillis.toCoarseUtc(),
        updatedAt = updatedAtEpochMillis.toCoarseUtc(),
        deletedAt = deletedAtEpochMillis?.toCoarseUtc()
    )

    private fun CycleData.toCleanState(entityType: String): SyncStateEntity = SyncStateEntity(
        entityId = id.toString(),
        entityType = entityType,
        clientRev = clientRev.toString(),
        createdAtEpochMillis = createdAt.toInstant().toEpochMilli(),
        updatedAtEpochMillis = updatedAt.toInstant().toEpochMilli(),
        deletedAtEpochMillis = deletedAt?.toInstant()?.toEpochMilli(),
        dirty = false,
        lastPushError = null
    )

    private fun Long.toUtcOffsetDateTime(): OffsetDateTime =
        OffsetDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneOffset.UTC)

    /**
     * Envelope timestamps, normalised to UTC and truncated to the minute.
     *
     * These stay readable by the server even under end-to-end encryption,
     * because delta pull orders by them. Millisecond precision would let the
     * server reconstruct exactly when each observation was entered, which is
     * behavioural data it has no need for: an offline-first client pushes a
     * batch long after the fact, so without truncation the batch itself
     * carries a minute-by-minute timeline of the user's evening.
     *
     * Minute granularity keeps last-write-wins well behaved (client_rev
     * remains the documented tiebreak) while removing that timeline.
     */
    private fun Long.toCoarseUtc(): OffsetDateTime =
        toUtcOffsetDateTime().truncatedTo(ChronoUnit.MINUTES)

}
