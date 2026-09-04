package fr.luteal.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Generic per-record sync envelope. Tracks the client_rev (conflict
 * tiebreak), server timestamps, and the dirty flag for every synchronized
 * entity type (cycle, daily_entry, symptom_log, bleeding_observation).
 *
 * Replaces the former cycle-only `cycle_sync_state` table (v3).
 */
@Entity(
    tableName = "sync_state",
    indices = [
        Index(value = ["dirty", "entityType"])
    ]
)
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
        const val TYPE_BIOMARKER_OBSERVATION = "biomarker_observation"

        fun biomarkerEntityId(date: String): String = "bm:$date"

        fun biomarkerDateFromEntityId(entityId: String): String? =
            entityId.takeIf { it.startsWith("bm:") }?.removePrefix("bm:")
    }
}
