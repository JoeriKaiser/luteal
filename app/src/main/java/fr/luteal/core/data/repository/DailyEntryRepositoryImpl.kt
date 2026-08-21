package fr.luteal.core.data.repository

import androidx.room.withTransaction
import fr.luteal.core.data.entity.DailyEntryEntity
import fr.luteal.core.data.entity.SyncStateEntity
import fr.luteal.core.data.local.DailyEntryDao
import fr.luteal.core.data.local.LutealDatabase
import fr.luteal.core.data.local.SyncStateDao
import fr.luteal.core.model.BleedingIntensity
import fr.luteal.core.model.DailyEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DailyEntryRepositoryImpl @Inject constructor(
    private val database: LutealDatabase,
    private val dailyEntryDao: DailyEntryDao,
    private val syncStateDao: SyncStateDao,
    private val clock: Clock
) : DailyEntryRepository {
    override fun observeEntries(): Flow<List<DailyEntry>> =
        dailyEntryDao.observeEntries().map { entries -> entries.map { it.toDomain() } }

    override fun observeEntry(date: LocalDate): Flow<DailyEntry?> =
        dailyEntryDao.observeEntry(date.toString()).map { it?.toDomain() }

    override suspend fun getEntryOnce(date: LocalDate): DailyEntry? =
        dailyEntryDao.getEntryOnce(date.toString())?.toDomain()

    override suspend fun save(entry: DailyEntry) {
        // Entity and envelope must land atomically: a crash between them
        // would leave an unpushed edit.
        database.withTransaction {
            dailyEntryDao.upsert(entry.toEntity())
            markDirty(entry.date.toString())
        }
    }

    override suspend fun delete(date: LocalDate) {
        // Local deletions must tombstone the sync envelope, or the next pull
        // resurrects the deleted entry from the server copy.
        val id = date.toString()
        database.withTransaction {
            val now = clock.millis()
            val existing = syncStateDao.getState(id)
            dailyEntryDao.delete(id)
            syncStateDao.upsert(
                SyncStateEntity(
                    entityId = id,
                    entityType = SyncStateEntity.TYPE_DAILY_ENTRY,
                    clientRev = UUID.randomUUID().toString(),
                    createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
                    updatedAtEpochMillis = now,
                    deletedAtEpochMillis = now,
                    dirty = true,
                    lastPushError = null
                )
            )
        }
    }

    private fun DailyEntryEntity.toDomain() = DailyEntry(
        date = LocalDate.parse(date),
        bleedingIntensity = bleedingIntensity?.let { stored ->
            BleedingIntensity.entries.firstOrNull { it.name == stored }
        },
        painLevel = painLevel,
        moodLevel = moodLevel,
        energyLevel = energyLevel,
        symptomIds = symptomIdsJson.toStringSet(),
        notes = notes,
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis)
    )

    private fun DailyEntry.toEntity() = DailyEntryEntity(
        date = date.toString(),
        bleedingIntensity = bleedingIntensity?.name,
        painLevel = painLevel,
        moodLevel = moodLevel,
        energyLevel = energyLevel,
        symptomIdsJson = symptomIds.toJson(),
        notes = notes.trim(),
        updatedAtEpochMillis = updatedAt.toEpochMilli()
    )

    private fun Set<String>.toJson(): String {
        val array = JSONArray()
        sorted().forEach(array::put)
        return array.toString()
    }

    private fun String.toStringSet(): Set<String> = runCatching {
        val array = JSONArray(this)
        buildSet {
            for (index in 0 until array.length()) add(array.getString(index))
        }
    }.getOrDefault(emptySet())

    private suspend fun markDirty(entityId: String) {
        val now = clock.millis()
        val existing = syncStateDao.getState(entityId)
        syncStateDao.upsert(
            SyncStateEntity(
                entityId = entityId,
                entityType = SyncStateEntity.TYPE_DAILY_ENTRY,
                clientRev = UUID.randomUUID().toString(),
                createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
                updatedAtEpochMillis = now,
                deletedAtEpochMillis = null,
                dirty = true,
                lastPushError = null
            )
        )
    }
}
