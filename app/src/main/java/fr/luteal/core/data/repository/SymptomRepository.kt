package fr.luteal.core.data.repository

import fr.luteal.core.model.SymptomLog
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface SymptomRepository {
    fun getSymptomsForDate(date: LocalDate): Flow<List<SymptomLog>>
    fun getAllSymptomLogs(): Flow<List<SymptomLog>>
    suspend fun logSymptom(log: SymptomLog)
    suspend fun deleteSymptomLog(id: String)
}
