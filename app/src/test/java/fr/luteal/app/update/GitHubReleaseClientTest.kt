package fr.luteal.app.update

import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class GitHubReleaseClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: GitHubReleaseClient
    private lateinit var dir: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = GitHubReleaseClient(
            httpClient = OkHttpClient(),
            latestReleaseUrl = server.url("/repos/JoeriKaiser/luteal/releases/latest").toString(),
            userAgent = "Luteal/2.0.0"
        )
        dir = Files.createTempDirectory("luteal-updates").toFile()
    }

    @After
    fun tearDown() {
        server.shutdown()
        dir.deleteRecursively()
    }

    @Test
    fun `latest release request carries only a generic user agent`() = runTest {
        server.enqueue(MockResponse().setBody(GitHubReleaseParserTest.SAMPLE_RELEASE))

        val release = client.fetchLatestRelease()

        assertEquals("v2.0.0", release.tagName)
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/repos/JoeriKaiser/luteal/releases/latest", recorded.path)
        assertEquals("Luteal/2.0.0", recorded.getHeader("User-Agent"))
        assertNull(recorded.getHeader("Authorization"))
        assertNull(recorded.getHeader("Cookie"))
        assertEquals(0L, recorded.bodySize)
    }

    @Test
    fun `http errors surface as IOException`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"message":"rate limited"}"""))
        try {
            client.fetchLatestRelease()
            fail("expected IOException")
        } catch (_: IOException) {
        }
    }

    @Test
    fun `download writes the body and leaves no partial file`() = runTest {
        val payload = ByteArray(10_000) { (it % 251).toByte() }
        server.enqueue(MockResponse().setBody(Buffer().write(payload)))
        val target = File(dir, "luteal-v2.1.0.apk")

        client.download(server.url("/dl/luteal-v2.1.0.apk").toString(), target)

        assertTrue(payload.contentEquals(target.readBytes()))
        assertFalse(File(target.path + ".part").exists())
        assertEquals("Luteal/2.0.0", server.takeRequest().getHeader("User-Agent"))
    }

    @Test
    fun `download over the size limit is refused and cleaned up`() = runTest {
        server.enqueue(MockResponse().setBody(Buffer().write(ByteArray(2_048))))
        val target = File(dir, "big.apk")
        try {
            client.download(server.url("/dl/big.apk").toString(), target, maxBytes = 1_024)
            fail("expected IOException")
        } catch (_: IOException) {
        }
        assertFalse(target.exists())
        assertFalse(File(target.path + ".part").exists())
    }

    @Test
    fun `failed download leaves no file`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val target = File(dir, "missing.apk")
        try {
            client.download(server.url("/dl/missing.apk").toString(), target)
            fail("expected IOException")
        } catch (_: IOException) {
        }
        assertFalse(target.exists())
    }
}
