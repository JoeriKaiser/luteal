package fr.luteal.core.network

import fr.luteal.core.network.auth.AuthVersion
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OkHttpFolicularApiClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: FolicularApiClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpFolicularApiClient(server.url("/").toString(), OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `register posts device name and parses account and token`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """
                {"account":{"id":"019832e0-6c14-7000-8000-0000000000aa",
                            "auth_version":1,
                            "code":"LTL-8K3FQ-Z2WNT-7HJMC-4XRDB"},
                 "device":{"id":"019832e0-6c14-7000-8000-0000000000bb",
                           "name":"Pixel 9","token":"ltok_abc"},
                 "warning":"Conservez votre code."}
                """.trimIndent()
            )
        )

        val response = client.register("Pixel 9")

        assertEquals("LTL-8K3FQ-Z2WNT-7HJMC-4XRDB", response.account.code)
        assertEquals("ltok_abc", response.device.token)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/v1/auth/register", recorded.path)
        assertTrue(recorded.body.readUtf8().contains("\"device_name\":\"Pixel 9\""))
    }

    @Test
    fun `registerV2 posts auth_key and parses v2 response`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """
                {"account":{"id":"019832e0-6c14-7000-8000-0000000000aa","auth_version":2},
                 "device":{"id":"019832e0-6c14-7000-8000-0000000000bb","name":"Pixel 9","token":"ltok_v2"},
                 "warning":"Conservez votre code."}
                """.trimIndent()
            )
        )

        val authKeyHex = "a".repeat(64)
        val result = client.registerV2(authKeyHex, "Pixel 9")

        assertEquals("019832e0-6c14-7000-8000-0000000000aa", result.accountId)
        assertEquals(AuthVersion.V2, result.authVersion)
        assertEquals("ltok_v2", result.deviceToken)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"auth_key\":\"$authKeyHex\""))
    }

    @Test
    fun `registerV2 maps legacy server unknown field 400 to LegacyServerUnsupportedException`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(400).setBody(
                """{"type":"about:blank","title":"Corps invalide","status":400,"detail":"JSON invalide : json: unknown field \"auth_key\""}"""
            )
        )

        val err = runCatching { client.registerV2("a".repeat(64), "Pixel 9") }.exceptionOrNull()
        assertTrue("expected LegacyServerUnsupportedException, got $err", err is LegacyServerUnsupportedException)
    }

    @Test
    fun `register sends invite code when provided`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """
                {"account":{"id":"019832e0-6c14-7000-8000-0000000000aa",
                            "auth_version":1,
                            "code":"LTL-8K3FQ-Z2WNT-7HJMC-4XRDB"},
                 "device":{"id":"019832e0-6c14-7000-8000-0000000000bb",
                           "name":"Pixel 9","token":"ltok_abc"},
                 "warning":"Conservez votre code."}
                """.trimIndent()
            )
        )

        client.register("Pixel 9", "BETA-1234")

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"device_name\":\"Pixel 9\""))
        assertTrue(body.contains("\"invite_code\":\"BETA-1234\""))
    }

    @Test
    fun `sync push sends bearer token and parses applied and cursor`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"applied":[{"entity_type":"cycle",
                             "entity_id":"019832e0-6c14-7000-8000-000000000001","seq":3}],
                 "rejected":[],"conflicts":[],"cursor":3}
                """.trimIndent()
            )
        )

        val result = client.syncPush("ltok_abc", changes = emptyList())

        assertEquals(3L, result.cursor)
        assertEquals(1, result.applied.size)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/v1/sync/push", recorded.path)
        assertEquals("Bearer ltok_abc", recorded.getHeader("Authorization"))
    }

    @Test
    fun `sync pull passes since and limit and parses result`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"changes":[],"cursor":42,"has_more":true}"""
            )
        )

        val result = client.syncPull("ltok_abc", since = 41L, limit = 100)

        assertEquals(42L, result.cursor)
        assertTrue(result.hasMore)

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/v1/sync/pull?since=41&limit=100", recorded.path)
        assertEquals("Bearer ltok_abc", recorded.getHeader("Authorization"))
    }

    @Test
    fun `problem response surfaces status and detail`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(422).setBody(
                """
                {"type":"about:blank","title":"Validation failed","status":422,
                 "detail":"changes[0]: flow: invalid value 'torrential'",
                 "instance":"/v1/sync/push"}
                """.trimIndent()
            )
        )

        val failure = runCatching { client.syncPush("ltok_abc", emptyList()) }

        assertTrue(failure.isFailure)
        val exception = failure.exceptionOrNull() as FolicularApiException
        assertEquals(422, exception.status)
        assertTrue(exception.message!!.contains("torrential"))
    }
}
