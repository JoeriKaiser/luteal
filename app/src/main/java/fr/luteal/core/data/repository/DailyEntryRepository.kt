package fr.luteal.core.data.repository

import fr.luteal.core.model.DailyEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface DailyEntryRepository {
    fun observeEntries(): Flow<List<DailyEntry>>
    fun observeEntry(date: LocalDate): Flow<DailyEntry?>
    suspend fun save(entry: DailyEntry)
    suspend fun delete(date: LocalDate)
}
