package fr.luteal.core.network.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HKDF-SHA256 (RFC 5869).
 *
 * Implemented over `javax.crypto.Mac` rather than pulled from a dependency:
 * the algorithm is short, the platform provides HMAC-SHA256 on every supported
 * API level, and keeping it in-tree means the key hierarchy is auditable in one
 * file. F-Droid builds from source, so fewer opaque dependencies in the crypto
 * path is a feature.
 */
object Hkdf {

    private const val HMAC_SHA256 = "HmacSha256"
    private const val HASH_LENGTH = 32

    /**
     * RFC 5869 Extract: condense possibly-structured input keying material
     * into a uniformly random pseudo-random key.
     */
    fun extract(salt: ByteArray, inputKeyMaterial: ByteArray): ByteArray {
        // RFC 5869 section 2.2: an all-zero salt of hash length is used when
        // no salt is supplied.
        val effectiveSalt = if (salt.isEmpty()) ByteArray(HASH_LENGTH) else salt
        return hmac(effectiveSalt, inputKeyMaterial)
    }

    /**
     * RFC 5869 Expand: stretch a pseudo-random key into [length] bytes bound to
     * [info], which provides domain separation between derived keys.
     */
    fun expand(pseudoRandomKey: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length > 0) { "length must be positive" }
        require(length <= 255 * HASH_LENGTH) { "length exceeds HKDF maximum" }

        val output = ByteArray(length)
        var block = ByteArray(0)
        var generated = 0
        var counter = 1

        while (generated < length) {
            val input = ByteArray(block.size + info.size + 1)
            block.copyInto(input, 0)
            info.copyInto(input, block.size)
            input[input.size - 1] = counter.toByte()

            block = hmac(pseudoRandomKey, input)
            val take = minOf(block.size, length - generated)
            block.copyInto(output, generated, 0, take)
            generated += take
            counter++
        }
        return output
    }

    /** Convenience: Extract then Expand in one call. */
    fun derive(
        inputKeyMaterial: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        length: Int
    ): ByteArray = expand(extract(salt, inputKeyMaterial), info, length)

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_SHA256)
        mac.init(SecretKeySpec(key, HMAC_SHA256))
        return mac.doFinal(data)
    }
}
