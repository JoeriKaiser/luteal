package fr.luteal.core.data.repository

import fr.luteal.core.model.CachedDuoCycleProjection
import fr.luteal.core.model.DuoCycleProjectionStatus
import fr.luteal.core.model.DuoProjection
import fr.luteal.core.network.contract.models.GrantField
import fr.luteal.core.network.contract.models.DuoView
import fr.luteal.core.network.crypto.DuoProjectionDecodeResult
import fr.luteal.core.network.crypto.DuoProjectionDecoder
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Persists only the cycle fields that survived Duo grant filtering. */
@Singleton
class DuoCycleProjectionCacheWriter @Inject constructor(
    private val cache: DuoWidgetCacheRepository,
    private val decoder: DuoProjectionDecoder,
    private val clock: Clock
) {
    suspend fun save(view: DuoView) {
        val linkId = view.linkId.toString()
        val existing = cache.getLatest()?.takeIf { it.linkId == linkId }
        val serverGrants = view.grants
        val cycleDayGranted = serverGrants?.contains(GrantField.CYCLE_DAY)
            ?: existing?.cycleDayGranted
            ?: false
        val estimateGranted = serverGrants?.contains(GrantField.PERIOD_ESTIMATE)
            ?: existing?.estimateGranted
            ?: false

        val decoded = decoder.decode(view)
        val projection = (decoded as? DuoProjectionDecodeResult.Available)?.projection
        val status = when (decoded) {
            is DuoProjectionDecodeResult.Available -> DuoCycleProjectionStatus.ACTIVE
            DuoProjectionDecodeResult.NoPayload -> DuoCycleProjectionStatus.NO_PAYLOAD
            DuoProjectionDecodeResult.KeyMissing -> DuoCycleProjectionStatus.KEY_MISSING
            DuoProjectionDecodeResult.InvalidPayload -> DuoCycleProjectionStatus.INVALID_PAYLOAD
        }

        cache.save(
            CachedDuoCycleProjection(
                linkId = linkId,
                role = view.role.name,
                cycleDay = projection?.cycleDay?.takeIf { cycleDayGranted },
                estimateStart = projection?.periodEstimate?.windowStart
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?.takeIf { estimateGranted },
                estimateEnd = projection?.periodEstimate?.windowEnd
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?.takeIf { estimateGranted },
                cycleDayGranted = cycleDayGranted,
                estimateGranted = estimateGranted,
                status = status,
                refreshedAt = Instant.now(clock)
            )
        )
    }

    suspend fun savePublished(
        linkId: String,
        projection: DuoProjection,
        grants: Map<GrantField, Boolean>
    ) {
        val cycleDayGranted = grants[GrantField.CYCLE_DAY] == true
        val estimateGranted = grants[GrantField.PERIOD_ESTIMATE] == true
        cache.save(
            CachedDuoCycleProjection(
                linkId = linkId,
                role = "TRACKER",
                cycleDay = projection.cycleDay.takeIf { cycleDayGranted },
                estimateStart = projection.periodEstimate?.windowStart
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?.takeIf { estimateGranted },
                estimateEnd = projection.periodEstimate?.windowEnd
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?.takeIf { estimateGranted },
                cycleDayGranted = cycleDayGranted,
                estimateGranted = estimateGranted,
                status = DuoCycleProjectionStatus.ACTIVE,
                refreshedAt = Instant.now(clock)
            )
        )
    }
}
