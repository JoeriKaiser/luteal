package fr.luteal.core.data.security

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.luteal.core.network.auth.KeystoreSecretStore
import javax.inject.Inject
import javax.inject.Singleton

interface PinSecretStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun clear()
}

@Singleton
class KeystorePinSecretStore @Inject constructor(
    @ApplicationContext context: Context
) : PinSecretStore {
    private val store = KeystoreSecretStore(
        context = context,
        fileName = "app_lock_secrets",
        keyAlias = "luteal_app_lock_key"
    )

    override fun get(key: String): String? = store.get(key)
    override fun put(key: String, value: String) = store.put(key, value)
    override fun clear() = store.clear()
}

class InMemoryPinSecretStore : PinSecretStore {
    private val map = mutableMapOf<String, String>()
    override fun get(key: String): String? = map[key]
    override fun put(key: String, value: String) { map[key] = value }
    override fun clear() { map.clear() }
}
