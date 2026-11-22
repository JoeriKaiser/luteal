package fr.luteal.core.network.crypto

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end encryption of synchronized record content.
 *
 * Supports both legacy `v1` (`0x01`) and operator-blind `v2` (`0x02`) formats.
 *
 * ## v1 Key Hierarchy (legacy, server-minted `LTL-...` code)
 * ```
 * account code (100 bits)
 *   +-- SHA-256(normalized_code) -> auth verifier stored server-side
 *   +-- HKDF(code, salt=account_id, info="luteal/v1/master") -> master key
 *         +-- HKDF-Expand(info="luteal/v1/record") -> record content key
 *         +-- HKDF-Expand(info="luteal/v1/duo")    -> Duo root key material
 * ```
 *
 * ## v2 Key Hierarchy (operator-blind, device-generated `LTL2-...` code)
 * ```
 * root_secret (16 random bytes, never leaves device)
 *   +-- HKDF-Extract(salt="luteal/v2/salt", IKM=root_secret) -> PRK_v2
 *         +-- HKDF-Expand("luteal/v2/auth")   -> K_auth (sent as auth_key; server stores SHA-256(domain || K_auth))
 *         +-- HKDF-Expand("luteal/v2/master") -> K_master (never leaves device)
 *               +-- HKDF-Expand("luteal/v2/record\0" + account_id)   -> K_record
 *               +-- HKDF-Expand("luteal/v2/settings\0" + account_id) -> K_settings
 *               +-- HKDF-Expand("luteal/v2/duo\0" + account_id)      -> K_duo_root
 * ```
 *
 * ## Record Sealing
 * AES-256-GCM with a fresh random 96-bit nonce per record.
 * - v1 wire format: `0x01 || nonce(12) || ciphertext || tag(16)`
 *   AAD: `"$entityType\u0000$entityId\u0000$clientRev"`
 * - v2 wire format: `0x02 || nonce(12) || ciphertext || tag(16)`
 *   AAD: `"v2\u0000$accountId\u0000$entityType\u0000$entityId\u0000$clientRev"`
 */
object RecordCrypto {

    private const val AES_GCM = "AES/GCM/NoPadding"
    private const val AES = "AES"
    private const val KEY_LENGTH_BYTES = 32
    private const val NONCE_LENGTH_BYTES = 12
    private const val TAG_LENGTH_BITS = 128

    /** Legacy v1 envelope version byte. */
    const val VERSION: Byte = 0x01

    /** Operator-blind v2 envelope version byte. */
    const val VERSION_V2: Byte = 0x02

    private val MASTER_INFO = "luteal/v1/master".toByteArray(Charsets.UTF_8)
    private val RECORD_INFO = "luteal/v1/record".toByteArray(Charsets.UTF_8)
    private val DUO_INFO = "luteal/v1/duo".toByteArray(Charsets.UTF_8)

    private val V2_SALT = "luteal/v2/salt".toByteArray(Charsets.UTF_8)
    private val V2_AUTH_INFO = "luteal/v2/auth".toByteArray(Charsets.UTF_8)
    private val V2_MASTER_INFO = "luteal/v2/master".toByteArray(Charsets.UTF_8)

    // --- v1 (legacy) -------------------------------------------------------

    /**
     * Canonical form of a legacy v1 account code, matching the server's
     * `auth.NormalizeCode`: prefix and separators stripped, uppercased.
     */
    fun normalizeAccountCode(input: String): String =
        input.trim()
            .uppercase()
            .removePrefix("LTL-")
            .replace("-", "")
            .replace(" ", "")

    fun deriveMasterKey(accountCode: String, accountId: String): ByteArray =
        Hkdf.derive(
            inputKeyMaterial = normalizeAccountCode(accountCode).toByteArray(Charsets.UTF_8),
            salt = accountId.toByteArray(Charsets.UTF_8),
            info = MASTER_INFO,
            length = KEY_LENGTH_BYTES
        )

    fun deriveRecordKey(masterKey: ByteArray): ByteArray =
        Hkdf.expand(masterKey, RECORD_INFO, KEY_LENGTH_BYTES)

    fun deriveDuoRootKey(masterKey: ByteArray): ByteArray =
        Hkdf.expand(masterKey, DUO_INFO, KEY_LENGTH_BYTES)

    fun associatedData(entityType: String, entityId: String, clientRev: String): ByteArray =
        "$entityType\u0000$entityId\u0000$clientRev".toByteArray(Charsets.UTF_8)

    fun seal(
        key: ByteArray,
        plaintext: ByteArray,
        associatedData: ByteArray,
        random: SecureRandom = SecureRandom()
    ): ByteArray = sealVersioned(VERSION, key, plaintext, associatedData, random)

    fun open(key: ByteArray, envelope: ByteArray, associatedData: ByteArray): ByteArray =
        openVersioned(VERSION, key, envelope, associatedData)

    // --- v2 (operator-blind) -----------------------------------------------

    fun deriveV2Prk(rootSecret: ByteArray): ByteArray {
        require(rootSecret.size == AccountCodeV2.ROOT_SECRET_BYTES) {
            "v2 root secret must be ${AccountCodeV2.ROOT_SECRET_BYTES} bytes"
        }
        return Hkdf.extract(V2_SALT, rootSecret)
    }

    /** Derives the 32-byte authentication key (`K_auth`) sent to `folicular` as `auth_key`. */
    fun deriveV2AuthKey(rootSecret: ByteArray): ByteArray {
        val prk = deriveV2Prk(rootSecret)
        return try {
            Hkdf.expand(prk, V2_AUTH_INFO, KEY_LENGTH_BYTES)
        } finally {
            prk.fill(0)
        }
    }

    /** Parses [accountCodeV2], derives `K_auth`, and returns it as lowercase 64-char hex. */
    fun deriveV2AuthKeyHex(accountCodeV2: String): String {
        val secret = AccountCodeV2.parseAndValidate(accountCodeV2)
        val authKey = try {
            deriveV2AuthKey(secret)
        } finally {
            secret.fill(0)
        }
        return try {
            authKey.toLowerHex()
        } finally {
            authKey.fill(0)
        }
    }

    fun deriveV2MasterKey(rootSecret: ByteArray): ByteArray {
        val prk = deriveV2Prk(rootSecret)
        return try {
            Hkdf.expand(prk, V2_MASTER_INFO, KEY_LENGTH_BYTES)
        } finally {
            prk.fill(0)
        }
    }

    /** Derives the 32-byte record encryption key bound to [accountId]. */
    fun deriveV2RecordKey(accountCodeV2: String, accountId: String): ByteArray =
        deriveV2ScopedKey(accountCodeV2, "luteal/v2/record\u0000$accountId")

    /** Derives the 32-byte settings encryption key bound to [accountId]. */
    fun deriveV2SettingsKey(accountCodeV2: String, accountId: String): ByteArray =
        deriveV2ScopedKey(accountCodeV2, "luteal/v2/settings\u0000$accountId")

    /** Derives the 32-byte Duo root key bound to [accountId]. */
    fun deriveV2DuoRootKey(accountCodeV2: String, accountId: String): ByteArray =
        deriveV2ScopedKey(accountCodeV2, "luteal/v2/duo\u0000$accountId")

    private fun deriveV2ScopedKey(accountCodeV2: String, info: String): ByteArray {
        val secret = AccountCodeV2.parseAndValidate(accountCodeV2)
        val master = try {
            deriveV2MasterKey(secret)
        } finally {
            secret.fill(0)
        }
        return try {
            Hkdf.expand(master, info.toByteArray(Charsets.UTF_8), KEY_LENGTH_BYTES)
        } finally {
            master.fill(0)
        }
    }

    /** Associated data for v2 sync records, binding version, account, and routing metadata. */
    fun associatedDataV2(
        accountId: String,
        entityType: String,
        entityId: String,
        clientRev: String
    ): ByteArray =
        "v2\u0000$accountId\u0000$entityType\u0000$entityId\u0000$clientRev".toByteArray(Charsets.UTF_8)

    /** Associated data for v2 sealed account settings. */
    fun associatedDataV2Settings(accountId: String): ByteArray =
        "v2\u0000$accountId\u0000account_settings".toByteArray(Charsets.UTF_8)

    fun sealV2(
        key: ByteArray,
        plaintext: ByteArray,
        associatedData: ByteArray,
        random: SecureRandom = SecureRandom()
    ): ByteArray = sealVersioned(VERSION_V2, key, plaintext, associatedData, random)

    fun openV2(key: ByteArray, envelope: ByteArray, associatedData: ByteArray): ByteArray =
        openVersioned(VERSION_V2, key, envelope, associatedData)

    // --- Shared AES-256-GCM framing ----------------------------------------

    private fun sealVersioned(
        version: Byte,
        key: ByteArray,
        plaintext: ByteArray,
        associatedData: ByteArray,
        random: SecureRandom
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
            out[0] = version
            nonce.copyInto(out, 1)
            sealed.copyInto(out, 1 + nonce.size)
        }
    }

    private fun openVersioned(
        expectedVersion: Byte,
        key: ByteArray,
        envelope: ByteArray,
        associatedData: ByteArray
    ): ByteArray {
        require(key.size == KEY_LENGTH_BYTES) { "record key must be 32 bytes" }
        if (envelope.size <= 1 + NONCE_LENGTH_BYTES) {
            throw GeneralSecurityException("Enveloppe chiffree trop courte")
        }
        if (envelope[0] != expectedVersion) {
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

    private fun ByteArray.toLowerHex(): String {
        val hex = "0123456789abcdef"
        val out = CharArray(size * 2)
        for (i in indices) {
            val v = this[i].toInt() and 0xFF
            out[i * 2] = hex[v ushr 4]
            out[i * 2 + 1] = hex[v and 0x0F]
        }
        return String(out)
    }
}
