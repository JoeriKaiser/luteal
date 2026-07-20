package fr.luteal.core.data.repository

import fr.luteal.core.data.entity.SymptomLogEntity
import fr.luteal.core.data.local.SymptomDao
import fr.luteal.core.model.SymptomLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SymptomRepositoryImpl @Inject constructor(
    private val symptomDao: SymptomDao
) : SymptomRepository {

    override fun getSymptomsForDate(date: LocalDate): Flow<List<SymptomLog>> {
        return symptomDao.getSymptomsForDate(date.toString()).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getAllSymptomLogs(): Flow<List<SymptomLog>> {
        return symptomDao.getAllSymptomLogs().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun logSymptom(log: SymptomLog) {
        symptomDao.insertSymptomLog(log.toEntity())
    }

    override suspend fun deleteSymptomLog(id: String) {
        symptomDao.deleteSymptomLog(id)
    }

    private fun SymptomLogEntity.toDomain(): SymptomLog {
        return SymptomLog(
            id = id,
            timestamp = Instant.ofEpochMilli(timestampEpochMillis),
            date = LocalDate.parse(date),
            symptomId = symptomId,
            severity = severity,
            notes = notes
        )
    }

    private fun SymptomLog.toEntity(): SymptomLogEntity {
        return SymptomLogEntity(
            id = id,
            date = date.toString(),
            timestampEpochMillis = timestamp.toEpochMilli(),
            symptomId = symptomId,
            severity = severity,
            notes = notes,
            isSynced = false
        )
    }
}
