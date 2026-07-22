package fr.luteal.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-cycle synchronization envelope and dirty tracking, kept in a side
 * table so the offline cycle write path ([CycleEntity]) stays untouched.
 * Local writes never wait on the network; the sync worker reads this table
 * to decide what to push and adopts the server's state on pull.
 *
 * Envelope fields mirror the contract record envelope: [clientRev] is a fresh
 * UUID on every local edit (the conflict tiebreak after updated_at);
 * [createdAtEpochMillis]/[updatedAtEpochMillis] are UTC instants; a non-null
 * [deletedAtEpochMillis] marks a tombstone. [dirty] flags a row that must be
 * pushed. [lastPushError] carries the most recent server rejection detail for
 * this row, if any (never the account code or device token).
 */
@Entity(tableName = "cycle_sync_state")
data class CycleSyncStateEntity(
    @PrimaryKey val cycleId: String,
    val clientRev: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val deletedAtEpochMillis: Long? = null,
    val dirty: Boolean = true,
    val lastPushError: String? = null
)
