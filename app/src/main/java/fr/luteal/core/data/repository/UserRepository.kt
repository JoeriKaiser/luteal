package fr.luteal.core.data.repository

import fr.luteal.core.data.datastore.UserPreferences
import fr.luteal.core.model.SyncMode
import fr.luteal.core.model.UserProfile
import fr.luteal.core.model.UserRole
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun getUserProfile(): Flow<UserProfile?>
    fun getUserPreferences(): Flow<UserPreferences>
    suspend fun updateSyncMode(mode: SyncMode)
    suspend fun updateUserRole(role: UserRole)
    suspend fun setCouplePairing(pairingCode: String?, partnerName: String?)
}
