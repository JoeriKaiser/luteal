package fr.luteal.core.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import fr.luteal.core.data.entity.BiomarkerObservationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BiomarkerDao {
    @Query("SELECT * FROM biomarker_observations ORDER BY date ASC")
    fun observeObservations(): Flow<List<BiomarkerObservationEntity>>

    @Query("SELECT * FROM biomarker_observations ORDER BY date ASC")
    suspend fun getAllObservationsOnce(): List<BiomarkerObservationEntity>

    @Query("SELECT * FROM biomarker_observations WHERE date = :date LIMIT 1")
    fun observeObservation(date: String): Flow<BiomarkerObservationEntity?>

    @Query("SELECT * FROM biomarker_observations WHERE date = :date LIMIT 1")
    suspend fun getObservationOnce(date: String): BiomarkerObservationEntity?

    @Query("SELECT * FROM biomarker_observations WHERE date >= :startDate AND date <= :endDate ORDER BY date ASC")
    fun getObservationsBetween(startDate: String, endDate: String): Flow<List<BiomarkerObservationEntity>>

    @Upsert
    suspend fun upsert(entity: BiomarkerObservationEntity)

    @Query("DELETE FROM biomarker_observations WHERE date = :date")
    suspend fun deleteForDate(date: String)

    @Query("DELETE FROM biomarker_observations")
    suspend fun deleteAll()
}
