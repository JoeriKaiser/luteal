package fr.luteal.core.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

data class UserPreferences(
    val syncMode: String = "OFFLINE_LOCAL",
    val userRole: String = "PRIMARY_TRACKER",
    val locale: String = "fr",
    val trackPmdd: Boolean = true,
    val trackPms: Boolean = true,
    val trackEndometriosis: Boolean = false,
    val trackPcos: Boolean = false,
    val couplePairingCode: String? = null
)

@Singleton
class UserPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val SYNC_MODE = stringPreferencesKey("sync_mode")
        val USER_ROLE = stringPreferencesKey("user_role")
        val LOCALE = stringPreferencesKey("locale")
        val TRACK_PMDD = booleanPreferencesKey("track_pmdd")
        val TRACK_PMS = booleanPreferencesKey("track_pms")
        val TRACK_ENDOMETRIOSIS = booleanPreferencesKey("track_endometriosis")
        val TRACK_PCOS = booleanPreferencesKey("track_pcos")
        val COUPLE_PAIRING_CODE = stringPreferencesKey("couple_pairing_code")
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            UserPreferences(
                syncMode = preferences[SYNC_MODE] ?: "OFFLINE_LOCAL",
                userRole = preferences[USER_ROLE] ?: "PRIMARY_TRACKER",
                locale = preferences[LOCALE] ?: "fr",
                trackPmdd = preferences[TRACK_PMDD] ?: true,
                trackPms = preferences[TRACK_PMS] ?: true,
                trackEndometriosis = preferences[TRACK_ENDOMETRIOSIS] ?: false,
                trackPcos = preferences[TRACK_PCOS] ?: false,
                couplePairingCode = preferences[COUPLE_PAIRING_CODE]
            )
        }

    suspend fun setSyncMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[SYNC_MODE] = mode
        }
    }

    suspend fun setUserRole(role: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_ROLE] = role
        }
    }

    suspend fun setLocale(locale: String) {
        context.dataStore.edit { preferences ->
            preferences[LOCALE] = locale
        }
    }

    suspend fun setDisorderTracking(disorderId: String, enabled: Boolean) {
        context.dataStore.edit { preferences ->
            when (disorderId.lowercase().removePrefix("track_")) {
                "pmdd" -> preferences[TRACK_PMDD] = enabled
                "pms" -> preferences[TRACK_PMS] = enabled
                "endometriosis" -> preferences[TRACK_ENDOMETRIOSIS] = enabled
                "pcos" -> preferences[TRACK_PCOS] = enabled
            }
        }
    }

    suspend fun setCouplePairingCode(code: String?) {
        context.dataStore.edit { preferences ->
            if (code != null) {
                preferences[COUPLE_PAIRING_CODE] = code
            } else {
                preferences.remove(COUPLE_PAIRING_CODE)
            }
        }
    }
}
