package fr.luteal.core.data.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PinCryptoManager @Inject constructor(
    private val secretStore: PinSecretStore
) {
    private val secureRandom = SecureRandom()

    companion object {
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_LENGTH = "pin_length"
        private const val PBKDF2_ITERATIONS = 100_000
        private const val KEY_LENGTH_BITS = 256
        private const val SALT_LENGTH_BYTES = 16
    }

    /**
     * Every operation below runs on [Dispatchers.Default]: PBKDF2 (100k
     * iterations) is a deliberate ~100ms CPU burn and each KeystoreSecretStore
     * get/put is an AndroidKeystore round trip. None of it may run on the
     * caller's (usually main) dispatcher.
     */
    suspend fun hasPinConfigured(): Boolean = withContext(Dispatchers.Default) {
        val salt = secretStore.get(KEY_PIN_SALT)
        val hash = secretStore.get(KEY_PIN_HASH)
        !salt.isNullOrBlank() && !hash.isNullOrBlank()
    }

    suspend fun pinLength(): Int? = withContext(Dispatchers.Default) {
        secretStore.get(KEY_PIN_LENGTH)?.toIntOrNull()?.takeIf { it in 4..8 }
    }

    suspend fun setPin(pin: String) = withContext(Dispatchers.Default) {
        val saltBytes = ByteArray(SALT_LENGTH_BYTES)
        secureRandom.nextBytes(saltBytes)

        val hashBytes = derivePbkdf2Hash(pin.toCharArray(), saltBytes)

        val saltB64 = Base64.getEncoder().encodeToString(saltBytes)
        val hashB64 = Base64.getEncoder().encodeToString(hashBytes)

        secretStore.put(KEY_PIN_SALT, saltB64)
        secretStore.put(KEY_PIN_HASH, hashB64)
        secretStore.put(KEY_PIN_LENGTH, pin.length.toString())
    }

    suspend fun verifyPin(pin: String): Boolean = withContext(Dispatchers.Default) {
        val saltB64 = secretStore.get(KEY_PIN_SALT) ?: return@withContext false
        val storedHashB64 = secretStore.get(KEY_PIN_HASH) ?: return@withContext false

        val saltBytes = runCatching { Base64.getDecoder().decode(saltB64) }.getOrNull()
            ?: return@withContext false
        val storedHashBytes = runCatching { Base64.getDecoder().decode(storedHashB64) }.getOrNull()
            ?: return@withContext false

        val derivedHashBytes = derivePbkdf2Hash(pin.toCharArray(), saltBytes)

        MessageDigest.isEqual(derivedHashBytes, storedHashBytes)
    }

    suspend fun clearPin() = withContext(Dispatchers.Default) {
        secretStore.clear()
    }


    private fun derivePbkdf2Hash(chars: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(chars, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
