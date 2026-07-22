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
import fr.luteal.core.model.DuoSharingField
import fr.luteal.core.model.DuoSharingPreferences
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
    val hasCompletedOnboarding: Boolean = false,
    val trackPmdd: Boolean = false,
    val trackPms: Boolean = false,
    val trackEndometriosis: Boolean = false,
    val trackPcos: Boolean = false,
    val couplePairingCode: String? = null,
    val duoSharing: DuoSharingPreferences = DuoSharingPreferences()
)

@Singleton
class UserPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        val SYNC_MODE = stringPreferencesKey("sync_mode")
        val USER_ROLE = stringPreferencesKey("user_role")
        val LOCALE = stringPreferencesKey("locale")
        val COMPLETED_ONBOARDING = booleanPreferencesKey("completed_onboarding")
        val TRACK_PMDD = booleanPreferencesKey("track_pmdd")
        val TRACK_PMS = booleanPreferencesKey("track_pms")
        val TRACK_ENDOMETRIOSIS = booleanPreferencesKey("track_endometriosis")
        val TRACK_PCOS = booleanPreferencesKey("track_pcos")
        val COUPLE_PAIRING_CODE = stringPreferencesKey("couple_pairing_code")
        val SHARE_CYCLE_DAY = booleanPreferencesKey("share_cycle_day")
        val SHARE_PERIOD_ESTIMATE = booleanPreferencesKey("share_period_estimate")
        val SHARE_MOOD = booleanPreferencesKey("share_mood")
        val SHARE_ENERGY = booleanPreferencesKey("share_energy")
        val SHARE_SUPPORT_REQUESTS = booleanPreferencesKey("share_support_requests")
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            UserPreferences(
                syncMode = preferences[SYNC_MODE] ?: "OFFLINE_LOCAL",
                userRole = preferences[USER_ROLE] ?: "PRIMARY_TRACKER",
                locale = preferences[LOCALE] ?: "fr",
                hasCompletedOnboarding = preferences[COMPLETED_ONBOARDING] ?: false,
                trackPmdd = preferences[TRACK_PMDD] ?: false,
                trackPms = preferences[TRACK_PMS] ?: false,
                trackEndometriosis = preferences[TRACK_ENDOMETRIOSIS] ?: false,
                trackPcos = preferences[TRACK_PCOS] ?: false,
                couplePairingCode = preferences[COUPLE_PAIRING_CODE],
                duoSharing = DuoSharingPreferences(
                    shareCycleDay = preferences[SHARE_CYCLE_DAY] ?: true,
                    sharePeriodEstimate = preferences[SHARE_PERIOD_ESTIMATE] ?: false,
                    shareMood = preferences[SHARE_MOOD] ?: false,
                    shareEnergy = preferences[SHARE_ENERGY] ?: false,
                    shareSupportRequests = preferences[SHARE_SUPPORT_REQUESTS] ?: true
                )
            )
        }

    suspend fun setSyncMode(mode: String) = edit { it[SYNC_MODE] = mode }

    suspend fun setUserRole(role: String) = edit { it[USER_ROLE] = role }

    suspend fun setLocale(locale: String) = edit { it[LOCALE] = locale }
    suspend fun setCompletedOnboarding(completed: Boolean) = edit { it[COMPLETED_ONBOARDING] = completed }

    suspend fun setDisorderTracking(disorderId: String, enabled: Boolean) = edit { preferences ->
        when (disorderId.lowercase().removePrefix("track_")) {
            "pmdd" -> preferences[TRACK_PMDD] = enabled
            "pms" -> preferences[TRACK_PMS] = enabled
            "endometriosis" -> preferences[TRACK_ENDOMETRIOSIS] = enabled
            "pcos" -> preferences[TRACK_PCOS] = enabled
        }
    }

    suspend fun setDuoSharing(field: DuoSharingField, enabled: Boolean) = edit { preferences ->
        preferences[field.preferenceKey()] = enabled
    }

    suspend fun setCouplePairingCode(code: String?) = edit { preferences ->
        if (code == null) preferences.remove(COUPLE_PAIRING_CODE)
        else preferences[COUPLE_PAIRING_CODE] = code
    }

    private suspend fun edit(block: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private fun DuoSharingField.preferenceKey() = when (this) {
        DuoSharingField.CYCLE_DAY -> SHARE_CYCLE_DAY
        DuoSharingField.PERIOD_ESTIMATE -> SHARE_PERIOD_ESTIMATE
        DuoSharingField.MOOD -> SHARE_MOOD
        DuoSharingField.ENERGY -> SHARE_ENERGY
        DuoSharingField.SUPPORT_REQUESTS -> SHARE_SUPPORT_REQUESTS
    }
}
