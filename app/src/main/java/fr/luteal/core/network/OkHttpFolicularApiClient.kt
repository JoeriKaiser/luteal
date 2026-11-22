package fr.luteal.core.network

import fr.luteal.core.network.auth.AuthVersion
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
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * OkHttp-backed [FolicularApiClient]. All (de)serialization goes through
 * [ContractJson] and the generated DTOs / wire mirrors. Cleartext HTTP is
 * debug-only; release builds sync over HTTPS only.
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

    override suspend fun register(deviceName: String, inviteCode: String?): Register201Response =
        withContext(Dispatchers.IO) {
            val body = ContractJson.encodeToString(
                RegisterRequest.serializer(),
                RegisterRequest(deviceName = deviceName, inviteCode = inviteCode?.ifBlank { null })
            )
            val request = Request.Builder()
                .url("$root/v1/auth/register")
                .post(body.toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(request).execute().use { resp ->
                val text = resp.body.string()
                if (!resp.isSuccessful) throw resp.toApiException(text)
                ContractJson.decodeFromString(Register201Response.serializer(), text)
            }
        }

    override suspend fun registerV2(
        authKeyHex: String,
        deviceName: String,
        inviteCode: String?
    ): RegisterV2Result = withContext(Dispatchers.IO) {
        val body = ContractJson.encodeToString(
            RegisterV2RequestWire.serializer(),
            RegisterV2RequestWire(
                deviceName = deviceName,
                authKey = authKeyHex,
                inviteCode = inviteCode?.ifBlank { null }
            )
        )
        val request = Request.Builder()
            .url("$root/v1/auth/register")
            .post(body.toRequestBody(jsonMediaType))
            .build()
        httpClient.newCall(request).execute().use { resp ->
            val text = resp.body.string()
            if (!resp.isSuccessful) {
                if (resp.code == 400 && text.contains("unknown field", ignoreCase = true)) {
                    throw LegacyServerUnsupportedException()
                }
                throw resp.toApiException(text)
            }
            val parsed = ContractJson.decodeFromString(RegisterV2ResponseWire.serializer(), text)
            RegisterV2Result(
                accountId = parsed.account.id,
                authVersion = AuthVersion.fromInt(parsed.account.authVersion),
                deviceToken = parsed.device.token
            )
        }
    }

    override suspend fun addDevice(accountCode: String, deviceName: String): AddDeviceResult =
        withContext(Dispatchers.IO) {
            val body = ContractJson.encodeToString(
                AddDeviceCodeRequestWire.serializer(),
                AddDeviceCodeRequestWire(code = accountCode, deviceName = deviceName)
            )
            val request = Request.Builder()
                .url("$root/v1/auth/devices")
                .post(body.toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(request).execute().use { resp ->
                val text = resp.body.string()
                if (!resp.isSuccessful) throw resp.toApiException(text)
                val parsed = ContractJson.decodeFromString(AddDeviceResponseWire.serializer(), text)
                AddDeviceResult(
                    accountId = parsed.accountId,
                    deviceToken = parsed.device.token,
                    authVersion = AuthVersion.fromInt(parsed.authVersion ?: 1)
                )
            }
        }

    override suspend fun addDeviceV2(authKeyHex: String, deviceName: String): AddDeviceResult =
        withContext(Dispatchers.IO) {
            val body = ContractJson.encodeToString(
                AddDeviceAuthKeyRequestWire.serializer(),
                AddDeviceAuthKeyRequestWire(authKey = authKeyHex, deviceName = deviceName)
            )
            val request = Request.Builder()
                .url("$root/v1/auth/devices")
                .post(body.toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(request).execute().use { resp ->
                val text = resp.body.string()
                if (!resp.isSuccessful) {
                    if (resp.code == 400 && text.contains("unknown field", ignoreCase = true)) {
                        throw LegacyServerUnsupportedException()
                    }
                    throw resp.toApiException(text)
                }
                val parsed = ContractJson.decodeFromString(AddDeviceResponseWire.serializer(), text)
                AddDeviceResult(
                    accountId = parsed.accountId,
                    deviceToken = parsed.device.token,
                    authVersion = AuthVersion.fromInt(parsed.authVersion ?: 2)
                )
            }
        }

    override suspend fun getMe(deviceToken: String): AccountMeResult =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$root/v1/me")
                .header("Authorization", "Bearer $deviceToken")
                .get()
                .build()
            httpClient.newCall(request).execute().use { resp ->
                val text = resp.body.string()
                if (!resp.isSuccessful) throw resp.toApiException(text)
                val parsed = ContractJson.decodeFromString(MeResponseWire.serializer(), text)
                AccountMeResult(
                    accountId = parsed.account.id,
                    authVersion = AuthVersion.fromInt(parsed.account.authVersion ?: 1),
                    status = parsed.account.status
                )
            }
        }

    override suspend fun listDevices(deviceToken: String): List<RemoteDevice> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$root/v1/auth/devices")
                .header("Authorization", "Bearer $deviceToken")
                .get()
                .build()
            httpClient.newCall(request).execute().use { resp ->
                val text = resp.body.string()
                if (!resp.isSuccessful) throw resp.toApiException(text)
                val parsed = ContractJson.decodeFromString(ListDevicesResponseWire.serializer(), text)
                parsed.devices.map {
                    RemoteDevice(
                        id = it.id,
                        name = it.name,
                        revoked = it.revoked,
                        current = it.current
                    )
                }
            }
        }

    override suspend fun getExport(deviceToken: String): SealedExportResult =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$root/v1/export")
                .header("Authorization", "Bearer $deviceToken")
                .get()
                .build()
            httpClient.newCall(request).execute().use { resp ->
                val text = resp.body.string()
                if (!resp.isSuccessful) throw resp.toApiException(text)
                val parsed = ContractJson.decodeFromString(ExportResponseWire.serializer(), text)
                SealedExportResult(
                    accountId = parsed.account.id,
                    authVersion = AuthVersion.fromInt(parsed.account.authVersion ?: 1),
                    settingsCiphertext = parsed.settings?.settings?.takeIf { it.isNotBlank() },
                    records = parsed.records
                )
            }
        }

    override suspend fun upgradeAccount(
        deviceToken: String,
        request: AccountUpgradeRequestWire
    ): AccountUpgradeResultWire = withContext(Dispatchers.IO) {
        val body = ContractJson.encodeToString(AccountUpgradeRequestWire.serializer(), request)
        val httpReq = Request.Builder()
            .url("$root/v1/auth/upgrade")
            .header("Authorization", "Bearer $deviceToken")
            .post(body.toRequestBody(jsonMediaType))
            .build()
        httpClient.newCall(httpReq).execute().use { resp ->
            val text = resp.body.string()
            if (!resp.isSuccessful) {
                if (resp.code == 404 || resp.code == 405) {
                    throw LegacyServerUnsupportedException()
                }
                throw resp.toApiException(text)
            }
            ContractJson.decodeFromString(AccountUpgradeResultWire.serializer(), text)
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
            httpClient.newCall(request).execute().use { resp ->
                val text = resp.body.string()
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
            httpClient.newCall(request).execute().use { resp ->
                val text = resp.body.string()
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
                val text = resp.body.string()
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
                val text = resp.body.string()
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
                val text = resp.body.string()
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
            val text = resp.body.string()
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
                val text = resp.body.string()
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
                val text = resp.body.string()
                if (!resp.isSuccessful) throw resp.toApiException(text)
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
                if (!resp.isSuccessful) throw resp.toApiException(resp.body.string())
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
            val text = resp.body.string()
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
                val text = resp.body.string()
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
            ?: message.ifBlank { "request failed" }
        return FolicularApiException(code, type, detail)
    }
}

/** Thrown when the device cannot reach the server at all (DNS, refused, timeout). */
class SyncTransportException(message: String, cause: Throwable? = null) : IOException(message, cause)

@Serializable
private data class PutDuoPayloadRequest(
    @SerialName("payload") val payload: String
)

@Serializable
private data class RegisterV2RequestWire(
    @SerialName("device_name") val deviceName: String,
    @SerialName("auth_key") val authKey: String,
    @SerialName("invite_code") val inviteCode: String? = null
)

@Serializable
private data class RegisterV2AccountWire(
    @SerialName("id") val id: String,
    @SerialName("auth_version") val authVersion: Int = 2
)

@Serializable
private data class RegisterV2ResponseWire(
    @SerialName("account") val account: RegisterV2AccountWire,
    @SerialName("device") val device: AddedDeviceWire
)

@Serializable
private data class AddDeviceCodeRequestWire(
    @SerialName("code") val code: String,
    @SerialName("device_name") val deviceName: String
)

@Serializable
private data class AddDeviceAuthKeyRequestWire(
    @SerialName("auth_key") val authKey: String,
    @SerialName("device_name") val deviceName: String
)

@Serializable
private data class AddDeviceResponseWire(
    @SerialName("account_id") val accountId: String,
    @SerialName("auth_version") val authVersion: Int? = null,
    @SerialName("device") val device: AddedDeviceWire
)

@Serializable
private data class AddedDeviceWire(
    @SerialName("token") val token: String
)

@Serializable
private data class MeAccountWire(
    @SerialName("id") val id: String,
    @SerialName("auth_version") val authVersion: Int? = null,
    @SerialName("status") val status: String
)

@Serializable
private data class MeResponseWire(
    @SerialName("account") val account: MeAccountWire
)

@Serializable
private data class DeviceItemWire(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("revoked") val revoked: Boolean,
    @SerialName("current") val current: Boolean
)

@Serializable
private data class ListDevicesResponseWire(
    @SerialName("devices") val devices: List<DeviceItemWire>
)

@Serializable
private data class ExportSettingsWire(
    @SerialName("settings") val settings: String? = null
)

@Serializable
private data class ExportResponseWire(
    @SerialName("account") val account: MeAccountWire,
    @SerialName("settings") val settings: ExportSettingsWire? = null,
    @SerialName("records") val records: List<ExportedRecordWire> = emptyList()
)
