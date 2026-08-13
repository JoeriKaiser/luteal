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
import fr.luteal.core.model.ContextGroup
import fr.luteal.core.model.DuoSharingField
import fr.luteal.core.model.DuoSharingPreferences
import fr.luteal.core.model.TrackingContext
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
    val trackPerimenopause: Boolean = false,
    val trackThyroid: Boolean = false,
    /** Optional; null means the user did not declare one. */
    val ageBand: String? = null,
    val couplePairingCode: String? = null,
    val duoSharing: DuoSharingPreferences = DuoSharingPreferences()
) {
    /** Contexts the user declared during onboarding. */
    val declaredContexts: Set<TrackingContext>
        get() = buildSet {
            if (trackPms) add(TrackingContext.PMS)
            if (trackPmdd) add(TrackingContext.PMDD)
            if (trackEndometriosis) add(TrackingContext.ENDOMETRIOSIS)
            if (trackPcos) add(TrackingContext.PCOS)
            if (trackPerimenopause) add(TrackingContext.PERIMENOPAUSE)
            if (trackThyroid) add(TrackingContext.THYROID)
        }

    /**
     * Whether any declared context belongs to [ContextGroup.TIMING].
     *
     * Endometriosis, SPM, and TDPM are deliberately excluded: they are
     * OBSERVATION contexts and must not influence estimation. See
     * [TrackingContext] and docs/research/CONDITION_CYCLE_IMPACTS.md.
     */
    val hasTimingContext: Boolean
        get() = declaredContexts.any { it.group == ContextGroup.TIMING }
}

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
        val TRACK_PERIMENOPAUSE = booleanPreferencesKey("track_perimenopause")
        val TRACK_THYROID = booleanPreferencesKey("track_thyroid")
        val AGE_BAND = stringPreferencesKey("age_band")
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
                trackPerimenopause = preferences[TRACK_PERIMENOPAUSE] ?: false,
                trackThyroid = preferences[TRACK_THYROID] ?: false,
                ageBand = preferences[AGE_BAND],
                couplePairingCode = preferences[COUPLE_PAIRING_CODE],
                duoSharing = DuoSharingPreferences(
                    // All off by default: sharing is opt-in and the server is
                    // the source of truth for grants. These keys mirror the
                    // last confirmed choices and are written only from the
                    // server's duoView response or a successful toggle.
                    shareCycleDay = preferences[SHARE_CYCLE_DAY] ?: false,
                    sharePeriodEstimate = preferences[SHARE_PERIOD_ESTIMATE] ?: false,
                    shareMood = preferences[SHARE_MOOD] ?: false,
                    shareEnergy = preferences[SHARE_ENERGY] ?: false,
                    shareSupportRequests = preferences[SHARE_SUPPORT_REQUESTS] ?: false
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
            "perimenopause" -> preferences[TRACK_PERIMENOPAUSE] = enabled
            "thyroid" -> preferences[TRACK_THYROID] = enabled
        }
    }

    /** Null clears a previously declared band. */
    suspend fun setAgeBand(ageBandId: String?) = edit { preferences ->
        if (ageBandId == null) preferences.remove(AGE_BAND)
        else preferences[AGE_BAND] = ageBandId
    }

    suspend fun setDuoSharing(field: DuoSharingField, enabled: Boolean) = edit { preferences ->
        preferences[field.preferenceKey()] = enabled
    }

    suspend fun setCouplePairingCode(code: String?) = edit { preferences ->
        if (code == null) preferences.remove(COUPLE_PAIRING_CODE)
        else preferences[COUPLE_PAIRING_CODE] = code
    }

    suspend fun clear() = edit { it.clear() }

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
