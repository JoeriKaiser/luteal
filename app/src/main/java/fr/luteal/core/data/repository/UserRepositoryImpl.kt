package fr.luteal.core.data.repository

import fr.luteal.core.data.datastore.UserPreferences
import fr.luteal.core.data.datastore.UserPreferencesDataStore
import fr.luteal.core.data.entity.UserProfileEntity
import fr.luteal.core.data.local.UserProfileDao
import fr.luteal.core.model.DuoSharingField
import fr.luteal.core.model.SyncMode
import fr.luteal.core.model.UserProfile
import fr.luteal.core.model.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userProfileDao: UserProfileDao,
    private val userPreferencesDataStore: UserPreferencesDataStore
) : UserRepository {

    override fun getUserProfile(): Flow<UserProfile?> {
        return userProfileDao.getUserProfile().map { entity ->
            entity?.toDomain()
        }
    }

    override fun getUserPreferences(): Flow<UserPreferences> {
        return userPreferencesDataStore.userPreferencesFlow
    }

    override suspend fun updateSyncMode(mode: SyncMode) {
        userPreferencesDataStore.setSyncMode(mode.name)
        val currentProfile = userProfileDao.getUserProfile().firstOrNull()
        if (currentProfile != null) {
            userProfileDao.insertOrUpdateUserProfile(currentProfile.copy(syncMode = mode.name))
        }
    }

    override suspend fun updateUserRole(role: UserRole) {
        userPreferencesDataStore.setUserRole(role.name)
        val currentProfile = userProfileDao.getUserProfile().firstOrNull()
        if (currentProfile != null) {
            userProfileDao.insertOrUpdateUserProfile(currentProfile.copy(role = role.name))
        }
    }

    override suspend fun updateDuoSharing(field: DuoSharingField, enabled: Boolean) {
        userPreferencesDataStore.setDuoSharing(field, enabled)
    }

    override suspend fun completeOnboarding(role: UserRole, disorderTracking: Map<String, Boolean>) {
        updateUserRole(role)
        for ((disorderId, enabled) in disorderTracking) {
            userPreferencesDataStore.setDisorderTracking(disorderId, enabled)
        }
        userPreferencesDataStore.setCompletedOnboarding(true)
    }

    override suspend fun setCouplePairing(pairingCode: String?, partnerName: String?) {
        userPreferencesDataStore.setCouplePairingCode(pairingCode)
        val currentProfile = userProfileDao.getUserProfile().firstOrNull()
        val isPaired = !pairingCode.isNullOrBlank()
        if (currentProfile != null) {
            userProfileDao.insertOrUpdateUserProfile(
                currentProfile.copy(
                    couplePairingCode = pairingCode,
                    partnerName = partnerName,
                    isPaired = isPaired
                )
            )
        } else {
            userProfileDao.insertOrUpdateUserProfile(
                UserProfileEntity(
                    userId = "local_user",
                    role = UserRole.PRIMARY_TRACKER.name,
                    syncMode = SyncMode.OFFLINE_LOCAL.name,
                    couplePairingCode = pairingCode,
                    partnerName = partnerName,
                    isPaired = isPaired
                )
            )
        }
    }

    private fun UserProfileEntity.toDomain(): UserProfile {
        val userRole = try {
            UserRole.valueOf(role)
        } catch (e: Exception) {
            UserRole.PRIMARY_TRACKER
        }
        val mode = try {
            SyncMode.valueOf(syncMode)
        } catch (e: Exception) {
            SyncMode.OFFLINE_LOCAL
        }
        return UserProfile(
            userId = userId,
            role = userRole,
            syncMode = mode,
            couplePairingCode = couplePairingCode,
            partnerName = partnerName,
            isPaired = isPaired
        )
    }

    private fun UserProfile.toEntity(): UserProfileEntity {
        return UserProfileEntity(
            userId = userId,
            role = role.name,
            syncMode = syncMode.name,
            couplePairingCode = couplePairingCode,
            partnerName = partnerName,
            isPaired = isPaired
        )
    }
}
