package fr.luteal.core.network

import fr.luteal.core.network.contract.models.Problem
import fr.luteal.core.network.contract.models.Register201Response
import fr.luteal.core.network.contract.models.RegisterRequest
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

    override suspend fun register(deviceName: String): Register201Response = withContext(Dispatchers.IO) {
        val body = ContractJson.encodeToString(RegisterRequest.serializer(), RegisterRequest(deviceName))
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
