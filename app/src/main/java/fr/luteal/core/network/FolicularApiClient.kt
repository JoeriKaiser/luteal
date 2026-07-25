package fr.luteal.core.network

import fr.luteal.core.network.contract.models.AcceptLink201Response
import fr.luteal.core.network.contract.models.DuoView
import fr.luteal.core.network.contract.models.GrantField
import fr.luteal.core.network.contract.models.Invitation
import fr.luteal.core.network.contract.models.ListLinks200Response
import fr.luteal.core.network.contract.models.Register201Response
import fr.luteal.core.network.contract.models.SupportKind
import fr.luteal.core.network.contract.models.SupportRequest

/**
 * Transport for the folicular v1 API. Bodies use the generated contract
 * DTOs / [ContractJson]; the polymorphic sync envelopes are carried by the
 * [PushChangeWire] / [PullResultWire] mirrors (see [SyncWire]).
 *
 * A client is constructed for a specific base URL (dev targeting: emulator
 * `http://10.0.2.2:8080`, a real device `http://127.0.0.1:8080` via
 * `adb reverse`, or the host's LAN IP).
 */
interface FolicularApiClient {

    /**
     * POST /v1/auth/register - create an anonymous account + first device.
     * [inviteCode] is required when the server gates registration (closed
     * rollout); pass an empty string when registration is open.
     */
    suspend fun register(deviceName: String, inviteCode: String): Register201Response

    /**
     * POST /v1/auth/devices - attach this device to an EXISTING account using
     * its account code. This is the only recovery path: the account code is the
     * root of the key hierarchy, so without it a reinstall cannot decrypt
     * anything the server holds.
     *
     * Returns the account id (needed to derive the keys) and the new device
     * token.
     */
    suspend fun addDevice(accountCode: String, deviceName: String): AddDeviceResult

    /** POST /v1/sync/push - push a batch of changes; server validates each. */
    suspend fun syncPush(deviceToken: String, changes: List<PushChangeWire>): PushResultWire

    /** GET /v1/sync/pull?since&limit - pull changes after the cursor. */
    suspend fun syncPull(deviceToken: String, since: Long, limit: Int = 500): PullResultWire

    // --- Duo ---------------------------------------------------------------

    /** POST /v1/duo/invitations - create a pending link + pairing code. */
    suspend fun createInvitation(deviceToken: String): Invitation

    /** POST /v1/duo/links - accept a pending invitation by pairing code. */
    suspend fun acceptLink(deviceToken: String, pairingCode: String): AcceptLink201Response

    /** GET /v1/duo/links - list the caller's Duo links in both roles. */
    suspend fun listLinks(deviceToken: String): ListLinks200Response

    /** PATCH /v1/duo/links/{linkID}/grants - grant or revoke one field. */
    suspend fun patchGrants(deviceToken: String, linkId: String, field: GrantField, granted: Boolean)

    /** DELETE /v1/duo/links/{linkID} - revoke a link. */
    suspend fun revokeLink(deviceToken: String, linkId: String)

    /** GET /v1/duo/view - the sealed partner projection and support thread. */
    suspend fun duoView(deviceToken: String): DuoView

    /**
     * PUT /v1/duo/payload - publish the sealed projection (tracker only).
     * [payload] is base64 of the sealed [fr.luteal.core.model.DuoProjection].
     */
    suspend fun putDuoPayload(deviceToken: String, payload: String)

    /** POST /v1/duo/support-requests - create a support request. */
    /** [sealedMessage] is ciphertext; the server relays it without reading it. */
    suspend fun createSupportRequest(
        deviceToken: String, linkId: String, kind: SupportKind, sealedMessage: ByteArray
    ): SupportRequest

    /** PATCH /v1/duo/support-requests/{requestID}/ack - acknowledge a request. */
    suspend fun ackSupportRequest(deviceToken: String, requestId: String)
}

/** Result of attaching a device to an existing account. */
data class AddDeviceResult(
    val accountId: String,
    val deviceToken: String
)

/**
 * A non-2xx API response. [detail] carries the RFC 9457 `detail` when the
 * server returned a `application/problem+json` body, else a generic message.
 * Never includes the account code or device token.
 */
class FolicularApiException(
    val status: Int,
    val errorType: String,
    message: String
) : RuntimeException("HTTP $status ($errorType): $message")
