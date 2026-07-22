package fr.luteal.core.network

import fr.luteal.core.network.contract.models.Register201Response

/**
 * Minimal transport for the folicular v1 API - just what the cycles-only sync
 * slice needs. Bodies use the generated contract DTOs / [ContractJson]; the
 * polymorphic sync envelopes are carried by the [PushChangeWire] /
 * [PullResultWire] mirrors (see [SyncWire]).
 *
 * A client is constructed for a specific base URL (dev targeting: emulator
 * `http://10.0.2.2:8080`, a real device `http://127.0.0.1:8080` via
 * `adb reverse`, or the host's LAN IP).
 */
interface FolicularApiClient {

    /** POST /v1/auth/register - create an anonymous account + first device. */
    suspend fun register(deviceName: String): Register201Response

    /** POST /v1/sync/push - push a batch of changes; server validates each. */
    suspend fun syncPush(deviceToken: String, changes: List<PushChangeWire>): PushResultWire

    /** GET /v1/sync/pull?since&limit - pull changes after the cursor. */
    suspend fun syncPull(deviceToken: String, since: Long, limit: Int = 500): PullResultWire
}

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
