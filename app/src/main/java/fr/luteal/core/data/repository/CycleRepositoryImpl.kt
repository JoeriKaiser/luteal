package fr.luteal.core.data.repository

import androidx.room.withTransaction
import fr.luteal.core.data.entity.CycleEntity
import fr.luteal.core.data.entity.DailyEntryEntity
import fr.luteal.core.data.entity.SyncStateEntity
import fr.luteal.core.data.local.CycleDao
import fr.luteal.core.data.local.DailyEntryDao
import fr.luteal.core.data.local.LutealDatabase
import fr.luteal.core.data.local.SyncStateDao
import fr.luteal.core.model.BleedingIntensity
import fr.luteal.core.model.Cycle
import fr.luteal.core.model.PeriodDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
    private val database: LutealDatabase,
    private val cycleDao: CycleDao,
    private val dailyEntryDao: DailyEntryDao,
    private val syncStateDao: SyncStateDao,
    private val clock: Clock
) : CycleRepository {

    override fun getCycles(): Flow<List<Cycle>> {
        return combine(cycleDao.getAllCycles(), dailyEntryDao.observeEntries()) { cycleEntities, dailyEntries ->
            cycleEntities.map { it.toDomain(dailyEntries) }
        }
    }

    override fun getCurrentCycle(): Flow<Cycle?> {
        return combine(cycleDao.getCurrentCycle(), dailyEntryDao.observeEntries()) { entity, dailyEntries ->
            entity?.toDomain(dailyEntries)
        }
    }

    override suspend fun getCyclesOnce(): List<Cycle> {
        val cycleEntities = cycleDao.getAllCyclesOnce()
        val dailyEntries = dailyEntryDao.getAllEntriesOnce()
        return cycleEntities.map { it.toDomain(dailyEntries) }
    }

    override suspend fun saveCycle(cycle: Cycle) {
        // Entity and envelope must land atomically: a crash between them
        // would leave an unpushed edit (or a resurrected deletion).
        database.withTransaction {
            upsertCycle(cycle)
            markDirty(cycle.id)
        }
    }

    override suspend fun upsertCycle(cycle: Cycle) {
        cycleDao.insertCycle(cycle.toEntity())
    }

    override suspend fun updateCycleExclusion(
        id: String,
        isExcluded: Boolean,
        reason: fr.luteal.core.model.CycleExclusionReason?
    ) {
        database.withTransaction {
            cycleDao.updateExclusion(id, isExcluded, reason?.name?.lowercase())
            markDirty(id)
        }
    }

    override suspend fun deleteCycle(id: String) {
        database.withTransaction {
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
    }

    override suspend fun addBackfilledCycle(startDate: LocalDate) {
        database.withTransaction {
            val existing = cycleDao.getAllCyclesOnce()
            require(existing.none { LocalDate.parse(it.startDate) == startDate }) {
                "Un cycle existe déjà à cette date."
            }

            val newCycle = CycleEntity(
                id = UUID.randomUUID().toString(),
                startDate = startDate.toString(),
                endDate = null,
                periodDaysJson = "[]",
                averageLengthDays = 28,
                lutealPhaseLengthDays = 14,
                isSynced = false,
                isExcludedFromEstimates = false,
                exclusionReason = null
            )

            val updatedList = (existing + newCycle).sortedBy { LocalDate.parse(it.startDate) }

            for (i in updatedList.indices) {
                val current = updatedList[i]
                val nextStart = updatedList.getOrNull(i + 1)?.let { LocalDate.parse(it.startDate) }
                val newEndDate = nextStart?.minusDays(1)?.toString()
                if (current.id == newCycle.id || current.endDate != newEndDate) {
                    cycleDao.insertCycle(current.copy(endDate = newEndDate))
                    markDirty(current.id)
                }
            }
        }
    }

    override suspend fun editCycleStartDate(cycleId: String, newStartDate: LocalDate) {
        database.withTransaction {
            val existing = cycleDao.getAllCyclesOnce()
            val target = existing.firstOrNull { it.id == cycleId }
                ?: error("Cycle introuvable.")
            if (LocalDate.parse(target.startDate) == newStartDate) return@withTransaction

            require(existing.none { it.id != cycleId && LocalDate.parse(it.startDate) == newStartDate }) {
                "Un autre cycle commence déjà à cette date."
            }

            val updatedList = existing.map {
                if (it.id == cycleId) it.copy(startDate = newStartDate.toString()) else it
            }.sortedBy { LocalDate.parse(it.startDate) }

            for (i in updatedList.indices) {
                val current = updatedList[i]
                val nextStart = updatedList.getOrNull(i + 1)?.let { LocalDate.parse(it.startDate) }
                val newEndDate = nextStart?.minusDays(1)?.toString()
                if (current.id == cycleId || current.endDate != newEndDate) {
                    cycleDao.insertCycle(current.copy(endDate = newEndDate))
                    markDirty(current.id)
                }
            }
        }
    }

    override suspend fun deleteCycleAndReconcile(cycleId: String) {
        database.withTransaction {
            deleteCycle(cycleId)
            val remaining = cycleDao.getAllCyclesOnce().sortedBy { LocalDate.parse(it.startDate) }
            for (i in remaining.indices) {
                val current = remaining[i]
                val nextStart = remaining.getOrNull(i + 1)?.let { LocalDate.parse(it.startDate) }
                val newEndDate = nextStart?.minusDays(1)?.toString()
                if (current.endDate != newEndDate) {
                    cycleDao.insertCycle(current.copy(endDate = newEndDate))
                    markDirty(current.id)
                }
            }
        }
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

    private fun CycleEntity.toDomain(entries: List<DailyEntryEntity> = emptyList()): Cycle {
        val cycleStart = LocalDate.parse(startDate)
        val cycleEnd = endDate?.let { LocalDate.parse(it) } ?: LocalDate.now(clock)

        val bleedingPeriodDays = entries.mapNotNull { entry ->
            val entryDate = runCatching { LocalDate.parse(entry.date) }.getOrNull() ?: return@mapNotNull null
            if (entryDate in cycleStart..cycleEnd) {
                val intensity = entry.bleedingIntensity?.let { stored ->
                    BleedingIntensity.entries.firstOrNull { it.name.equals(stored, ignoreCase = true) }
                }
                if (intensity != null && intensity != BleedingIntensity.NONE) {
                    PeriodDay(
                        date = entryDate,
                        bleedingIntensity = intensity,
                        notes = entry.notes,
                        symptomIds = entry.symptomIdsJson.toSymptomIds()
                    )
                } else null
            } else null
        }.sortedBy { it.date }

        val resolvedPeriodDays = if (bleedingPeriodDays.isNotEmpty()) {
            bleedingPeriodDays
        } else {
            periodDaysJson.toPeriodDays()
        }

        return Cycle(
            id = id,
            startDate = cycleStart,
            endDate = endDate?.let { LocalDate.parse(it) },
            averageLengthDays = averageLengthDays,
            lutealPhaseLengthDays = lutealPhaseLengthDays,
            periodDays = resolvedPeriodDays,
            isExcludedFromEstimates = isExcludedFromEstimates,
            exclusionReason = fr.luteal.core.model.CycleExclusionReason.fromKey(exclusionReason)
        )
    }

    private fun String.toSymptomIds(): List<String> {
        if (isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(this)
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
            list
        }.getOrDefault(emptyList())
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
