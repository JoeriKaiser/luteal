package fr.luteal.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import fr.luteal.core.data.entity.CycleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CycleDao {
    @Query("SELECT * FROM cycles ORDER BY startDate DESC")
    fun getAllCycles(): Flow<List<CycleEntity>>

    @Query("SELECT * FROM cycles ORDER BY startDate DESC")
    suspend fun getAllCyclesOnce(): List<CycleEntity>

    @Query("SELECT * FROM cycles WHERE id = :id")
    suspend fun getCycleById(id: String): CycleEntity?

    @Query("SELECT * FROM cycles WHERE endDate IS NULL LIMIT 1")
    fun getCurrentCycle(): Flow<CycleEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCycle(cycle: CycleEntity)

    @Update
    suspend fun updateCycle(cycle: CycleEntity)

    @Query("DELETE FROM cycles WHERE id = :id")
    suspend fun deleteCycle(id: String)

    @Query("DELETE FROM cycles")
    suspend fun deleteAllCycles()
}
