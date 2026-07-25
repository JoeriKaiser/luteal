package fr.luteal.core.network.crypto

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keystore-backed storage for Duo link keys, one per link.
 *
 * These keys never reach folicular: the tracker generates one and shares it in
 * the pairing URL fragment (see [DuoCrypto]). Losing them means the Duo has to
 * be re-paired, which is the correct failure mode - the server cannot help
 * recover a key it never held.
 */
@Singleton
class DuoKeyStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        const val FILE_NAME = "luteal_duo_keys"
    }

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun save(linkId: String, linkKey: ByteArray) {
        prefs.edit().putString(linkId, DuoCrypto.encodeKey(linkKey)).apply()
    }

    fun load(linkId: String): ByteArray? =
        prefs.getString(linkId, null)?.let {
            runCatching { DuoCrypto.decodeKey(it) }.getOrNull()
        }

    fun remove(linkId: String) {
        prefs.edit().remove(linkId).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
