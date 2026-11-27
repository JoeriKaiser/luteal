package fr.luteal.app.update

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.updateDataStore: DataStore<Preferences> by preferencesDataStore(name = "update_preferences")

data class UpdatePreferences(
    val checksEnabled: Boolean = false,
    val lastNotifiedVersion: String? = null
)

@Singleton
class UpdatePreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        val CHECKS_ENABLED = booleanPreferencesKey("update_checks_enabled")
        val LAST_NOTIFIED_VERSION = stringPreferencesKey("update_last_notified_version")
    }

    val preferences: Flow<UpdatePreferences> = context.updateDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs ->
            UpdatePreferences(
                checksEnabled = prefs[CHECKS_ENABLED] ?: false,
                lastNotifiedVersion = prefs[LAST_NOTIFIED_VERSION]
            )
        }

    suspend fun setChecksEnabled(enabled: Boolean) {
        context.updateDataStore.edit { prefs ->
            prefs[CHECKS_ENABLED] = enabled
            if (!enabled) prefs.remove(LAST_NOTIFIED_VERSION)
        }
    }

    suspend fun setLastNotifiedVersion(version: String) {
        context.updateDataStore.edit { it[LAST_NOTIFIED_VERSION] = version }
    }
}
