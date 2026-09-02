package fr.luteal.core.network.sync

import fr.luteal.core.data.entity.BiomarkerObservationEntity
import fr.luteal.core.data.entity.DailyEntryEntity
import fr.luteal.core.data.entity.SyncStateEntity
import fr.luteal.core.data.entity.SymptomLogEntity
import fr.luteal.core.data.local.BiomarkerDao
import fr.luteal.core.data.local.DailyEntryDao
import fr.luteal.core.data.local.SyncStateDao
import fr.luteal.core.data.local.SymptomDao
import fr.luteal.core.data.repository.CycleRepository
import fr.luteal.core.model.Cycle
import fr.luteal.core.network.FolicularApiClient
import fr.luteal.core.network.PullResultWire
import fr.luteal.core.network.PushChangeWire
import fr.luteal.core.network.PushResultWire
import fr.luteal.core.network.auth.SyncCredentialStore
import fr.luteal.core.network.auth.SyncCredentials
import fr.luteal.core.network.contract.models.Register201Response
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import java.util.UUID
/** In-memory [CycleRepository] for engine tests. */
class FakeCycleRepository(initial: List<Cycle> = emptyList()) : CycleRepository {
    val cycles = LinkedHashMap<String, Cycle>().apply { initial.associateByTo(this) { it.id } }
    val upsertedIds = mutableListOf<String>()
    val deletedIds = mutableListOf<String>()
    var onDelete: (suspend (String) -> Unit)? = null

    override fun getCycles(): Flow<List<Cycle>> = flowOf(cycles.values.toList())
    override fun getCurrentCycle(): Flow<Cycle?> = flowOf(cycles.values.firstOrNull { it.endDate == null })
    override suspend fun getCyclesOnce(): List<Cycle> = cycles.values.toList()

    override suspend fun saveCycle(cycle: Cycle) {
        cycles[cycle.id] = cycle
    }

    override suspend fun upsertCycle(cycle: Cycle) {
        cycles[cycle.id] = cycle
        upsertedIds += cycle.id
    }

    override suspend fun deleteCycle(id: String) {
        cycles.remove(id)
        deletedIds += id
        onDelete?.invoke(id)
    }

    override suspend fun updateCycleExclusion(
        id: String,
        isExcluded: Boolean,
        reason: fr.luteal.core.model.CycleExclusionReason?
    ) {
        cycles[id]?.let {
            cycles[id] = it.copy(isExcludedFromEstimates = isExcluded, exclusionReason = reason)
        }
    }

    override suspend fun addBackfilledCycle(startDate: LocalDate) {
        val newCycle = Cycle(
            id = UUID.randomUUID().toString(),
            startDate = startDate
        )
        val all = (cycles.values + newCycle).sortedBy { it.startDate }
        for (i in all.indices) {
            val current = all[i]
            val nextStart = all.getOrNull(i + 1)?.startDate
            cycles[current.id] = current.copy(endDate = nextStart?.minusDays(1))
        }
    }

    override suspend fun editCycleStartDate(cycleId: String, newStartDate: LocalDate) {
        val all = cycles.values.map {
            if (it.id == cycleId) it.copy(startDate = newStartDate) else it
        }.sortedBy { it.startDate }
        for (i in all.indices) {
            val current = all[i]
            val nextStart = all.getOrNull(i + 1)?.startDate
            cycles[current.id] = current.copy(endDate = nextStart?.minusDays(1))
        }
    }

    override suspend fun deleteCycleAndReconcile(cycleId: String) {
        deleteCycle(cycleId)
        val remaining = cycles.values.sortedBy { it.startDate }
        for (i in remaining.indices) {
            val current = remaining[i]
            val nextStart = remaining.getOrNull(i + 1)?.startDate
            cycles[current.id] = current.copy(endDate = nextStart?.minusDays(1))
        }
    }
}

/** In-memory [SyncStateDao] for engine tests. */
class FakeSyncStateDao : SyncStateDao {
    val states = LinkedHashMap<String, SyncStateEntity>()

    override suspend fun getState(entityId: String): SyncStateEntity? = states[entityId]

    override suspend fun getDirtyStates(): List<SyncStateEntity> =
        states.values.filter { it.dirty }

    override suspend fun getDirtyStatesByType(entityType: String): List<SyncStateEntity> =
        states.values.filter { it.dirty && it.entityType == entityType }

    override suspend fun getAllStates(): List<SyncStateEntity> = states.values.toList()

    override suspend fun upsert(state: SyncStateEntity) {
        states[state.entityId] = state
    }

    override suspend fun markCleanIfRev(entityId: String, clientRev: String): Int {
        val current = states[entityId] ?: return 0
        if (current.clientRev != clientRev) return 0
        states[entityId] = current.copy(dirty = false, lastPushError = null)
        return 1
    }

    override suspend fun deleteIfRev(entityId: String, clientRev: String): Int {
        val current = states[entityId] ?: return 0
        if (current.clientRev != clientRev) return 0
        states.remove(entityId)
        return 1
    }

    override suspend fun markPushError(entityId: String, detail: String) {
        states[entityId]?.let { states[entityId] = it.copy(lastPushError = detail) }
    }

    override suspend fun markRejected(entityId: String, detail: String) {
        states[entityId]?.let { states[entityId] = it.copy(dirty = false, lastPushError = detail) }
    }

    override suspend fun delete(entityId: String) {
        states.remove(entityId)
    }

    override suspend fun deleteAll() {
        states.clear()
    }
}

/** In-memory [DailyEntryDao] for engine tests. */
class FakeDailyEntryDao : DailyEntryDao {
    val entries = LinkedHashMap<String, DailyEntryEntity>()

    override fun observeEntries() = kotlinx.coroutines.flow.flowOf(entries.values.toList())
    override suspend fun getAllEntriesOnce(): List<DailyEntryEntity> = entries.values.toList()
    override fun observeEntry(date: String) = kotlinx.coroutines.flow.flowOf(entries[date])
    override suspend fun getEntryOnce(date: String): DailyEntryEntity? = entries[date]
    override suspend fun upsert(entry: DailyEntryEntity) { entries[entry.date] = entry }
    override suspend fun delete(date: String) { entries.remove(date) }
    override suspend fun deleteAllEntries() { entries.clear() }
}

/** In-memory [BiomarkerDao] for engine tests. */
class FakeBiomarkerDao : BiomarkerDao {
    val observations = LinkedHashMap<String, BiomarkerObservationEntity>()

    override fun observeObservations() = kotlinx.coroutines.flow.flowOf(observations.values.toList())
    override suspend fun getAllObservationsOnce() = observations.values.toList()
    override fun observeObservation(date: String) = kotlinx.coroutines.flow.flowOf(observations[date])
    override suspend fun getObservationOnce(date: String) = observations[date]
    override fun getObservationsBetween(startDate: String, endDate: String) =
        kotlinx.coroutines.flow.flowOf(
            observations.values.filter { it.date >= startDate && it.date <= endDate }
        )
    override suspend fun upsert(entity: BiomarkerObservationEntity) {
        observations[entity.date] = entity
    }
    override suspend fun deleteForDate(date: String) {
        observations.remove(date)
    }
    override suspend fun deleteAll() {
        observations.clear()
    }
}

/** In-memory [SymptomDao] for engine tests. */
class FakeSymptomDao : SymptomDao {
    val logs = LinkedHashMap<String, SymptomLogEntity>()

    override fun getSymptomsForDate(dateString: String) =
        kotlinx.coroutines.flow.flowOf(logs.values.filter { it.date == dateString })
    override fun getAllSymptomLogs() =
        kotlinx.coroutines.flow.flowOf(logs.values.toList())
    override suspend fun getAllSymptomLogsOnce(): List<SymptomLogEntity> = logs.values.toList()
    override suspend fun getSymptomLogOnce(id: String): SymptomLogEntity? = logs[id]
    override suspend fun insertSymptomLog(log: SymptomLogEntity) { logs[log.id] = log }
    override suspend fun deleteSymptomLog(id: String) { logs.remove(id) }
    override suspend fun deleteAllSymptomLogs() { logs.clear() }
}

/** In-memory [SyncCredentialStore] for engine tests. */
class FakeCredentialStore(var credentials: SyncCredentials? = null) : SyncCredentialStore {
    override fun load(): SyncCredentials? = credentials
    override fun save(credentials: SyncCredentials) {
        this.credentials = credentials
    }

    override fun clear() {
        credentials = null
    }
}

/** In-memory [SyncCursorStore] for engine tests. */
class FakeCursorStore(
    private var cursor: Long = 0L,
    private val baseUrl: String = "http://test.local:8080",
    private val deviceLabel: String = "test-device"
) : SyncCursorStore {
    override suspend fun getCursor(): Long = cursor
    override suspend fun setCursor(cursor: Long) {
        this.cursor = cursor
    }

    override suspend fun getBaseUrl(): String = baseUrl
    override suspend fun getDeviceLabel(): String = deviceLabel
    override suspend fun clear() {
        cursor = 0L
    }
}

/** Scripted [FolicularApiClient] for engine tests. */
class FakeApiClient : FolicularApiClient {
    lateinit var registerResponse: Register201Response
    var pushResult: PushResultWire = PushResultWire(emptyList(), emptyList(), emptyList(), 0L)
    var pullResults: ArrayDeque<PullResultWire> = ArrayDeque()
    var pushError: Throwable? = null
    var pullError: Throwable? = null

    /** Runs inside [syncPush] before the result is returned: simulates a
     * local edit landing during the network round trip. */
    var onPush: (suspend () -> Unit)? = null

    var registerCalls = 0
        private set
    val registerInviteCodes = mutableListOf<String>()
    val pushCalls = mutableListOf<List<PushChangeWire>>()
    val pullSinceValues = mutableListOf<Long>()

    override suspend fun register(deviceName: String, inviteCode: String?): Register201Response {
        registerCalls++
        if (inviteCode != null) registerInviteCodes += inviteCode
        return registerResponse
    }

    override suspend fun syncPush(deviceToken: String, changes: List<PushChangeWire>): PushResultWire {
        pushError?.let { throw it }
        onPush?.invoke()
        pushCalls += changes
        return pushResult
    }

    override suspend fun syncPull(deviceToken: String, since: Long, limit: Int): PullResultWire {
        pullError?.let { throw it }
        pullSinceValues += since
        return if (pullResults.isNotEmpty()) pullResults.removeFirst() else
            PullResultWire(emptyList(), since, false)
    }

    // --- Duo stubs (not exercised by sync engine tests) ---
    override suspend fun createInvitation(deviceToken: String) =
        throw UnsupportedOperationException("not used in sync tests")
    override suspend fun acceptLink(deviceToken: String, pairingCode: String) =
        throw UnsupportedOperationException("not used in sync tests")
    override suspend fun listLinks(deviceToken: String) =
        throw UnsupportedOperationException("not used in sync tests")
    override suspend fun patchGrants(deviceToken: String, linkId: String, field: fr.luteal.core.network.contract.models.GrantField, granted: Boolean) =
        throw UnsupportedOperationException("not used in sync tests")
    override suspend fun revokeLink(deviceToken: String, linkId: String) =
        throw UnsupportedOperationException("not used in sync tests")
    override suspend fun duoView(deviceToken: String) =
        throw UnsupportedOperationException("not used in sync tests")
    override suspend fun addDevice(accountCode: String, deviceName: String) =
        throw UnsupportedOperationException("not used in sync tests")
    override suspend fun putDuoPayload(deviceToken: String, payload: String) =
        throw UnsupportedOperationException("not used in sync tests")
    override suspend fun createSupportRequest(deviceToken: String, linkId: String, kind: fr.luteal.core.network.contract.models.SupportKind, sealedMessage: String) =
        throw UnsupportedOperationException("not used in sync tests")
    override suspend fun ackSupportRequest(deviceToken: String, requestId: String) =
        throw UnsupportedOperationException("not used in sync tests")
}
