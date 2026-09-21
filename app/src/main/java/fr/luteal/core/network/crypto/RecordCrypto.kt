package fr.luteal.core.network.crypto

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end encryption of synchronized record content.
 *
 * ## Key hierarchy
 *
 * ```
 * account code (100 bits). The server mints it at registration and the client
 * sends it again on addDevice. It is not a secret the server has never seen.
 *   |
 *   +-- SHA-256(code)                     -> auth hash, stored server-side
 *   |
 *   +-- HKDF(code, salt=account_id,
 *            info="luteal/v1/master")     -> master key, never leaves device
 *         |
 *         +-- HKDF-Expand(info="luteal/v1/record") -> record content key
 *         +-- HKDF-Expand(info="luteal/v1/duo")    -> Duo key material
 * ```
 *
 * The server already stores only `SHA-256(normalized_code)` for authentication.
 * The master key uses HKDF with a distinct `info` label, so the stored auth
 * hash yields nothing usable for decryption. The account code carries 100 bits
 * of entropy, which is why a plain KDF is appropriate here: a slow
 * password-hardening KDF such as Argon2id exists to compensate for low-entropy
 * human-chosen secrets, and buys nothing against a uniformly random 100-bit
 * value.
 *
 * ## Record sealing
 *
 * AES-256-GCM with a fresh random 96-bit nonce per record. The associated data
 * binds each ciphertext to its own routing metadata, so a server that reorders
 * or swaps payloads between records produces an authentication failure rather
 * than silently mismatched data.
 *
 * Wire format: `0x01 || nonce(12) || ciphertext || tag(16)`.
 */
object RecordCrypto {

    private const val AES_GCM = "AES/GCM/NoPadding"
    private const val AES = "AES"
    private const val KEY_LENGTH_BYTES = 32
    private const val NONCE_LENGTH_BYTES = 12
    private const val TAG_LENGTH_BITS = 128

    /** Format version, so the envelope can evolve without ambiguity. */
    const val VERSION: Byte = 0x01

    private val MASTER_INFO = "luteal/v1/master".toByteArray()
    private val RECORD_INFO = "luteal/v1/record".toByteArray()
    private val DUO_INFO = "luteal/v1/duo".toByteArray()

    /**
     * Canonical form of an account code, matching the server's
     * `auth.NormalizeCode`: prefix and separators stripped, uppercased.
     * Both sides must agree exactly or no key derived here will ever match.
     */
    fun normalizeAccountCode(input: String): String =
        input.trim()
            .uppercase()
            .removePrefix("LTL-")
            .replace("-", "")
            .replace(" ", "")

    /**
     * Derives the per-account master key. [accountId] is not secret; it is the
     * HKDF salt, which only needs to be stable and account-unique.
     */
    fun deriveMasterKey(accountCode: String, accountId: String): ByteArray =
        Hkdf.derive(
            inputKeyMaterial = normalizeAccountCode(accountCode).toByteArray(),
            salt = accountId.toByteArray(),
            info = MASTER_INFO,
            length = KEY_LENGTH_BYTES
        )

    /** Content key for synchronized records. */
    fun deriveRecordKey(masterKey: ByteArray): ByteArray =
        Hkdf.expand(masterKey, RECORD_INFO, KEY_LENGTH_BYTES)

    /** Root key material for Duo key agreement. */
    fun deriveDuoRootKey(masterKey: ByteArray): ByteArray =
        Hkdf.expand(masterKey, DUO_INFO, KEY_LENGTH_BYTES)

    /**
     * Associated data binding a ciphertext to the routing metadata the server
     * can see. Any server-side substitution of one record's payload for
     * another fails authentication on decrypt.
     */
    fun associatedData(entityType: String, entityId: String, clientRev: String): ByteArray =
        "$entityType\u0000$entityId\u0000$clientRev".toByteArray()

    fun seal(
        key: ByteArray,
        plaintext: ByteArray,
        associatedData: ByteArray,
        random: SecureRandom = SecureRandom()
    ): ByteArray {
        require(key.size == KEY_LENGTH_BYTES) { "record key must be 32 bytes" }

        val nonce = ByteArray(NONCE_LENGTH_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, AES),
            GCMParameterSpec(TAG_LENGTH_BITS, nonce)
        )
        cipher.updateAAD(associatedData)
        val sealed = cipher.doFinal(plaintext)

        return ByteArray(1 + nonce.size + sealed.size).also { out ->
            out[0] = VERSION
            nonce.copyInto(out, 1)
            sealed.copyInto(out, 1 + nonce.size)
        }
    }

    /**
     * @throws GeneralSecurityException if the envelope is malformed, the key is
     * wrong, or the ciphertext or its associated data has been tampered with.
     */
    fun open(key: ByteArray, envelope: ByteArray, associatedData: ByteArray): ByteArray {
        require(key.size == KEY_LENGTH_BYTES) { "record key must be 32 bytes" }
        if (envelope.size <= 1 + NONCE_LENGTH_BYTES) {
            throw GeneralSecurityException("Enveloppe chiffree trop courte")
        }
        if (envelope[0] != VERSION) {
            throw GeneralSecurityException("Version d'enveloppe inconnue: ${envelope[0]}")
        }

        val nonce = envelope.copyOfRange(1, 1 + NONCE_LENGTH_BYTES)
        val sealed = envelope.copyOfRange(1 + NONCE_LENGTH_BYTES, envelope.size)

        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, AES),
            GCMParameterSpec(TAG_LENGTH_BITS, nonce)
        )
        cipher.updateAAD(associatedData)
        return cipher.doFinal(sealed)
    }
}
