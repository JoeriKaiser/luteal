package fr.luteal.core.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.syncDataStore: DataStore<Preferences> by preferencesDataStore(name = "sync_preferences")

/**
 * Non-secret sync transport state. The pull cursor (an opaque server sequence
 * number), the dev base URL, and the last sync outcome live here. Credentials
 * (account code, device token) are NEVER stored here - they live in the
 * Keystore-backed [fr.luteal.core.network.auth.SyncCredentialStore].
 */
data class SyncPreferences(
    val cursor: Long = 0L,
    val baseUrl: String? = null,
    val inviteCode: String? = null,
    val deviceLabel: String? = null,
    val lastSyncedEpochMillis: Long? = null,
    val lastError: String? = null,
    val inProgress: Boolean = false
)

@Singleton
class SyncDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        val CURSOR = longPreferencesKey("sync_cursor")
        val BASE_URL = stringPreferencesKey("sync_base_url")
        val INVITE_CODE = stringPreferencesKey("sync_invite_code")
        val DEVICE_LABEL = stringPreferencesKey("sync_device_label")
        val LAST_SYNCED = longPreferencesKey("sync_last_synced_epoch_millis")
        val LAST_ERROR = stringPreferencesKey("sync_last_error")
        val IN_PROGRESS = stringPreferencesKey("sync_in_progress")
    }

    val syncPreferencesFlow: Flow<SyncPreferences> = context.syncDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            SyncPreferences(
                cursor = preferences[CURSOR] ?: 0L,
                baseUrl = preferences[BASE_URL],
                inviteCode = preferences[INVITE_CODE],
                deviceLabel = preferences[DEVICE_LABEL],
                lastSyncedEpochMillis = preferences[LAST_SYNCED],
                lastError = preferences[LAST_ERROR],
                inProgress = preferences[IN_PROGRESS]?.toBoolean() ?: false
            )
        }

    suspend fun setCursor(cursor: Long): Unit = edit { it[CURSOR] = cursor }

    suspend fun setBaseUrl(baseUrl: String?): Unit = edit { preferences ->
        if (baseUrl.isNullOrBlank()) preferences.remove(BASE_URL) else preferences[BASE_URL] = baseUrl
    }

    suspend fun setInviteCode(inviteCode: String?): Unit = edit { preferences ->
        if (inviteCode.isNullOrBlank()) preferences.remove(INVITE_CODE) else preferences[INVITE_CODE] = inviteCode
    }

    /**
     * Persists the generated device label so it stays stable across syncs.
     * See [fr.luteal.core.network.auth.DeviceLabel] for why this is not the
     * hardware model.
     */
    suspend fun setDeviceLabel(label: String): Unit = edit { it[DEVICE_LABEL] = label }

    suspend fun setInProgress(inProgress: Boolean): Unit = edit { it[IN_PROGRESS] = inProgress.toString() }

    suspend fun recordSuccess(epochMillis: Long): Unit = edit { preferences ->
        preferences[LAST_SYNCED] = epochMillis
        preferences.remove(LAST_ERROR)
        preferences[IN_PROGRESS] = false.toString()
    }

    suspend fun recordError(message: String): Unit = edit { preferences ->
        preferences[LAST_ERROR] = message
        preferences[IN_PROGRESS] = false.toString()
    }

    suspend fun clear(): Unit = edit { it.clear() }

    private suspend fun edit(block: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.syncDataStore.edit(block)
    }
}
