package fr.luteal.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import fr.luteal.core.data.entity.SyncStateEntity

@Dao
interface SyncStateDao {

    @Query("SELECT * FROM sync_state WHERE entityId = :entityId")
    suspend fun getState(entityId: String): SyncStateEntity?

    @Query("SELECT * FROM sync_state WHERE dirty = 1")
    suspend fun getDirtyStates(): List<SyncStateEntity>

    @Query("SELECT * FROM sync_state WHERE dirty = 1 AND entityType = :entityType")
    suspend fun getDirtyStatesByType(entityType: String): List<SyncStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SyncStateEntity)

    @Query("UPDATE sync_state SET dirty = 0, lastPushError = NULL WHERE entityId = :entityId")
    suspend fun markClean(entityId: String)

    @Query("UPDATE sync_state SET lastPushError = :detail WHERE entityId = :entityId")
    suspend fun markPushError(entityId: String, detail: String)

    @Query("UPDATE sync_state SET dirty = 0, lastPushError = :detail WHERE entityId = :entityId")
    suspend fun markRejected(entityId: String, detail: String)

    @Query("DELETE FROM sync_state WHERE entityId = :entityId")
    suspend fun delete(entityId: String)

    @Query("DELETE FROM sync_state")
    suspend fun deleteAll()
}
