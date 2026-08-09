package fr.luteal.core.network

import fr.luteal.core.network.contract.models.AcceptLink201Response
import fr.luteal.core.network.contract.models.AcceptLinkRequest
import fr.luteal.core.network.contract.models.DuoView
import fr.luteal.core.network.contract.models.GrantField
import fr.luteal.core.network.contract.models.Invitation
import fr.luteal.core.network.contract.models.ListLinks200Response
import fr.luteal.core.network.contract.models.PatchGrantsRequest
import fr.luteal.core.network.contract.models.Problem
import fr.luteal.core.network.contract.models.Register201Response
import fr.luteal.core.network.contract.models.RegisterRequest
import fr.luteal.core.network.contract.models.SupportKind
import fr.luteal.core.network.contract.models.SupportRequest
import java.util.UUID
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * OkHttp-backed [FolicularApiClient]. All (de)serialization goes through
 * [ContractJson] and the generated DTOs. Cleartext HTTP is only reachable in
 * the debug build (see `app/src/debug` network security config); the release
 * build declares no network permission at all.
 */
class OkHttpFolicularApiClient(
    private val baseUrl: String,
    private val httpClient: OkHttpClient
) : FolicularApiClient {

    private val root: String = baseUrl.trimEnd('/')
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private companion object {
        val EMPTY_BODY = "".toRequestBody(null)
    }

    override suspend fun register(deviceName: String, inviteCode: String): Register201Response = withContext(Dispatchers.IO) {
        val body = ContractJson.encodeToString(
            RegisterRequest.serializer(),
            RegisterRequest(deviceName = deviceName, inviteCode = inviteCode.ifBlank { null })
        )
        val request = Request.Builder()
            .url("$root/v1/auth/register")
            .post(body.toRequestBody(jsonMediaType))
            .build()
        val response = httpClient.newCall(request).execute()
        response.use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw resp.toApiException(text)
            ContractJson.decodeFromString(Register201Response.serializer(), text)
        }
    }

    override suspend fun addDevice(accountCode: String, deviceName: String): AddDeviceResult =
        withContext(Dispatchers.IO) {
            val body = ContractJson.encodeToString(
                AddDeviceRequestWire.serializer(),
                AddDeviceRequestWire(code = accountCode, deviceName = deviceName)
            )
            val request = Request.Builder()
                .url("$root/v1/auth/devices")
                .post(body.toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw resp.toApiException(text)
                val parsed = ContractJson.decodeFromString(AddDeviceResponseWire.serializer(), text)
                AddDeviceResult(
                    accountId = parsed.accountId,
                    deviceToken = parsed.device.token
                )
            }
        }

    override suspend fun syncPush(deviceToken: String, changes: List<PushChangeWire>): PushResultWire =
        withContext(Dispatchers.IO) {
            val body = PushRequestWire(changes).toWireString()
            val request = Request.Builder()
                .url("$root/v1/sync/push")
                .header("Authorization", "Bearer $deviceToken")
                .post(body.toRequestBody(jsonMediaType))
                .build()
            val response = httpClient.newCall(request).execute()
            response.use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw resp.toApiException(text)
                text.toPushResultWire()
            }
        }

    override suspend fun syncPull(deviceToken: String, since: Long, limit: Int): PullResultWire =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$root/v1/sync/pull?since=$since&limit=$limit")
                .header("Authorization", "Bearer $deviceToken")
                .get()
                .build()
            val response = httpClient.newCall(request).execute()
            response.use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw resp.toApiException(text)
                text.toPullResultWire()
            }
        }

    // --- Duo ---------------------------------------------------------------

    override suspend fun createInvitation(deviceToken: String): Invitation =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$root/v1/duo/invitations")
                .header("Authorization", "Bearer $deviceToken")
                .post(EMPTY_BODY)
                .build()
            httpClient.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw resp.toApiException(text)
                ContractJson.decodeFromString(Invitation.serializer(), text)
            }
        }

    override suspend fun acceptLink(deviceToken: String, pairingCode: String): AcceptLink201Response =
        withContext(Dispatchers.IO) {
            val body = ContractJson.encodeToString(
                AcceptLinkRequest.serializer(), AcceptLinkRequest(pairingCode)
            )
            val request = Request.Builder()
                .url("$root/v1/duo/links")
                .header("Authorization", "Bearer $deviceToken")
                .post(body.toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw resp.toApiException(text)
                ContractJson.decodeFromString(AcceptLink201Response.serializer(), text)
            }
        }

    override suspend fun listLinks(deviceToken: String): ListLinks200Response =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$root/v1/duo/links")
                .header("Authorization", "Bearer $deviceToken")
                .get()
                .build()
            httpClient.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw resp.toApiException(text)
                ContractJson.decodeFromString(ListLinks200Response.serializer(), text)
            }
        }

    override suspend fun patchGrants(
        deviceToken: String, linkId: String, field: GrantField, granted: Boolean
    ): Unit = withContext(Dispatchers.IO) {
        val body = ContractJson.encodeToString(
            PatchGrantsRequest.serializer(), PatchGrantsRequest(field, granted)
        )
        val request = Request.Builder()
            .url("$root/v1/duo/links/$linkId/grants")
            .header("Authorization", "Bearer $deviceToken")
            .patch(body.toRequestBody(jsonMediaType))
            .build()
        httpClient.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw resp.toApiException(text)
        }
    }

    override suspend fun revokeLink(deviceToken: String, linkId: String): Unit =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$root/v1/duo/links/$linkId")
                .header("Authorization", "Bearer $deviceToken")
                .delete()
                .build()
            httpClient.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw resp.toApiException(text)
            }
        }
    override suspend fun duoView(deviceToken: String): DuoView =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$root/v1/duo/view")
                .header("Authorization", "Bearer $deviceToken")
                .get()
                .build()
            httpClient.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw resp.toApiException(text)
                // The generated DuoView cannot decode the base64 payload and
                // message ciphertexts (see DuoWire.kt); go through the wire
                // mirror instead.
                ContractJson.decodeFromString(DuoViewWire.serializer(), text).toModel()
            }
        }

    override suspend fun putDuoPayload(deviceToken: String, payload: String) =
        withContext(Dispatchers.IO) {
            val body = ContractJson.encodeToString(
                PutDuoPayloadRequest.serializer(), PutDuoPayloadRequest(payload)
            )
            val request = Request.Builder()
                .url("$root/v1/duo/payload")
                .header("Authorization", "Bearer $deviceToken")
                .put(body.toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw resp.toApiException(resp.body?.string().orEmpty())
            }
        }
    override suspend fun createSupportRequest(
        deviceToken: String, linkId: String, kind: SupportKind, sealedMessage: String
    ): SupportRequest = withContext(Dispatchers.IO) {
        val body = ContractJson.encodeToString(
            CreateSupportRequestRequestWire.serializer(),
            CreateSupportRequestRequestWire(UUID.fromString(linkId), kind, sealedMessage)
        )
        val request = Request.Builder()
            .url("$root/v1/duo/support-requests")
            .header("Authorization", "Bearer $deviceToken")
            .post(body.toRequestBody(jsonMediaType))
            .build()
        httpClient.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw resp.toApiException(text)
            ContractJson.decodeFromString(SupportRequestWire.serializer(), text).toModel()
        }
    }

    override suspend fun ackSupportRequest(deviceToken: String, requestId: String): Unit =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$root/v1/duo/support-requests/$requestId/ack")
                .header("Authorization", "Bearer $deviceToken")
                .patch(EMPTY_BODY)
                .build()
            httpClient.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw resp.toApiException(text)
            }
        }

    private fun okhttp3.Response.toApiException(body: String): FolicularApiException {
        val problem = runCatching {
            ContractJson.decodeFromString(Problem.serializer(), body)
        }.getOrNull()
        val type = problem?.type ?: "about:blank"
        val detail = problem?.detail?.takeIf { it.isNotBlank() }
            ?: problem?.title
            ?: body.takeIf { it.isNotBlank() }
            ?: message
            ?: "request failed"
        return FolicularApiException(code, type, detail)
    }
}

/** Thrown when the device cannot reach the server at all (DNS, refused, timeout). */
class SyncTransportException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Request body for PUT /v1/duo/payload. The payload is base64 ciphertext. */
@kotlinx.serialization.Serializable
private data class PutDuoPayloadRequest(
    @kotlinx.serialization.SerialName("payload") val payload: String
)

/** Request body for POST /v1/auth/devices (account recovery). */
@kotlinx.serialization.Serializable
private data class AddDeviceRequestWire(
    @kotlinx.serialization.SerialName("code") val code: String,
    @kotlinx.serialization.SerialName("device_name") val deviceName: String
)

@kotlinx.serialization.Serializable
private data class AddDeviceResponseWire(
    @kotlinx.serialization.SerialName("account_id") val accountId: String,
    @kotlinx.serialization.SerialName("device") val device: AddedDeviceWire
)

@kotlinx.serialization.Serializable
private data class AddedDeviceWire(
    @kotlinx.serialization.SerialName("token") val token: String
)
