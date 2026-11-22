package fr.luteal.core.network.auth

import fr.luteal.core.network.crypto.AccountCodeV2

/**
 * Sync authentication and encryption protocol version.
 *
 * - [V1]: Legacy server-minted `LTL-...` code, `0x01` sealed envelopes.
 * - [V2]: Client-generated `LTL2-...` recovery code, decoupled `K_auth` / `K_master`
 *   HKDF hierarchy, server-hashed auth verifier, and `0x02` sealed envelopes with
 *   per-record `schema_version`.
 */
enum class AuthVersion(val code: Int) {
    V1(1),
    V2(2);

    companion object {
        fun fromInt(value: Int): AuthVersion = when (value) {
            2 -> V2
            else -> V1
        }

        fun fromCode(accountCode: String): AuthVersion = AccountCodeV2.detectVersion(accountCode)
    }
}

/**
 * Pseudonymous account credentials for the folicular backend.
 *
 * [accountCode] is the only recovery credential (either legacy `LTL-...` or
 * operator-blind `LTL2-...`). [deviceToken] (`ltok_...`) is the bearer
 * credential for every authenticated call. Both are secrets: they must never
 * be written to Room, to plaintext preferences, or to logs.
 */
data class SyncCredentials(
    val accountId: String,
    val accountCode: String,
    val deviceToken: String,
    val authVersion: AuthVersion = AuthVersion.fromCode(accountCode)
)

/**
 * Storage for [SyncCredentials]. Implementations must be Keystore-backed and
 * must never log the code or token.
 */
interface SyncCredentialStore {
    fun load(): SyncCredentials?
    fun save(credentials: SyncCredentials)
    fun clear()

    fun clearTracked(): Boolean {
        clear()
        return true
    }

    /**
     * Write-ahead persistence for an in-flight v1 -> v2 upgrade recovery code.
     * Stored encrypted before calling `POST /v1/auth/upgrade` so a crash or
     * network drop right after server commit can reconcile cleanly against
     * `GET /v1/me` without stranding the user.
     */
    fun savePendingUpgradeCode(code: String) {}
    fun loadPendingUpgradeCode(): String? = null
    fun clearPendingUpgradeCode() {}
}
