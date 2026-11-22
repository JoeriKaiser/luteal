package fr.luteal.core.network

import fr.luteal.core.network.auth.AuthVersion
import fr.luteal.core.network.contract.models.AcceptLink201Response
import fr.luteal.core.network.contract.models.DuoView
import fr.luteal.core.network.contract.models.GrantField
import fr.luteal.core.network.contract.models.Invitation
import fr.luteal.core.network.contract.models.ListLinks200Response
import fr.luteal.core.network.contract.models.Register201Response
import fr.luteal.core.network.contract.models.SupportKind
import fr.luteal.core.network.contract.models.SupportRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Transport for the folicular v1 API. Bodies use the generated contract
 * DTOs / [ContractJson]; the polymorphic sync envelopes are carried by the
 * [PushChangeWire] / [PullResultWire] mirrors (see [SyncWire]).
 */
interface FolicularApiClient {

    /**
     * POST /v1/auth/register (legacy v1) - server generates a 100-bit account code.
     * Kept for backward-compatible tests and interoperability.
     */
    suspend fun register(deviceName: String, inviteCode: String? = null): Register201Response

    /**
     * POST /v1/auth/register (operator-blind v2) - client supplies HKDF-derived
     * 32-byte `auth_key` (hex). Server hashes it before storing and never sees
     * the recovery code or encryption key.
     */
    suspend fun registerV2(
        authKeyHex: String,
        deviceName: String,
        inviteCode: String? = null
    ): RegisterV2Result

    /**
     * POST /v1/auth/devices (legacy v1) - attach this device using a server-minted
     * `LTL-...` account code.
     */
    suspend fun addDevice(accountCode: String, deviceName: String): AddDeviceResult

    /**
     * POST /v1/auth/devices (operator-blind v2) - attach this device using the
     * HKDF-derived `auth_key` (hex). The recovery code never leaves the device.
     */
    suspend fun addDeviceV2(authKeyHex: String, deviceName: String): AddDeviceResult

    /** GET /v1/me - account metadata including `auth_version`. */
    suspend fun getMe(deviceToken: String): AccountMeResult

    /** GET /v1/auth/devices - list devices attached to this account. */
    suspend fun listDevices(deviceToken: String): List<RemoteDevice>

    /** GET /v1/export - full sealed export of server-held records and settings. */
    suspend fun getExport(deviceToken: String): SealedExportResult

    /** POST /v1/auth/upgrade - atomic in-place v1 -> v2 account rotation. */
    suspend fun upgradeAccount(
        deviceToken: String,
        request: AccountUpgradeRequestWire
    ): AccountUpgradeResultWire

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

    /** PUT /v1/duo/payload - publish the sealed projection (tracker only). */
    suspend fun putDuoPayload(deviceToken: String, payload: String)

    /** POST /v1/duo/support-requests - create a support request. */
    suspend fun createSupportRequest(
        deviceToken: String, linkId: String, kind: SupportKind, sealedMessage: String
    ): SupportRequest

    /** PATCH /v1/duo/support-requests/{requestID}/ack - acknowledge a request. */
    suspend fun ackSupportRequest(deviceToken: String, requestId: String)
}

/** Result of registering an operator-blind v2 account. */
data class RegisterV2Result(
    val accountId: String,
    val authVersion: AuthVersion,
    val deviceToken: String
)

/** Result of attaching a device to an existing account. */
data class AddDeviceResult(
    val accountId: String,
    val deviceToken: String,
    val authVersion: AuthVersion = AuthVersion.V1
)

/** Account summary returned by `GET /v1/me`. */
data class AccountMeResult(
    val accountId: String,
    val authVersion: AuthVersion,
    val status: String
)

/** Device summary returned by `GET /v1/auth/devices`. */
data class RemoteDevice(
    val id: String,
    val name: String,
    val revoked: Boolean,
    val current: Boolean
)

/** One record inside a `GET /v1/export` response. */
@Serializable
data class ExportedRecordWire(
    @SerialName("entity_id") val entityId: String,
    @SerialName("entity_type") val entityType: String,
    @SerialName("client_rev") val clientRev: String,
    @SerialName("deleted") val deleted: Boolean,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("ciphertext") val ciphertext: String? = null
)

/** Parsed `GET /v1/export` payload. */
data class SealedExportResult(
    val accountId: String,
    val authVersion: AuthVersion,
    val settingsCiphertext: String?,
    val records: List<ExportedRecordWire>
)

@Serializable
data class UpgradeRecordInputWire(
    @SerialName("entity_type") val entityType: String,
    @SerialName("entity_id") val entityId: String,
    @SerialName("client_rev") val clientRev: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted") val deleted: Boolean,
    @SerialName("ciphertext") val ciphertext: String? = null
)

@Serializable
data class AccountUpgradeRequestWire(
    @SerialName("auth_key") val authKey: String,
    @SerialName("expected_cursor") val expectedCursor: Long,
    @SerialName("revoke_other_devices") val revokeOtherDevices: Boolean = true,
    @SerialName("settings") val settings: String? = null,
    @SerialName("records") val records: List<UpgradeRecordInputWire>
)

@Serializable
data class AccountUpgradeResultWire(
    @SerialName("account_id") val accountId: String,
    @SerialName("auth_version") val authVersion: Int,
    @SerialName("cursor") val cursor: Long,
    @SerialName("records_migrated") val recordsMigrated: Int,
    @SerialName("revoked_devices") val revokedDevices: Long
)

/**
 * Thrown when a self-hosted `folicular` server predates operator-blind v2
 * support (for example, rejecting `auth_key` with HTTP 400 unknown field or
 * returning 404 on `/v1/auth/upgrade`).
 */
class LegacyServerUnsupportedException(
    message: String = "Sync server does not support v2 encryption"
) : RuntimeException(message)

/**
 * A non-2xx API response. [message] carries the RFC 9457 `detail` when the
 * server returned an `application/problem+json` body, else a generic message.
 * Never includes the account code, auth_key, or device token.
 */
class FolicularApiException(
    val status: Int,
    val errorType: String,
    message: String
) : RuntimeException("HTTP $status ($errorType): $message")
