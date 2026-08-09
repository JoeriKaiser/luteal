package fr.luteal.core.model

import java.time.Instant
import java.time.LocalDate

/**
 * Minimal, derived Duo state retained for the home-screen widget.
 *
 * This is already grant-filtered shared data. Private observations, notes,
 * support messages, credentials, ciphertext, and Duo keys deliberately have no
 * representation here.
 */
data class CachedDuoCycleProjection(
    val linkId: String,
    val role: String,
    val cycleDay: Int?,
    val estimateStart: LocalDate?,
    val estimateEnd: LocalDate?,
    val cycleDayGranted: Boolean,
    val estimateGranted: Boolean,
    val status: DuoCycleProjectionStatus,
    val refreshedAt: Instant
)

enum class DuoCycleProjectionStatus {
    ACTIVE,
    NO_PAYLOAD,
    KEY_MISSING,
    INVALID_PAYLOAD
}
