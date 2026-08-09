package fr.luteal.core.data.repository

import fr.luteal.core.data.entity.DuoWidgetCacheEntity
import fr.luteal.core.data.local.DuoWidgetCacheDao
import fr.luteal.core.model.CachedDuoCycleProjection
import fr.luteal.core.model.DuoCycleProjectionStatus
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Local cache for the already grant-filtered cycle subset of a Duo projection. */
@Singleton
class DuoWidgetCacheRepository @Inject constructor(
    private val dao: DuoWidgetCacheDao
) {
    fun observeLatest(): Flow<CachedDuoCycleProjection?> =
        dao.observeLatest().map { it?.toDomain() }

    suspend fun getLatest(): CachedDuoCycleProjection? = dao.getLatest()?.toDomain()

    suspend fun save(value: CachedDuoCycleProjection) {
        // There is one active Duo in the current product surface. Clearing also
        // prevents a revoked or replaced link from becoming a fallback row.
        dao.clear()
        dao.upsert(value.toEntity())
    }

    suspend fun clear() = dao.clear()

    private fun DuoWidgetCacheEntity.toDomain() = CachedDuoCycleProjection(
        linkId = linkId,
        role = role,
        cycleDay = cycleDay,
        estimateStart = estimateStart?.let(LocalDate::parse),
        estimateEnd = estimateEnd?.let(LocalDate::parse),
        cycleDayGranted = cycleDayGranted,
        estimateGranted = estimateGranted,
        status = runCatching { DuoCycleProjectionStatus.valueOf(status) }
            .getOrDefault(DuoCycleProjectionStatus.INVALID_PAYLOAD),
        refreshedAt = Instant.ofEpochMilli(refreshedAtEpochMillis)
    )

    private fun CachedDuoCycleProjection.toEntity() = DuoWidgetCacheEntity(
        linkId = linkId,
        role = role,
        cycleDay = cycleDay,
        estimateStart = estimateStart?.toString(),
        estimateEnd = estimateEnd?.toString(),
        cycleDayGranted = cycleDayGranted,
        estimateGranted = estimateGranted,
        status = status.name,
        refreshedAtEpochMillis = refreshedAt.toEpochMilli()
    )
}
