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
    val duoSharing: DuoSharingPreferences = DuoSharingPreferences(),
    val isAppLockEnabled: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    val autoLockTimeout: String = "IMMEDIATE",
    val isScreenMaskingEnabled: Boolean = false,
    val consecutivePinFailures: Int = 0,
    val lockoutUntilEpochMillis: Long = 0L,
    val isNotificationsEnabled: Boolean = false,
    val isDailyCheckInEnabled: Boolean = false,
    val dailyCheckInTime: String = "21:00",
    val isPeriodWindowEnabled: Boolean = false,
    val periodWindowLeadDays: Int = 2,
    val isLateCycleEnabled: Boolean = false,
    val lateCycleGraceDays: Int = 1,
    val notificationVisibilityMode: String = "CONCEALED",
    val notificationCustomTitle: String = "",
    val notificationCustomBody: String = "",
    val temperatureUnit: String = "CELSIUS"
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
        val IS_APP_LOCK_ENABLED = booleanPreferencesKey("is_app_lock_enabled")
        val IS_BIOMETRIC_ENABLED = booleanPreferencesKey("is_biometric_enabled")
        val AUTO_LOCK_TIMEOUT = stringPreferencesKey("auto_lock_timeout")
        val IS_SCREEN_MASKING_ENABLED = booleanPreferencesKey("is_screen_masking_enabled")
        val CONSECUTIVE_PIN_FAILURES = androidx.datastore.preferences.core.intPreferencesKey("consecutive_pin_failures")
        val LOCKOUT_UNTIL_EPOCH_MILLIS = androidx.datastore.preferences.core.longPreferencesKey("lockout_until_epoch_millis")
        val NOTIF_ENABLED = booleanPreferencesKey("notif_enabled")
        val NOTIF_DAILY_ENABLED = booleanPreferencesKey("notif_daily_enabled")
        val NOTIF_DAILY_TIME = stringPreferencesKey("notif_daily_time")
        val NOTIF_WINDOW_ENABLED = booleanPreferencesKey("notif_window_enabled")
        val NOTIF_WINDOW_LEAD_DAYS = androidx.datastore.preferences.core.intPreferencesKey("notif_window_lead_days")
        val NOTIF_LATE_ENABLED = booleanPreferencesKey("notif_late_enabled")
        val NOTIF_LATE_GRACE_DAYS = androidx.datastore.preferences.core.intPreferencesKey("notif_late_grace_days")
        val NOTIF_VISIBILITY_MODE = stringPreferencesKey("notif_visibility_mode")
        val NOTIF_CUSTOM_TITLE = stringPreferencesKey("notif_custom_title")
        val NOTIF_CUSTOM_BODY = stringPreferencesKey("notif_custom_body")
        val TEMPERATURE_UNIT = stringPreferencesKey("temperature_unit")
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
                ),
                isAppLockEnabled = preferences[IS_APP_LOCK_ENABLED] ?: false,
                isBiometricEnabled = preferences[IS_BIOMETRIC_ENABLED] ?: false,
                autoLockTimeout = preferences[AUTO_LOCK_TIMEOUT] ?: "IMMEDIATE",
                isScreenMaskingEnabled = preferences[IS_SCREEN_MASKING_ENABLED] ?: false,
                consecutivePinFailures = preferences[CONSECUTIVE_PIN_FAILURES] ?: 0,
                lockoutUntilEpochMillis = preferences[LOCKOUT_UNTIL_EPOCH_MILLIS] ?: 0L,
                isNotificationsEnabled = preferences[NOTIF_ENABLED] ?: false,
                isDailyCheckInEnabled = preferences[NOTIF_DAILY_ENABLED] ?: false,
                dailyCheckInTime = preferences[NOTIF_DAILY_TIME] ?: "21:00",
                isPeriodWindowEnabled = preferences[NOTIF_WINDOW_ENABLED] ?: false,
                periodWindowLeadDays = preferences[NOTIF_WINDOW_LEAD_DAYS] ?: 2,
                isLateCycleEnabled = preferences[NOTIF_LATE_ENABLED] ?: false,
                lateCycleGraceDays = preferences[NOTIF_LATE_GRACE_DAYS] ?: 1,
                notificationVisibilityMode = preferences[NOTIF_VISIBILITY_MODE] ?: "CONCEALED",
                notificationCustomTitle = preferences[NOTIF_CUSTOM_TITLE] ?: "",
                notificationCustomBody = preferences[NOTIF_CUSTOM_BODY] ?: "",
                temperatureUnit = preferences[TEMPERATURE_UNIT] ?: "CELSIUS"
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


    suspend fun setAppLockEnabled(enabled: Boolean) = edit { it[IS_APP_LOCK_ENABLED] = enabled }
    suspend fun setBiometricEnabled(enabled: Boolean) = edit { it[IS_BIOMETRIC_ENABLED] = enabled }
    suspend fun setAutoLockTimeout(timeout: String) = edit { it[AUTO_LOCK_TIMEOUT] = timeout }
    suspend fun setScreenMaskingEnabled(enabled: Boolean) = edit { it[IS_SCREEN_MASKING_ENABLED] = enabled }
    suspend fun setConsecutivePinFailures(count: Int) = edit { it[CONSECUTIVE_PIN_FAILURES] = count }
    suspend fun setLockoutUntilEpochMillis(timestampMillis: Long) = edit { it[LOCKOUT_UNTIL_EPOCH_MILLIS] = timestampMillis }

    suspend fun resetPinFailures() = edit {
        it[CONSECUTIVE_PIN_FAILURES] = 0
        it[LOCKOUT_UNTIL_EPOCH_MILLIS] = 0L
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) = edit { it[NOTIF_ENABLED] = enabled }
    suspend fun setDailyCheckInEnabled(enabled: Boolean) = edit { it[NOTIF_DAILY_ENABLED] = enabled }
    suspend fun setDailyCheckInTime(time: String) = edit { it[NOTIF_DAILY_TIME] = time }
    suspend fun setPeriodWindowNotificationEnabled(enabled: Boolean) = edit { it[NOTIF_WINDOW_ENABLED] = enabled }
    suspend fun setPeriodWindowLeadDays(days: Int) = edit { it[NOTIF_WINDOW_LEAD_DAYS] = days }
    suspend fun setLateCycleNotificationEnabled(enabled: Boolean) = edit { it[NOTIF_LATE_ENABLED] = enabled }
    suspend fun setLateCycleGraceDays(days: Int) = edit { it[NOTIF_LATE_GRACE_DAYS] = days }
    suspend fun setNotificationVisibilityMode(mode: String) = edit { it[NOTIF_VISIBILITY_MODE] = mode }
    suspend fun setNotificationCustomTitle(title: String) = edit { it[NOTIF_CUSTOM_TITLE] = title }
    suspend fun setNotificationCustomBody(body: String) = edit { it[NOTIF_CUSTOM_BODY] = body }
    suspend fun setTemperatureUnit(unit: String) = edit { it[TEMPERATURE_UNIT] = unit }
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
