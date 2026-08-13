package fr.luteal.core.data

import fr.luteal.core.data.datastore.SyncDataStore
import fr.luteal.core.data.datastore.UserPreferencesDataStore
import fr.luteal.core.data.local.LutealDatabase
import fr.luteal.core.network.auth.SyncCredentialStore
import fr.luteal.core.network.crypto.DuoKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executes a complete, GDPR-compliant local wipe of all user health records,
 * settings, sync metadata, and cryptographic credentials stored on device.
 */
@Singleton
class LocalDataPurgeManager @Inject constructor(
    private val database: LutealDatabase,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val syncDataStore: SyncDataStore,
    private val syncCredentialStore: SyncCredentialStore,
    private val duoKeyStore: DuoKeyStore
) {
    suspend fun purgeAllLocalData() {
        withContext(Dispatchers.IO) {
            database.clearAllTables()
        }
        userPreferencesDataStore.clear()
        syncDataStore.clear()
        runCatching { syncCredentialStore.clear() }
        runCatching { duoKeyStore.clear() }
    }
}
