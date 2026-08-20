package fr.luteal.core.data.repository

import fr.luteal.core.data.entity.CycleEntity
import fr.luteal.core.data.entity.SyncStateEntity
import fr.luteal.core.data.local.CycleDao
import fr.luteal.core.data.local.SyncStateDao
import fr.luteal.core.model.BleedingIntensity
import fr.luteal.core.model.Cycle
import fr.luteal.core.model.PeriodDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CycleRepositoryImpl @Inject constructor(
    private val cycleDao: CycleDao,
    private val syncStateDao: SyncStateDao,
    private val clock: Clock
) : CycleRepository {

    override fun getCycles(): Flow<List<Cycle>> {
        return cycleDao.getAllCycles().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getCurrentCycle(): Flow<Cycle?> {
        return cycleDao.getCurrentCycle().map { entity ->
            entity?.toDomain()
        }
    }

    override suspend fun getCyclesOnce(): List<Cycle> {
        return cycleDao.getAllCyclesOnce().map { it.toDomain() }
    }

    override suspend fun saveCycle(cycle: Cycle) {
        upsertCycle(cycle)
        markDirty(cycle.id)
    }

    override suspend fun upsertCycle(cycle: Cycle) {
        cycleDao.insertCycle(cycle.toEntity())
    }

    override suspend fun updateCycleExclusion(
        id: String,
        isExcluded: Boolean,
        reason: fr.luteal.core.model.CycleExclusionReason?
    ) {
        cycleDao.updateExclusion(id, isExcluded, reason?.name?.lowercase())
        markDirty(id)
    }

    override suspend fun deleteCycle(id: String) {
        val now = clock.millis()
        val existingState = syncStateDao.getState(id)
        cycleDao.deleteCycle(id)
        val stateToSave = existingState?.copy(
            clientRev = UUID.randomUUID().toString(),
            updatedAtEpochMillis = now,
            deletedAtEpochMillis = now,
            dirty = true,
            lastPushError = null
        ) ?: SyncStateEntity(
            entityId = id,
            entityType = SyncStateEntity.TYPE_CYCLE,
            clientRev = UUID.randomUUID().toString(),
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            deletedAtEpochMillis = now,
            dirty = true,
            lastPushError = null
        )
        syncStateDao.upsert(stateToSave)
    }

    /**
     * Records a fresh envelope for a locally edited cycle: a new client_rev on
     * every edit (the conflict tiebreak), created_at preserved across edits,
     * updated_at bumped to now, and the dirty flag set so the sync worker
     * pushes it. This is a local Room write only - it never waits on network.
     */
    private suspend fun markDirty(cycleId: String) {
        val now = clock.millis()
        val existing = syncStateDao.getState(cycleId)
        syncStateDao.upsert(
            SyncStateEntity(
                entityId = cycleId,
                entityType = SyncStateEntity.TYPE_CYCLE,
                clientRev = UUID.randomUUID().toString(),
                createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
                updatedAtEpochMillis = now,
                deletedAtEpochMillis = null,
                dirty = true,
                lastPushError = null
            )
        )
    }

    private fun CycleEntity.toDomain(): Cycle {
        return Cycle(
            id = id,
            startDate = LocalDate.parse(startDate),
            endDate = endDate?.let { LocalDate.parse(it) },
            averageLengthDays = averageLengthDays,
            lutealPhaseLengthDays = lutealPhaseLengthDays,
            periodDays = periodDaysJson.toPeriodDays(),
            isExcludedFromEstimates = isExcludedFromEstimates,
            exclusionReason = fr.luteal.core.model.CycleExclusionReason.fromKey(exclusionReason)
        )
    }

    private fun Cycle.toEntity(): CycleEntity {
        return CycleEntity(
            id = id,
            startDate = startDate.toString(),
            endDate = endDate?.toString(),
            periodDaysJson = periodDays.toJson(),
            averageLengthDays = averageLengthDays,
            lutealPhaseLengthDays = lutealPhaseLengthDays,
            isSynced = false,
            isExcludedFromEstimates = isExcludedFromEstimates,
            exclusionReason = exclusionReason?.name?.lowercase()
        )
    }
    private fun List<PeriodDay>.toJson(): String {
        val array = JSONArray()
        for (day in this) {
            val obj = JSONObject().apply {
                put("date", day.date.toString())
                put("bleedingIntensity", day.bleedingIntensity.name)
                put("notes", day.notes)
                val symptomsArr = JSONArray()
                day.symptomIds.forEach { symptomsArr.put(it) }
                put("symptomIds", symptomsArr)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun String.toPeriodDays(): List<PeriodDay> {
        if (isBlank()) return emptyList()
        return try {
            val array = JSONArray(this)
            val list = mutableListOf<PeriodDay>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val date = LocalDate.parse(obj.getString("date"))
                val bleedingIntensity = try {
                    BleedingIntensity.valueOf(obj.getString("bleedingIntensity"))
                } catch (e: Exception) {
                    BleedingIntensity.MEDIUM
                }
                val notes = obj.optString("notes", "")
                val symptomIds = mutableListOf<String>()
                val symptomsArr = obj.optJSONArray("symptomIds")
                if (symptomsArr != null) {
                    for (j in 0 until symptomsArr.length()) {
                        symptomIds.add(symptomsArr.getString(j))
                    }
                }
                list.add(PeriodDay(date, bleedingIntensity, notes, symptomIds))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }
}
