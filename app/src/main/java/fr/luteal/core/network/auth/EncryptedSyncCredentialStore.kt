package fr.luteal.core.network.auth

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SyncCredentialStore] backed by [KeystoreSecretStore].
 *
 * The account code and device token are encrypted at rest under a
 * non-exportable AndroidKeyStore key and are never logged. This is the only
 * place credentials are persisted.
 *
 * The account code is the root of the encryption key hierarchy (see
 * docs/architecture/E2EE_DESIGN.md), so losing this store means losing the
 * ability to decrypt anything the server holds. That is by design: the server
 * has no key that could substitute for it.
 */
@Singleton
class EncryptedSyncCredentialStore @Inject constructor(
    @ApplicationContext context: Context
) : SyncCredentialStore {

    private companion object {
        const val FILE_NAME = "luteal_sync_credentials_v2"
        const val KEY_ALIAS = "luteal_sync_credentials_key"
        const val KEY_ACCOUNT_ID = "account_id"
        const val KEY_ACCOUNT_CODE = "account_code"
        const val KEY_DEVICE_TOKEN = "device_token"
    }

    private val store = KeystoreSecretStore(
        context = context,
        fileName = FILE_NAME,
        keyAlias = KEY_ALIAS
    )

    override fun load(): SyncCredentials? {
        val accountId = store.get(KEY_ACCOUNT_ID) ?: return null
        val accountCode = store.get(KEY_ACCOUNT_CODE) ?: return null
        val deviceToken = store.get(KEY_DEVICE_TOKEN) ?: return null
        return SyncCredentials(
            accountId = accountId,
            accountCode = accountCode,
            deviceToken = deviceToken
        )
    }

    override fun save(credentials: SyncCredentials) {
        store.put(KEY_ACCOUNT_ID, credentials.accountId)
        store.put(KEY_ACCOUNT_CODE, credentials.accountCode)
        store.put(KEY_DEVICE_TOKEN, credentials.deviceToken)
    }

    override fun clear() {
        store.clear()
    }
}
