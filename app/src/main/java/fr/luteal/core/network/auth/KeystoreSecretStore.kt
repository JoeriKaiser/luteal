package fr.luteal.core.network.auth

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small key-value store whose values are encrypted with a hardware-backed key.
 *
 * Replaces `EncryptedSharedPreferences` from `androidx.security:security-crypto`,
 * which Google deprecated in April 2025 at 1.1.0-alpha07. Beyond being an
 * abandoned alpha, it pulled in Google Tink: 1416 classes, roughly a fifth of
 * this app, to protect a handful of short strings. It also had a history of
 * keyset-corruption crashes on some OEM devices.
 *
 * This does the same job with the platform's own primitives:
 *
 *  - a 256-bit AES-GCM key generated in `AndroidKeyStore`, non-exportable and
 *    backed by the secure element or TEE where the device has one,
 *  - values sealed as `0x01 || iv(12) || ciphertext || tag(16)`, base64'd into
 *    ordinary `SharedPreferences`,
 *  - keys (the map keys) left in the clear. They are fixed field names such as
 *    "account_code"; only the values are secret.
 *
 * The envelope layout deliberately matches
 * [fr.luteal.core.network.crypto.RecordCrypto] so there is one format to reason
 * about, but the key here never leaves the Keystore, so it cannot be passed to
 * that class.
 *
 * ## Failure behaviour
 *
 * If the Keystore key is missing or unusable - a restore to a new device, a
 * factory reset of the secure element, or a user clearing credentials - stored
 * values become permanently unreadable. [get] returns null rather than
 * throwing, so callers treat it as "not stored yet". For credentials that means
 * re-registering, which is the correct outcome: the ciphertext is genuinely
 * gone and no recovery is possible.
 */
class KeystoreSecretStore(
    context: Context,
    fileName: String,
    private val keyAlias: String
) {
    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val IV_LENGTH_BYTES = 12
        const val TAG_LENGTH_BITS = 128
        const val VERSION: Byte = 0x01
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(fileName, Context.MODE_PRIVATE)

    /**
     * Returns the Keystore key, creating it on first use.
     *
     * Synchronised because two threads racing to create the same alias would
     * otherwise leave one of them encrypting under a key the other replaced.
     */
    @Synchronized
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                // No user authentication requirement: sync runs in the
                // background via WorkManager, where no user is present to
                // authenticate. The boundary here is app-sandbox plus
                // Keystore, not user presence.
                .setUserAuthenticationRequired(false)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    fun get(key: String): String? {
        val encoded = prefs.getString(key, null) ?: return null
        return try {
            val envelope = Base64.getDecoder().decode(encoded)
            if (envelope.size <= 1 + IV_LENGTH_BYTES || envelope[0] != VERSION) {
                return null
            }
            val iv = envelope.copyOfRange(1, 1 + IV_LENGTH_BYTES)
            val sealed = envelope.copyOfRange(1 + IV_LENGTH_BYTES, envelope.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(TAG_LENGTH_BITS, iv)
            )
            String(cipher.doFinal(sealed))
        } catch (e: GeneralSecurityException) {
            // Key gone or value tampered with: unreadable, not recoverable.
            null
        } catch (e: IllegalArgumentException) {
            null // malformed base64
        }
    }

    fun put(key: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val sealed = cipher.doFinal(value.toByteArray())
        val iv = cipher.iv
        require(iv.size == IV_LENGTH_BYTES) { "unexpected GCM IV length ${iv.size}" }

        val envelope = ByteArray(1 + iv.size + sealed.size).also { out ->
            out[0] = VERSION
            iv.copyInto(out, 1)
            sealed.copyInto(out, 1 + iv.size)
        }
        prefs.edit()
            .putString(key, Base64.getEncoder().encodeToString(envelope))
            .apply()
    }

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    fun keys(): Set<String> = prefs.all.keys

    fun clear() {
        prefs.edit().clear().apply()
        runCatching {
            val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(keyAlias)) {
                keyStore.deleteEntry(keyAlias)
            }
        }
    }
}
