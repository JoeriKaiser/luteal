package fr.luteal.core.network.sync

import fr.luteal.core.data.datastore.SyncDataStore
import kotlinx.coroutines.flow.first

/**
 * [SyncCursorStore] over [SyncDataStore]. The cursor and base URL are not
 * secrets and live in DataStore; when no dev base URL is configured, falls
 * back to [defaultBaseUrl] (the emulator loopback alias by default).
 */
class DataStoreSyncCursorStore(
    private val syncDataStore: SyncDataStore,
    private val defaultBaseUrl: String
) : SyncCursorStore {

    override suspend fun getCursor(): Long =
        syncDataStore.syncPreferencesFlow.first().cursor

    override suspend fun setCursor(cursor: Long) =
        syncDataStore.setCursor(cursor)

    override suspend fun getBaseUrl(): String =
        syncDataStore.syncPreferencesFlow.first().baseUrl?.takeIf { it.isNotBlank() } ?: defaultBaseUrl
}
