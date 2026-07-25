package fr.luteal.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The projection the tracker shares with their partner.
 *
 * This is the plaintext sealed inside `DuoView.payload`. It is composed on the
 * tracker's device, which applies the sharing grants before encrypting: a field
 * the tracker has not granted is absent here because it was never encrypted,
 * not because the server filtered it afterwards. The server therefore cannot
 * leak what it never received.
 *
 * Private notes and raw observations have no representation here at all, by
 * construction rather than by policy.
 */
@Serializable
data class DuoProjection(
    /** Schema version of the sealed payload, so clients can migrate it. */
    @SerialName("v") val version: Int = CURRENT_VERSION,
    @SerialName("cycle_day") val cycleDay: Int? = null,
    @SerialName("period_estimate") val periodEstimate: SharedEstimate? = null,
    @SerialName("mood") val mood: SharedLevel? = null,
    @SerialName("energy") val energy: SharedLevel? = null
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

@Serializable
data class SharedEstimate(
    @SerialName("window_start") val windowStart: String,
    @SerialName("window_end") val windowEnd: String
)

@Serializable
data class SharedLevel(
    @SerialName("date") val date: String,
    @SerialName("level") val level: Int
)
