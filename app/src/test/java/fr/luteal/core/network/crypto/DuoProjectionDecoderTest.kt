package fr.luteal.core.network.crypto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fr.luteal.core.model.DuoProjection
import fr.luteal.core.model.SharedLevel
import fr.luteal.core.network.ContractJson
import fr.luteal.core.network.contract.models.DuoRole
import fr.luteal.core.network.contract.models.DuoView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.OffsetDateTime
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class DuoProjectionDecoderTest {

    private class InMemoryDuoKeyStore(context: Context) : DuoKeyStore(context) {
        private val keys = mutableMapOf<String, ByteArray>()
        override fun save(linkId: String, linkKey: ByteArray) { keys[linkId] = linkKey }
        override fun load(linkId: String): ByteArray? = keys[linkId]
        override fun remove(linkId: String) { keys.remove(linkId) }
        override fun clear() { keys.clear() }
    }

    private lateinit var keyStore: DuoKeyStore
    private lateinit var decoder: DuoProjectionDecoder

    private val linkUuid = UUID.fromString("019832e1-0000-7000-8000-000000000abc")
    private val linkId = linkUuid.toString()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        keyStore = InMemoryDuoKeyStore(context)
        decoder = DuoProjectionDecoder(keyStore)
    }

    @Test
    fun `decodes a projection sealed with sealRaw`() {
        val linkKey = DuoCrypto.generateLinkKey()
        keyStore.save(linkId, linkKey)

        val projection = DuoProjection(
            version = DuoProjection.CURRENT_VERSION,
            cycleDay = 14,
            mood = SharedLevel(date = "2026-09-02", level = 3),
            energy = SharedLevel(date = "2026-09-02", level = 4)
        )
        val plaintext = ContractJson.encodeToString(DuoProjection.serializer(), projection).toByteArray()
        val sealed = DuoCrypto.sealRaw(linkKey, linkId, plaintext)

        val view = DuoView(
            linkId = linkUuid,
            role = DuoRole.TRACKER,
            asOf = OffsetDateTime.now(),
            payload = sealed
        )

        val result = decoder.decode(view)
        assertTrue(result is DuoProjectionDecodeResult.Available)
        assertEquals(projection, (result as DuoProjectionDecodeResult.Available).projection)
    }

    @Test
    fun `returns NoPayload when view payload is null`() {
        val view = DuoView(
            linkId = linkUuid,
            role = DuoRole.TRACKER,
            asOf = OffsetDateTime.now(),
            payload = null
        )

        val result = decoder.decode(view)
        assertEquals(DuoProjectionDecodeResult.NoPayload, result)
    }

    @Test
    fun `returns KeyMissing when key is not in store`() {
        val view = DuoView(
            linkId = linkUuid,
            role = DuoRole.TRACKER,
            asOf = OffsetDateTime.now(),
            payload = byteArrayOf(1, 2, 3)
        )

        val result = decoder.decode(view)
        assertEquals(DuoProjectionDecodeResult.KeyMissing, result)
    }

    @Test
    fun `returns InvalidPayload when ciphertext is corrupted`() {
        val linkKey = DuoCrypto.generateLinkKey()
        keyStore.save(linkId, linkKey)

        val view = DuoView(
            linkId = linkUuid,
            role = DuoRole.TRACKER,
            asOf = OffsetDateTime.now(),
            payload = byteArrayOf(1, 2, 3, 4, 5)
        )

        val result = decoder.decode(view)
        assertEquals(DuoProjectionDecodeResult.InvalidPayload, result)
    }
}
