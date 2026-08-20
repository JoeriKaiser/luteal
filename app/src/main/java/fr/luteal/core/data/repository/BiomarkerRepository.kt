package fr.luteal.core.data.repository

import fr.luteal.core.model.BiomarkerObservation
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface BiomarkerRepository {
    fun observeObservations(): Flow<List<BiomarkerObservation>>
    fun observeObservation(date: LocalDate): Flow<BiomarkerObservation?>
    suspend fun getObservationOnce(date: LocalDate): BiomarkerObservation?
    suspend fun save(observation: BiomarkerObservation)
    suspend fun delete(date: LocalDate)
}
