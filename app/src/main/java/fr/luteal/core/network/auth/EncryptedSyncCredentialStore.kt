package fr.luteal.core.network.auth

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SyncCredentialStore] backed by [KeystoreSecretStore].
 *
 * The account code, auth version, device token, and any in-flight upgrade code
 * are encrypted at rest under a non-exportable AndroidKeyStore key and are
 * never logged. This is the only place credentials are persisted.
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
        const val KEY_AUTH_VERSION = "auth_version"
        const val KEY_PENDING_UPGRADE_CODE = "pending_upgrade_code"
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
        val storedVersion = store.get(KEY_AUTH_VERSION)?.toIntOrNull()
        val authVersion = if (storedVersion != null) {
            AuthVersion.fromInt(storedVersion)
        } else {
            AuthVersion.fromCode(accountCode)
        }
        return SyncCredentials(
            accountId = accountId,
            accountCode = accountCode,
            deviceToken = deviceToken,
            authVersion = authVersion
        )
    }

    override fun save(credentials: SyncCredentials) {
        store.put(KEY_ACCOUNT_ID, credentials.accountId)
        store.put(KEY_ACCOUNT_CODE, credentials.accountCode)
        store.put(KEY_DEVICE_TOKEN, credentials.deviceToken)
        store.put(KEY_AUTH_VERSION, credentials.authVersion.code.toString())
    }

    override fun clear() {
        store.clear()
    }

    override fun clearTracked(): Boolean = store.clearCommitted()

    override fun savePendingUpgradeCode(code: String) {
        store.put(KEY_PENDING_UPGRADE_CODE, code)
    }

    override fun loadPendingUpgradeCode(): String? = store.get(KEY_PENDING_UPGRADE_CODE)

    override fun clearPendingUpgradeCode() {
        store.remove(KEY_PENDING_UPGRADE_CODE)
    }
}
