package fr.luteal.core.data.repository

import fr.luteal.core.model.Cycle
import kotlinx.coroutines.flow.Flow

interface CycleRepository {
    fun getCycles(): Flow<List<Cycle>>
    fun getCurrentCycle(): Flow<Cycle?>
    suspend fun saveCycle(cycle: Cycle)
    suspend fun deleteCycle(id: String)
}
