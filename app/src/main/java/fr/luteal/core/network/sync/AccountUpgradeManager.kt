package fr.luteal.core.network.sync

import fr.luteal.core.network.AccountUpgradeRequestWire
import fr.luteal.core.network.FolicularApiException
import fr.luteal.core.network.UpgradeRecordInputWire
import fr.luteal.core.network.auth.AuthVersion
import fr.luteal.core.network.auth.SyncCredentialStore
import fr.luteal.core.network.auth.SyncCredentials
import fr.luteal.core.network.crypto.AccountCodeV2
import fr.luteal.core.network.crypto.RecordCrypto
import fr.luteal.core.network.crypto.RecordSealer
import java.io.IOException
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.withLock

/** Result of pre-flight preparation before showing the new v2 code to the user. */
data class PreparedAccountUpgrade(
    val accountId: String,
    val newRecoveryCode: String,
    val otherActiveDeviceCount: Int
)

/** Outcome of executing the atomic `v1 -> v2` upgrade. */
sealed interface AccountUpgradeOutcome {
    data class Success(
        val recordsMigrated: Int,
        val revokedDevices: Long
    ) : AccountUpgradeOutcome

    data object AlreadyUpgraded : AccountUpgradeOutcome

    /**
     * Another device pushed a change between export and upgrade (HTTP 409).
     * Safe to retry immediately after re-syncing.
     */
    data object ConcurrentChangeConflict : AccountUpgradeOutcome
}

/**
 * Orchestrates the crash-safe, atomic `v1 -> v2` in-place account upgrade.
 *
 * All operations acquire [SyncLock.mutex] so background sync can never overlap
 * with export, re-sealing, or credential promotion.
 */
@Singleton
class AccountUpgradeManager @Inject constructor(
    private val syncEngine: CycleSyncEngine,
    private val credentialStore: SyncCredentialStore,
    private val apiClientFactory: FolicularApiClientFactory,
    private val cursorStore: SyncCursorStore,
    private val recordSealer: RecordSealer,
    private val syncLock: SyncLock
) {

    /**
     * Reconciles any write-ahead `pending_upgrade_code` left behind by a crash
     * or network drop during `POST /v1/auth/upgrade`.
     *
     * Returns `true` if a pending upgrade was promoted to active v2 credentials.
     */
    suspend fun reconcilePendingUpgradeIfNeeded(): Boolean = syncLock.mutex.withLock {
        reconcilePendingUpgradeUnlocked()
    }

    internal suspend fun reconcilePendingUpgradeUnlocked(): Boolean {
        val pendingCode = credentialStore.loadPendingUpgradeCode() ?: return false
        val creds = credentialStore.load()
        if (creds == null) {
            credentialStore.clearPendingUpgradeCode()
            return false
        }
        if (creds.authVersion == AuthVersion.V2) {
            credentialStore.clearPendingUpgradeCode()
            return false
        }

        val client = apiClientFactory.create(cursorStore.getBaseUrl())
        val me = client.getMe(creds.deviceToken)
        return if (me.authVersion == AuthVersion.V2) {
            credentialStore.save(
                creds.copy(
                    accountCode = pendingCode,
                    authVersion = AuthVersion.V2
                )
            )
            credentialStore.clearPendingUpgradeCode()
            recordSealer.invalidate()
            // Reset cursor to 0 so the next sync cleanly converges against the
            // freshly rewritten v2 change log.
            cursorStore.setCursor(0L)
            true
        } else {
            credentialStore.clearPendingUpgradeCode()
            false
        }
    }

    /**
     * Runs a pre-flight v1 sync, counts other active devices so the UI can warn
     * the user, and generates a fresh 128-bit `LTL2-...` recovery code locally.
     * Nothing is sent to the server until [executeUpgrade] is called.
     */
    suspend fun prepareUpgrade(): PreparedAccountUpgrade = syncLock.mutex.withLock {
        reconcilePendingUpgradeUnlocked()
        syncEngine.syncUnlocked()

        val creds = credentialStore.load()
            ?: throw IllegalStateException("No linked sync account to upgrade")
        check(creds.authVersion == AuthVersion.V1) {
            "Account is already using v2 encryption"
        }

        val client = apiClientFactory.create(cursorStore.getBaseUrl())
        val devices = client.listDevices(creds.deviceToken)
        val otherActive = devices.count { !it.revoked && !it.current }
        val newCode = AccountCodeV2.generate()

        PreparedAccountUpgrade(
            accountId = creds.accountId,
            newRecoveryCode = newCode,
            otherActiveDeviceCount = otherActive
        )
    }

    /**
     * Executes the atomic `v1 -> v2` upgrade using the user-confirmed
     * [newRecoveryCode].
     *
     * Crash safety:
     * 1. Persists `pending_upgrade_code` in Keystore BEFORE sending HTTP request.
     * 2. Server executes `POST /v1/auth/upgrade` in one SQLite transaction with
     *    optimistic `expected_cursor` and idempotent retry support.
     * 3. On `IOException` (dropped response after possible commit), immediately
     *    attempts reconciliation against `GET /v1/me` and preserves
     *    `pending_upgrade_code` if the network is still down.
     */
    suspend fun executeUpgrade(
        newRecoveryCode: String,
        revokeOtherDevices: Boolean = true
    ): AccountUpgradeOutcome = syncLock.mutex.withLock {
        val normalizedCode = AccountCodeV2.normalizeDisplay(newRecoveryCode)

        if (reconcilePendingUpgradeUnlocked()) {
            return@withLock AccountUpgradeOutcome.AlreadyUpgraded
        }

        val syncReport = syncEngine.syncUnlocked()
        val v1Creds = credentialStore.load()
            ?: throw IllegalStateException("No linked sync account to upgrade")
        if (v1Creds.authVersion == AuthVersion.V2) {
            return@withLock AccountUpgradeOutcome.AlreadyUpgraded
        }

        val client = apiClientFactory.create(cursorStore.getBaseUrl())
        val export = client.getExport(v1Creds.deviceToken)
        if (export.authVersion == AuthVersion.V2) {
            return@withLock AccountUpgradeOutcome.AlreadyUpgraded
        }

        val resealedRecords = export.records.map { rec ->
            if (rec.deleted) {
                UpgradeRecordInputWire(
                    entityType = rec.entityType,
                    entityId = rec.entityId,
                    clientRev = rec.clientRev,
                    updatedAt = rec.updatedAt,
                    deleted = true,
                    ciphertext = null
                )
            } else {
                val rawCiphertext = rec.ciphertext
                    ?: throw IllegalStateException("Live exported record ${rec.entityId} missing ciphertext")
                val plaintextJson = recordSealer.openWithCredentials(
                    credentials = v1Creds,
                    entityType = rec.entityType,
                    entityId = rec.entityId,
                    clientRev = rec.clientRev,
                    ciphertext = rawCiphertext
                )
                val v2Ciphertext = recordSealer.sealV2WithCode(
                    accountId = v1Creds.accountId,
                    accountCodeV2 = normalizedCode,
                    entityType = rec.entityType,
                    entityId = rec.entityId,
                    clientRev = rec.clientRev,
                    payload = plaintextJson
                )
                UpgradeRecordInputWire(
                    entityType = rec.entityType,
                    entityId = rec.entityId,
                    clientRev = rec.clientRev,
                    updatedAt = rec.updatedAt,
                    deleted = false,
                    ciphertext = v2Ciphertext
                )
            }
        }

        val resealedSettings = export.settingsCiphertext?.let { v1SettingsB64 ->
            resealV1SettingsBlob(v1Creds, normalizedCode, v1SettingsB64)
        }

        val authKeyHex = RecordCrypto.deriveV2AuthKeyHex(normalizedCode)
        val expectedCursor = maxOf(syncReport.cursor, cursorStore.getCursor())
        val upgradeReq = AccountUpgradeRequestWire(
            authKey = authKeyHex,
            expectedCursor = expectedCursor,
            revokeOtherDevices = revokeOtherDevices,
            settings = resealedSettings,
            records = resealedRecords
        )

        // Write-ahead to Keystore before touching the server.
        credentialStore.savePendingUpgradeCode(normalizedCode)

        val result = try {
            client.upgradeAccount(v1Creds.deviceToken, upgradeReq)
        } catch (e: FolicularApiException) {
            credentialStore.clearPendingUpgradeCode()
            if (e.status == 409) {
                return@withLock AccountUpgradeOutcome.ConcurrentChangeConflict
            }
            if (e.status == 401) {
                throw SyncAuthException()
            }
            throw e
        } catch (io: IOException) {
            // Connection dropped mid-flight: server may already have committed.
            // Try immediate reconciliation; if still offline, leave pending code
            // in Keystore for the next app launch or sync attempt.
            val promoted = runCatching { reconcilePendingUpgradeUnlocked() }.getOrDefault(false)
            if (promoted) {
                return@withLock AccountUpgradeOutcome.Success(
                    recordsMigrated = resealedRecords.size,
                    revokedDevices = 0L
                )
            }
            throw io
        } catch (t: Throwable) {
            credentialStore.clearPendingUpgradeCode()
            throw t
        }

        credentialStore.save(
            SyncCredentials(
                accountId = v1Creds.accountId,
                accountCode = normalizedCode,
                deviceToken = v1Creds.deviceToken,
                authVersion = AuthVersion.V2
            )
        )
        credentialStore.clearPendingUpgradeCode()
        recordSealer.invalidate()
        cursorStore.setCursor(result.cursor)

        AccountUpgradeOutcome.Success(
            recordsMigrated = result.recordsMigrated,
            revokedDevices = result.revokedDevices
        )
    }

    private fun resealV1SettingsBlob(
        v1Creds: SyncCredentials,
        newCodeV2: String,
        v1SettingsBase64: String
    ): String {
        val raw = Base64.getDecoder().decode(v1SettingsBase64)
        val master = RecordCrypto.deriveMasterKey(v1Creds.accountCode, v1Creds.accountId)
        val v1Key = try {
            RecordCrypto.deriveRecordKey(master)
        } finally {
            master.fill(0)
        }
        val plaintext = try {
            RecordCrypto.open(
                key = v1Key,
                envelope = raw,
                associatedData = "account_settings\u0000${v1Creds.accountId}".toByteArray(Charsets.UTF_8)
            )
        } finally {
            v1Key.fill(0)
        }
        return recordSealer.sealV2SettingsBytes(v1Creds.accountId, newCodeV2, plaintext)
    }
}
