package fr.luteal.core.data

import fr.luteal.core.data.datastore.UserPreferencesDataStore
import fr.luteal.core.data.repository.DuoWidgetCacheRepository
import fr.luteal.core.model.UserRole
import fr.luteal.core.network.crypto.DuoKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides the Duo tab setting for installs that predate it.
 *
 * New installs answer during onboarding, so the setting is only ever unset
 * after onboarding on an app that was updated. Anyone already in a Duo, or who
 * onboarded as a partner, keeps the tab. Everyone else loses it and gets a
 * single notice pointing to settings.
 */
@Singleton
class DuoTabMigrator @Inject constructor(
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val duoKeyStore: DuoKeyStore,
    private val duoWidgetCacheRepository: DuoWidgetCacheRepository
) {
    data class Outcome(val duoEnabled: Boolean, val showMovedNotice: Boolean)

    suspend fun migrateIfNeeded() {
        val preferences = userPreferencesDataStore.userPreferencesFlow.first()
        if (!needsMigration(preferences.hasCompletedOnboarding, preferences.duoEnabled)) return
        // A failed probe counts as being in a Duo: hiding the tab from someone
        // who uses it is worse than showing it to someone who does not.
        val hasDuoLink = runCatching {
            withContext(Dispatchers.IO) {
                duoKeyStore.hasLinkKeys() || duoWidgetCacheRepository.getLatest() != null
            }
        }.getOrDefault(true)
        val outcome = resolve(preferences.userRole, hasDuoLink)
        userPreferencesDataStore.applyDuoTabMigration(outcome.duoEnabled, outcome.showMovedNotice)
    }

    companion object {
        fun needsMigration(hasCompletedOnboarding: Boolean, storedDuoEnabled: Boolean?): Boolean =
            hasCompletedOnboarding && storedDuoEnabled == null

        fun resolve(userRole: String, hasDuoLink: Boolean): Outcome {
            val keepDuo = hasDuoLink || userRole == UserRole.PARTNER_VIEWER.name
            return Outcome(duoEnabled = keepDuo, showMovedNotice = !keepDuo)
        }
    }
}
