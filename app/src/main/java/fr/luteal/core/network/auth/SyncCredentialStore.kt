package fr.luteal.core.network.auth

/**
 * Anonymous account credentials for the folicular backend.
 *
 * [accountCode] (format `LTL-XXXXX-XXXXX-XXXXX-XXXXX`) is shown to the user
 * ONCE at registration and is the only recovery credential; the server stores
 * just its hash. [deviceToken] (`ltok_...`) is the bearer credential for every
 * authenticated call. Both are secrets: they must never be written to Room, to
 * plaintext preferences, or to logs.
 */
data class SyncCredentials(
    val accountId: String,
    val accountCode: String,
    val deviceToken: String
)

/**
 * Storage for [SyncCredentials]. Implementations must be Keystore-backed and
 * must never log the code or token. First run has no credentials (the sync
 * engine must register); returning runs reuse the stored device token.
 */
interface SyncCredentialStore {
    fun load(): SyncCredentials?
    fun save(credentials: SyncCredentials)
    fun clear()
}
