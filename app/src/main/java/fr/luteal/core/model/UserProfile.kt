package fr.luteal.core.model

data class UserProfile(
    val userId: String,
    val role: UserRole = UserRole.PRIMARY_TRACKER,
    val syncMode: SyncMode = SyncMode.OFFLINE_LOCAL,
    val couplePairingCode: String? = null,
    val partnerName: String? = null,
    val isPaired: Boolean = false
)
