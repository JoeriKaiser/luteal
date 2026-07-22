package fr.luteal.core.network.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SyncCredentialStore] backed by [EncryptedSharedPreferences] (Android
 * Keystore). The account code and device token are encrypted at rest and are
 * never logged. This is the only place credentials are persisted.
 */
@Singleton
class EncryptedSyncCredentialStore @Inject constructor(
    @ApplicationContext private val context: Context
) : SyncCredentialStore {

    private companion object {
        const val FILE_NAME = "luteal_sync_credentials"
        const val KEY_ACCOUNT_ID = "account_id"
        const val KEY_ACCOUNT_CODE = "account_code"
        const val KEY_DEVICE_TOKEN = "device_token"
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

    override fun load(): SyncCredentials? {
        val accountId = prefs.getString(KEY_ACCOUNT_ID, null) ?: return null
        val accountCode = prefs.getString(KEY_ACCOUNT_CODE, null) ?: return null
        val deviceToken = prefs.getString(KEY_DEVICE_TOKEN, null) ?: return null
        return SyncCredentials(accountId = accountId, accountCode = accountCode, deviceToken = deviceToken)
    }

    override fun save(credentials: SyncCredentials) {
        prefs.edit()
            .putString(KEY_ACCOUNT_ID, credentials.accountId)
            .putString(KEY_ACCOUNT_CODE, credentials.accountCode)
            .putString(KEY_DEVICE_TOKEN, credentials.deviceToken)
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }
}
