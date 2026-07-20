package fr.luteal.core.model

import java.time.Instant

data class CouplePairing(
    val pairId: String? = null,
    val pairingCode: String? = null,
    val partnerName: String? = null,
    val isConnected: Boolean = false,
    val lastSyncedAt: Instant? = null
)
