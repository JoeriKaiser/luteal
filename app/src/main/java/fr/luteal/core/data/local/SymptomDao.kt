package fr.luteal.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import fr.luteal.core.data.entity.SymptomLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SymptomDao {
    @Query("SELECT * FROM symptom_logs WHERE date = :dateString")
    fun getSymptomsForDate(dateString: String): Flow<List<SymptomLogEntity>>

    @Query("SELECT * FROM symptom_logs ORDER BY timestampEpochMillis DESC")
    fun getAllSymptomLogs(): Flow<List<SymptomLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSymptomLog(log: SymptomLogEntity)

    @Query("DELETE FROM symptom_logs WHERE id = :id")
    suspend fun deleteSymptomLog(id: String)
}
