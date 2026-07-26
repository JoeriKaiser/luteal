package fr.luteal.core.data.repository

import fr.luteal.core.data.datastore.UserPreferences
import fr.luteal.core.model.DuoSharingField
import fr.luteal.core.model.SyncMode
import fr.luteal.core.model.UserProfile
import fr.luteal.core.model.UserRole
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun getUserProfile(): Flow<UserProfile?>
    fun getUserPreferences(): Flow<UserPreferences>
    suspend fun updateSyncMode(mode: SyncMode)
    suspend fun updateUserRole(role: UserRole)
    suspend fun updateDuoSharing(field: DuoSharingField, enabled: Boolean)
    suspend fun setCouplePairing(pairingCode: String?, partnerName: String?)
    suspend fun completeOnboarding(
        role: UserRole,
        disorderTracking: Map<String, Boolean>,
        ageBandId: String? = null
    )

    /**
     * Declared contexts and age band are editable after onboarding.
     *
     * Both change over time - perimenopause in particular is something a user
     * enters partway through using the app - and anyone who skipped the
     * introduction never set them at all.
     */
    suspend fun setTrackingContext(contextId: String, enabled: Boolean)

    /** Null clears a previously declared band. */
    suspend fun setAgeBand(ageBandId: String?)
}
