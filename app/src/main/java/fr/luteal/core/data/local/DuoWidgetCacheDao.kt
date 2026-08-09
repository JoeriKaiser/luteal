package fr.luteal.core.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import fr.luteal.core.data.entity.DuoWidgetCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DuoWidgetCacheDao {
    @Query("SELECT * FROM duo_widget_cache ORDER BY refreshedAtEpochMillis DESC LIMIT 1")
    fun observeLatest(): Flow<DuoWidgetCacheEntity?>

    @Query("SELECT * FROM duo_widget_cache ORDER BY refreshedAtEpochMillis DESC LIMIT 1")
    suspend fun getLatest(): DuoWidgetCacheEntity?

    @Upsert
    suspend fun upsert(entity: DuoWidgetCacheEntity)

    @Query("DELETE FROM duo_widget_cache")
    suspend fun clear()
}
