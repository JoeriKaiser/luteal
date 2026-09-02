package fr.luteal.core.data.repository

import fr.luteal.core.model.Cycle
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface CycleRepository {
    fun getCycles(): Flow<List<Cycle>>
    fun getCurrentCycle(): Flow<Cycle?>
    suspend fun getCyclesOnce(): List<Cycle>
    suspend fun saveCycle(cycle: Cycle)

    /**
     * Upserts a cycle WITHOUT marking it dirty for sync. Used by the sync
     * apply path, where the record comes from the server and is already
     * canonical (the sync layer writes the clean envelope separately).
     */
    suspend fun upsertCycle(cycle: Cycle)
    suspend fun deleteCycle(id: String)
    suspend fun updateCycleExclusion(id: String, isExcluded: Boolean, reason: fr.luteal.core.model.CycleExclusionReason?)
    suspend fun addBackfilledCycle(startDate: LocalDate)
    suspend fun editCycleStartDate(cycleId: String, newStartDate: LocalDate)
    suspend fun deleteCycleAndReconcile(cycleId: String)
}
