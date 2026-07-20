package fr.luteal.core.model

import java.time.Instant

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Syncing : SyncStatus
    data class Success(val lastSynced: Instant) : SyncStatus
    data class Error(val message: String) : SyncStatus
}
