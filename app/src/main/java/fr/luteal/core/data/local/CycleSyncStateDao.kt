package fr.luteal.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import fr.luteal.core.data.entity.CycleSyncStateEntity

@Dao
interface CycleSyncStateDao {

    @Query("SELECT * FROM cycle_sync_state WHERE cycleId = :cycleId")
    suspend fun getState(cycleId: String): CycleSyncStateEntity?

    @Query("SELECT * FROM cycle_sync_state WHERE dirty = 1")
    suspend fun getDirtyStates(): List<CycleSyncStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: CycleSyncStateEntity)

    @Query("UPDATE cycle_sync_state SET dirty = 0, lastPushError = NULL WHERE cycleId = :cycleId")
    suspend fun markClean(cycleId: String)

    @Query("UPDATE cycle_sync_state SET lastPushError = :detail WHERE cycleId = :cycleId")
    suspend fun markPushError(cycleId: String, detail: String)

    @Query("UPDATE cycle_sync_state SET dirty = 0, lastPushError = :detail WHERE cycleId = :cycleId")
    suspend fun markRejected(cycleId: String, detail: String)

    @Query("DELETE FROM cycle_sync_state WHERE cycleId = :cycleId")
    suspend fun delete(cycleId: String)

    @Query("DELETE FROM cycle_sync_state")
    suspend fun deleteAllSyncStates()
}
