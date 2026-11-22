package fr.luteal.core.network.sync

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex

/**
 * Shared mutual-exclusion lock serializing [CycleSyncEngine.sync] and
 * [AccountUpgradeManager] operations so background sync can never interleave
 * with an in-place `v1 -> v2` key rotation.
 */
@Singleton
class SyncLock @Inject constructor() {
    val mutex: Mutex = Mutex()
}
