package fr.luteal.core.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import fr.luteal.core.data.entity.DailyEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyEntryDao {
    @Query("SELECT * FROM daily_entries ORDER BY date DESC")
    fun observeEntries(): Flow<List<DailyEntryEntity>>

    @Query("SELECT * FROM daily_entries WHERE date = :date LIMIT 1")
    fun observeEntry(date: String): Flow<DailyEntryEntity?>

    @Upsert
    suspend fun upsert(entry: DailyEntryEntity)

    @Query("DELETE FROM daily_entries WHERE date = :date")
    suspend fun delete(date: String)

    @Query("SELECT * FROM daily_entries WHERE date = :date LIMIT 1")
    suspend fun getEntryOnce(date: String): DailyEntryEntity?

    @Query("DELETE FROM daily_entries")
    suspend fun deleteAllEntries()
}
