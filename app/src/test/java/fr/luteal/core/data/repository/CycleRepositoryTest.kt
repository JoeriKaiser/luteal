package fr.luteal.core.data.repository

import fr.luteal.core.data.entity.CycleEntity
import fr.luteal.core.data.entity.SyncStateEntity
import fr.luteal.core.data.local.CycleDao
import fr.luteal.core.data.local.SyncStateDao
import fr.luteal.core.model.Cycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import fr.luteal.core.model.CycleExclusionReason
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class CycleRepositoryTest {

    private val fixedInstant = Instant.parse("2026-08-13T12:00:00Z")
    private val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    private class FakeCycleDao : CycleDao {
        val cycles = mutableMapOf<String, CycleEntity>()

        override fun getAllCycles(): Flow<List<CycleEntity>> = flowOf(cycles.values.sortedByDescending { it.startDate })
        override suspend fun getAllCyclesOnce(): List<CycleEntity> = cycles.values.sortedByDescending { it.startDate }
        override suspend fun getCycleById(id: String): CycleEntity? = cycles[id]
        override fun getCurrentCycle(): Flow<CycleEntity?> = flowOf(cycles.values.firstOrNull { it.endDate == null })
        override suspend fun insertCycle(cycle: CycleEntity) { cycles[cycle.id] = cycle }
        override suspend fun updateCycle(cycle: CycleEntity) { cycles[cycle.id] = cycle }
        override suspend fun deleteCycle(id: String) { cycles.remove(id) }
        override suspend fun deleteAllCycles() { cycles.clear() }
        override suspend fun updateExclusion(id: String, isExcluded: Boolean, reason: String?) {
            cycles[id]?.let {
                cycles[id] = it.copy(isExcludedFromEstimates = isExcluded, exclusionReason = reason)
            }
        }
    }

    private class FakeSyncStateDao : SyncStateDao {
        val states = mutableMapOf<String, SyncStateEntity>()

        override suspend fun getState(entityId: String): SyncStateEntity? = states[entityId]
        override suspend fun getDirtyStates(): List<SyncStateEntity> = states.values.filter { it.dirty }
        override suspend fun getDirtyStatesByType(entityType: String): List<SyncStateEntity> = states.values.filter { it.dirty && it.entityType == entityType }
        override suspend fun getAllStates(): List<SyncStateEntity> = states.values.toList()
        override suspend fun upsert(state: SyncStateEntity) { states[state.entityId] = state }
        override suspend fun markClean(entityId: String) {
            states[entityId]?.let { states[entityId] = it.copy(dirty = false, lastPushError = null) }
        }
        override suspend fun markPushError(entityId: String, detail: String) {
            states[entityId]?.let { states[entityId] = it.copy(lastPushError = detail) }
        }
        override suspend fun markRejected(entityId: String, detail: String) {
            states[entityId]?.let { states[entityId] = it.copy(dirty = false, lastPushError = detail) }
        }
        override suspend fun delete(entityId: String) { states.remove(entityId) }
        override suspend fun deleteAll() { states.clear() }
    }

    @Test
    fun saveCycleInsertsAndMarksDirty() = runTest {
        val cycleDao = FakeCycleDao()
        val syncDao = FakeSyncStateDao()
        val repo = CycleRepositoryImpl(cycleDao, syncDao, clock)

        val cycle = Cycle(
            id = "c1",
            startDate = LocalDate.of(2026, 8, 1),
            endDate = null
        )

        repo.saveCycle(cycle)

        val saved = cycleDao.getCycleById("c1")
        assertNotNull(saved)
        assertEquals("2026-08-01", saved?.startDate)

        val syncState = syncDao.getState("c1")
        assertNotNull(syncState)
        assertTrue(syncState!!.dirty)
        assertEquals(fixedInstant.toEpochMilli(), syncState.updatedAtEpochMillis)
        assertNull(syncState.deletedAtEpochMillis)
    }

    @Test
    fun deleteCycleRemovesAndWritesSyncTombstone() = runTest {
        val cycleDao = FakeCycleDao()
        val syncDao = FakeSyncStateDao()
        val repo = CycleRepositoryImpl(cycleDao, syncDao, clock)

        val cycle = Cycle(
            id = "c1",
            startDate = LocalDate.of(2026, 8, 1),
            endDate = null
        )
        repo.saveCycle(cycle)

        repo.deleteCycle("c1")

        assertNull(cycleDao.getCycleById("c1"))

        val tombstone = syncDao.getState("c1")
        assertNotNull(tombstone)
        assertTrue(tombstone!!.dirty)
        assertEquals(fixedInstant.toEpochMilli(), tombstone.deletedAtEpochMillis)
    }

    @Test
    fun updateCycleExclusionPersistsAndMarksDirty() = runTest {
        val cycleDao = FakeCycleDao()
        val syncDao = FakeSyncStateDao()
        val repo = CycleRepositoryImpl(cycleDao, syncDao, clock)
        val cycle = Cycle(
            id = "cycle-1",
            startDate = LocalDate.parse("2026-06-01"),
            endDate = LocalDate.parse("2026-06-28")
        )
        repo.saveCycle(cycle)
        syncDao.markClean(cycle.id)

        repo.updateCycleExclusion("cycle-1", true, CycleExclusionReason.ILLNESS)

        val updated = repo.getCyclesOnce().first()
        assertTrue(updated.isExcludedFromEstimates)
        assertEquals(CycleExclusionReason.ILLNESS, updated.exclusionReason)

        val state = syncDao.getState("cycle-1")
        assertNotNull(state)
        assertTrue(state!!.dirty)
    }
}
