package fr.luteal.core.network.crypto

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * RFC 5869 Appendix A published test vectors for HKDF-SHA256.
 *
 * These are the authoritative vectors from the specification. If this file
 * fails, the key hierarchy is wrong and every ciphertext derived from it is
 * suspect.
 */
class HkdfTest {

    private fun hex(s: String): ByteArray =
        s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

    @Test
    fun `RFC 5869 test case 1 - basic with salt and info`() {
        val ikm = hex("0b".repeat(22))
        val salt = hex("000102030405060708090a0b0c")
        val info = hex("f0f1f2f3f4f5f6f7f8f9")

        val prk = Hkdf.extract(salt, ikm)
        assertEquals(
            "077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5",
            prk.hex()
        )

        val okm = Hkdf.expand(prk, info, 42)
        assertEquals(
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
            okm.hex()
        )
    }

    @Test
    fun `RFC 5869 test case 2 - longer inputs and outputs`() {
        val ikm = hex((0..79).joinToString("") { "%02x".format(it) })
        val salt = hex((96..175).joinToString("") { "%02x".format(it) })
        val info = hex((176..255).joinToString("") { "%02x".format(it) })

        val prk = Hkdf.extract(salt, ikm)
        assertEquals(
            "06a6b88c5853361a06104c9ceb35b45cef760014904671014a193f40c15fc244",
            prk.hex()
        )

        val okm = Hkdf.expand(prk, info, 82)
        assertEquals(
            "b11e398dc80327a1c8e7f78c596a49344f012eda2d4efad8a050cc4c19afa97c" +
                "59045a99cac7827271cb41c65e590e09da3275600c2f09b8367793a9aca3db71" +
                "cc30c58179ec3e87c14c01d5c1f3434f1d87",
            okm.hex()
        )
    }

    @Test
    fun `RFC 5869 test case 3 - empty salt and info`() {
        val ikm = hex("0b".repeat(22))

        val prk = Hkdf.extract(ByteArray(0), ikm)
        assertEquals(
            "19ef24a32c717b167f33a91d6f648bdf96596776afdb6377ac434c1c293ccb04",
            prk.hex()
        )

        val okm = Hkdf.expand(prk, ByteArray(0), 42)
        assertEquals(
            "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8",
            okm.hex()
        )
    }

    @Test
    fun `different info labels yield independent keys`() {
        val prk = Hkdf.extract("salt".toByteArray(), "material".toByteArray())

        val a = Hkdf.expand(prk, "luteal/v1/record".toByteArray(), 32)
        val b = Hkdf.expand(prk, "luteal/v1/duo".toByteArray(), 32)

        assertEquals(32, a.size)
        assertEquals(32, b.size)
        org.junit.Assert.assertNotEquals(a.hex(), b.hex())
    }
}
