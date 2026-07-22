package fr.luteal.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Generic per-record sync envelope. Tracks the client_rev (conflict
 * tiebreak), server timestamps, and the dirty flag for every synchronized
 * entity type (cycle, daily_entry, symptom_log, bleeding_observation).
 *
 * Replaces the former cycle-only `cycle_sync_state` table (v3).
 */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val entityId: String,
    val entityType: String,
    val clientRev: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val deletedAtEpochMillis: Long? = null,
    val dirty: Boolean = true,
    val lastPushError: String? = null
) {
    companion object {
        const val TYPE_CYCLE = "cycle"
        const val TYPE_DAILY_ENTRY = "daily_entry"
        const val TYPE_SYMPTOM_LOG = "symptom_log"
        const val TYPE_BLEEDING_OBSERVATION = "bleeding_observation"
    }
}
