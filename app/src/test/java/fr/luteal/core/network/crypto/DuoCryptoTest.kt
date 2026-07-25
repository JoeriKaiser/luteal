package fr.luteal.core.network.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.GeneralSecurityException

class DuoCryptoTest {

    private val linkId = "019832e1-0000-7000-8000-000000000abc"
    private val serverPairingUrl = "https://luteal-api.waldemar.site/accept?code=LTL-A1B2C-D3E4F"

    // --- Key generation -----------------------------------------------------

    @Test
    fun `generates 256-bit keys that differ every time`() {
        val a = DuoCrypto.generateLinkKey()
        val b = DuoCrypto.generateLinkKey()

        assertEquals(32, a.size)
        assertEquals(32, b.size)
        assertNotEquals(a.toList(), b.toList())
    }

    @Test
    fun `key survives an encode decode round trip`() {
        val key = DuoCrypto.generateLinkKey()
        assertArrayEquals(key, DuoCrypto.decodeKey(DuoCrypto.encodeKey(key)))
    }

    @Test
    fun `encoded key is URL safe and unpadded`() {
        repeat(20) {
            val encoded = DuoCrypto.encodeKey(DuoCrypto.generateLinkKey())
            assertTrue("must not contain '+': $encoded", !encoded.contains('+'))
            assertTrue("must not contain '/': $encoded", !encoded.contains('/'))
            assertTrue("must not be padded: $encoded", !encoded.contains('='))
        }
    }

    @Test
    fun `rejects a key of the wrong size`() {
        val short = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(ByteArray(16))
        assertThrows(GeneralSecurityException::class.java) { DuoCrypto.decodeKey(short) }
    }

    // --- Pairing link -------------------------------------------------------

    @Test
    fun `key travels in the fragment, never in the server-visible part`() {
        val key = DuoCrypto.generateLinkKey()
        val url = DuoCrypto.shareableUrl(serverPairingUrl, key)

        val beforeFragment = url.substringBefore('#')
        val encodedKey = DuoCrypto.encodeKey(key)

        // A fragment is never sent to a server. This assertion is the whole
        // reason the design puts the key there.
        assertEquals(serverPairingUrl, beforeFragment)
        assertTrue("key must not appear before the fragment", !beforeFragment.contains(encodedKey))
        assertTrue(url.contains("#k=$encodedKey"))
    }

    @Test
    fun `parses a shareable link back into code and key`() {
        val key = DuoCrypto.generateLinkKey()
        val url = DuoCrypto.shareableUrl(serverPairingUrl, key)

        val pairing = DuoCrypto.parsePairing(url)!!

        assertEquals("LTL-A1B2C-D3E4F", pairing.code)
        assertArrayEquals(key, pairing.linkKey)
    }

    @Test
    fun `tolerates surrounding whitespace when pasted`() {
        val key = DuoCrypto.generateLinkKey()
        val url = DuoCrypto.shareableUrl(serverPairingUrl, key)

        val pairing = DuoCrypto.parsePairing("  \n$url\n ")!!

        assertEquals("LTL-A1B2C-D3E4F", pairing.code)
        assertArrayEquals(key, pairing.linkKey)
    }

    @Test
    fun `rejects a bare pairing code with no key`() {
        // Without the fragment there is no key, so the Duo could not be
        // encrypted. Accepting this would silently downgrade security.
        assertNull(DuoCrypto.parsePairing("LTL-A1B2C-D3E4F"))
        assertNull(DuoCrypto.parsePairing(serverPairingUrl))
    }

    @Test
    fun `rejects a link with a fragment but no code`() {
        val key = DuoCrypto.encodeKey(DuoCrypto.generateLinkKey())
        assertNull(DuoCrypto.parsePairing("https://luteal-api.example/accept#k=$key"))
    }

    @Test
    fun `url-encoded codes are decoded`() {
        val key = DuoCrypto.generateLinkKey()
        val url = "https://luteal-api.example/accept?code=LTL-A%2BB&x=1#k=" +
            DuoCrypto.encodeKey(key)

        assertEquals("LTL-A+B", DuoCrypto.parsePairing(url)!!.code)
    }

    // --- Payload sealing ----------------------------------------------------

    @Test
    fun `round trips a payload for the same link`() {
        val key = DuoCrypto.generateLinkKey()
        val plaintext = """{"cycle_day":12}""".toByteArray()

        val sealed = DuoCrypto.seal(key, linkId, plaintext)
        assertArrayEquals(plaintext, DuoCrypto.open(key, linkId, sealed))
    }

    @Test
    fun `raw and base64 forms interoperate`() {
        val key = DuoCrypto.generateLinkKey()
        val plaintext = "message de soutien".toByteArray()

        val raw = DuoCrypto.sealRaw(key, linkId, plaintext)
        assertArrayEquals(plaintext, DuoCrypto.openRaw(key, linkId, raw))
    }

    @Test
    fun `ciphertext does not contain the plaintext`() {
        val key = DuoCrypto.generateLinkKey()
        val sealed = DuoCrypto.seal(key, linkId, "humeur difficile".toByteArray())

        assertTrue(!sealed.contains("humeur"))
    }

    @Test
    fun `rejects the wrong link key`() {
        val sealed = DuoCrypto.seal(DuoCrypto.generateLinkKey(), linkId, "x".toByteArray())

        assertThrows(GeneralSecurityException::class.java) {
            DuoCrypto.open(DuoCrypto.generateLinkKey(), linkId, sealed)
        }
    }

    @Test
    fun `rejects a payload replayed onto a different link`() {
        val key = DuoCrypto.generateLinkKey()
        val sealed = DuoCrypto.seal(key, linkId, "x".toByteArray())

        assertThrows(GeneralSecurityException::class.java) {
            DuoCrypto.open(key, "019832e1-0000-7000-8000-00000000ffff", sealed)
        }
    }

    @Test
    fun `rejects tampered ciphertext`() {
        val key = DuoCrypto.generateLinkKey()
        val raw = DuoCrypto.sealRaw(key, linkId, "x".toByteArray())
        raw[raw.size - 1] = (raw[raw.size - 1] + 1).toByte()

        assertThrows(GeneralSecurityException::class.java) {
            DuoCrypto.openRaw(key, linkId, raw)
        }
    }

    @Test
    fun `rejects malformed base64`() {
        assertThrows(GeneralSecurityException::class.java) {
            DuoCrypto.open(DuoCrypto.generateLinkKey(), linkId, "not base64 !!!")
        }
    }

    @Test
    fun `same plaintext seals differently each time`() {
        val key = DuoCrypto.generateLinkKey()

        val a = DuoCrypto.seal(key, linkId, "identique".toByteArray())
        val b = DuoCrypto.seal(key, linkId, "identique".toByteArray())

        assertNotEquals(a, b)
    }
}
