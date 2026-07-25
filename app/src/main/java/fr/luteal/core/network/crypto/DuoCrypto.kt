package fr.luteal.core.network.crypto

import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64

/**
 * Key agreement and sealing for the Duo link.
 *
 * ## Why the link key travels in the pairing URL fragment
 *
 * The pairing code is 50 bits, short-lived and single-use. That is adequate as
 * a bearer secret on a rate-limited endpoint, but it is far too small to be an
 * encryption key: the server holds the ciphertext and could simply try every
 * code.
 *
 * The obvious alternative, server-mediated X25519, has a worse property than it
 * first appears. The server introduces the two devices to each other, so a
 * malicious operator can substitute its own public keys and read everything -
 * and closing that needs an out-of-band safety-number comparison, which is
 * exactly the out-of-band channel the pairing link already is.
 *
 * So the tracker generates a random 256-bit link key and puts it in the
 * **fragment** of the pairing URL. Fragments are never sent to a server, and
 * this one is built entirely on the client, so the key reaches the partner over
 * whatever channel the users already chose for the link (QR, message, in
 * person) and never touches folicular at all. The server cannot MITM a key it
 * never sees.
 *
 * The trust model is unchanged from the pairing link itself: whoever can read
 * the link can join the Duo. That was already true of the bare pairing code.
 */
object DuoCrypto {

    private const val KEY_LENGTH_BYTES = 32
    private const val FRAGMENT_PREFIX = "#k="

    /** URL-safe and unpadded, so the key survives inside a URL intact. */
    private val urlEncoder = Base64.getUrlEncoder().withoutPadding()
    private val urlDecoder = Base64.getUrlDecoder()

    private val PAYLOAD_INFO = "luteal/v1/duo/payload".toByteArray()

    fun generateLinkKey(random: SecureRandom = SecureRandom()): ByteArray =
        ByteArray(KEY_LENGTH_BYTES).also(random::nextBytes)

    fun encodeKey(key: ByteArray): String = urlEncoder.encodeToString(key)

    fun decodeKey(encoded: String): ByteArray {
        val key = try {
            urlDecoder.decode(encoded)
        } catch (e: IllegalArgumentException) {
            throw GeneralSecurityException("Clé de lien Duo illisible", e)
        }
        if (key.size != KEY_LENGTH_BYTES) {
            throw GeneralSecurityException("Clé de lien Duo de taille invalide")
        }
        return key
    }

    /**
     * Appends the link key to the server-supplied pairing URL as a fragment.
     * The server generated everything before the `#` and never learns what
     * follows it.
     */
    fun shareableUrl(serverPairingUrl: String, linkKey: ByteArray): String =
        serverPairingUrl.substringBefore('#') + FRAGMENT_PREFIX + encodeKey(linkKey)

    /** A pairing link split into the parts each side needs. */
    data class Pairing(val code: String, val linkKey: ByteArray)

    /**
     * Parses what the partner pasted. Accepts a full pairing URL; a bare code
     * is rejected because without the fragment there is no key and the Duo
     * could not be encrypted.
     */
    fun parsePairing(input: String): Pairing? {
        val trimmed = input.trim()
        val fragment = trimmed.substringAfter(FRAGMENT_PREFIX, "")
        if (fragment.isBlank()) return null

        val beforeFragment = trimmed.substringBefore('#')
        val code = Regex("[?&]code=([^&]+)").find(beforeFragment)
            ?.groupValues?.get(1)
            ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
            ?: return null

        return Pairing(code = code, linkKey = decodeKey(fragment))
    }

    /** Per-link payload key, domain-separated from the link key itself. */
    private fun payloadKey(linkKey: ByteArray, linkId: String): ByteArray =
        Hkdf.derive(
            inputKeyMaterial = linkKey,
            salt = linkId.toByteArray(),
            info = PAYLOAD_INFO,
            length = KEY_LENGTH_BYTES
        )

    /** Seals to raw bytes, for fields the contract types as `format: byte`. */
    fun sealRaw(linkKey: ByteArray, linkId: String, plaintext: ByteArray): ByteArray =
        RecordCrypto.seal(
            key = payloadKey(linkKey, linkId),
            plaintext = plaintext,
            associatedData = linkId.toByteArray()
        )

    /**
     * @throws GeneralSecurityException when the payload was tampered with or
     * was sealed for a different link.
     */
    fun openRaw(linkKey: ByteArray, linkId: String, sealed: ByteArray): ByteArray =
        RecordCrypto.open(
            key = payloadKey(linkKey, linkId),
            envelope = sealed,
            associatedData = linkId.toByteArray()
        )

    fun seal(linkKey: ByteArray, linkId: String, plaintext: ByteArray): String =
        Base64.getEncoder().encodeToString(sealRaw(linkKey, linkId, plaintext))

    /**
     * @throws GeneralSecurityException when the payload was tampered with or
     * was sealed for a different link.
     */
    fun open(linkKey: ByteArray, linkId: String, ciphertext: String): ByteArray {
        val sealed = try {
            Base64.getDecoder().decode(ciphertext)
        } catch (e: IllegalArgumentException) {
            throw GeneralSecurityException("Charge utile Duo illisible", e)
        }
        return openRaw(linkKey, linkId, sealed)
    }
}
