package fr.luteal.core.network.sync

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class RevisionTicket(
    val updatedAtEpochMillis: Long,
    val clientRev: String,
    val pushable: Boolean
)

interface RevisionWatermarkStore {
    suspend fun update(block: (minuteEpochMillis: Long, counter: Int) -> Pair<Long, Int>): Pair<Long, Int>
}

class MemoryRevisionWatermarkStore : RevisionWatermarkStore {
    private var minute: Long = 0L
    private var counter: Int = 0

    override suspend fun update(block: (minuteEpochMillis: Long, counter: Int) -> Pair<Long, Int>): Pair<Long, Int> {
        val next = block(minute, counter)
        minute = next.first
        counter = next.second
        return next
    }
}

class DataStoreRevisionWatermarkStore(
    private val syncDataStore: fr.luteal.core.data.datastore.SyncDataStore
) : RevisionWatermarkStore {
    override suspend fun update(block: (minuteEpochMillis: Long, counter: Int) -> Pair<Long, Int>): Pair<Long, Int> =
        syncDataStore.updateRevisionWatermark(block)
}

/**
 * Allocates a coarse-minute timestamp and a UUID that sorts after the previous
 * local allocation, and after a remote winner in that same minute.
 *
 * The server orders by the timestamp text, then the revision text. A random
 * UUID does not. If no revision in the current minute sorts after the remote
 * revision, the next minute is used. A ticket more than five minutes ahead of
 * the device clock is not pushable. The local row can still be saved.
 */
@Singleton
class SyncRevisionClock @Inject constructor(
    private val store: RevisionWatermarkStore,
    private val clock: Clock
) {
    suspend fun allocate(
        floorUpdatedAtEpochMillis: Long? = null,
        floorClientRev: String? = null
    ): RevisionTicket {
        val nowMinute = truncateToMinute(clock.millis())
        val floorMinute = floorUpdatedAtEpochMillis?.let(::truncateToMinute) ?: 0L
        var revision = ""
        var pushable = true
        val minute = store.update { storedMinute, storedCounter ->
            var nextMinute = maxOf(nowMinute, floorMinute, storedMinute)
            var nextCounter = if (nextMinute == storedMinute) storedCounter + 1 else 1
            if (nextCounter > MAX_COUNTER) {
                nextMinute += MINUTE_MILLIS
                nextCounter = 1
            }
            revision = revisionUuid(nextMinute, nextCounter)
            if (floorClientRev != null && nextMinute == floorMinute && !sortsAfter(revision, floorClientRev)) {
                nextMinute += MINUTE_MILLIS
                nextCounter = 1
                revision = revisionUuid(nextMinute, nextCounter)
            }
            pushable = nextMinute <= nowMinute + FUTURE_GUARD_MILLIS
            nextMinute to nextCounter
        }.first
        return RevisionTicket(
            updatedAtEpochMillis = minute,
            clientRev = revision,
            pushable = pushable
        )
    }

    companion object {
        const val WAITING_CLOCK = "waiting_clock"
        private const val MINUTE_MILLIS = 60_000L
        private const val FUTURE_GUARD_MILLIS = 5 * MINUTE_MILLIS
        private const val MAX_COUNTER = 0x0FFF

        fun truncateToMinute(epochMillis: Long): Long =
            Instant.ofEpochMilli(epochMillis)
                .atOffset(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MINUTES)
                .toInstant()
                .toEpochMilli()

        fun revisionUuid(minuteEpochMillis: Long, counter: Int): String {
            val timestamp = minuteEpochMillis and 0x0000_FFFF_FFFF_FFFFL
            val msb = (timestamp shl 16) or 0x7000L or (counter.toLong() and 0x0FFFL)
            val lsb = Long.MIN_VALUE or (counter.toLong() and 0x3FFF_FFFF_FFFF_FFFFL)
            return UUID(msb, lsb).toString()
        }

        fun sortsAfter(candidate: String, floor: String): Boolean =
            candidate.lowercase() > floor.lowercase()

        fun remoteOrdersAfter(
            remoteUpdatedAtEpochMillis: Long,
            remoteRev: String,
            localUpdatedAtEpochMillis: Long,
            localRev: String
        ): Boolean {
            val remoteMinute = truncateToMinute(remoteUpdatedAtEpochMillis)
            val localMinute = truncateToMinute(localUpdatedAtEpochMillis)
            if (remoteMinute != localMinute) return remoteMinute > localMinute
            return sortsAfter(remoteRev, localRev)
        }
    }
}
