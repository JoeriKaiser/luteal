package fr.luteal.core.network.crypto

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.luteal.core.network.auth.KeystoreSecretStore
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
    @ApplicationContext context: Context
) {
    private companion object {
        const val FILE_NAME = "luteal_duo_keys_v2"
        const val KEY_ALIAS = "luteal_duo_keys_key"
    }

    private val store = KeystoreSecretStore(
        context = context,
        fileName = FILE_NAME,
        keyAlias = KEY_ALIAS
    )

    fun save(linkId: String, linkKey: ByteArray) {
        store.put(linkId, DuoCrypto.encodeKey(linkKey))
    }

    fun load(linkId: String): ByteArray? =
        store.get(linkId)?.let { runCatching { DuoCrypto.decodeKey(it) }.getOrNull() }

    fun remove(linkId: String) {
        store.remove(linkId)
    }

    fun clear() {
        store.clear()
    }
}
