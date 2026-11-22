package fr.luteal.core.network.crypto

import fr.luteal.core.network.ContractJson
import fr.luteal.core.network.auth.AuthVersion
import fr.luteal.core.network.auth.SyncCredentialStore
import fr.luteal.core.network.auth.SyncCredentials
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Thrown when a `0x02` sealed payload declares a `schema_version` newer than
 * this version of Luteal supports.
 */
class UnsupportedRecordSchemaException(
    val entityType: String,
    val foundVersion: Int,
    val maxSupportedVersion: Int
) : RuntimeException(
    "Record $entityType uses schema_version=$foundVersion (max supported $maxSupportedVersion)"
)

/**
 * Seals and opens synchronized record payloads for the sync layer.
 *
 * Dual-mode:
 * - [AuthVersion.V1]: seals/opens `0x01` envelopes with raw JSON payloads.
 * - [AuthVersion.V2]: seals/opens `0x02` envelopes with account-bound AAD and
 *   `{"schema_version": 1, "payload": ...}` plaintext framing.
 *
 * The cached record key is keyed by `(accountId, authVersion, codeDigest)` so
 * an in-place v1 -> v2 upgrade (which preserves `accountId`) can never reuse a
 * stale v1 key even if [invalidate] were not called first.
 */
@Singleton
class RecordSealer @Inject constructor(
    private val credentialStore: SyncCredentialStore
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        private const val FIELD_SCHEMA_VERSION = "schema_version"
        private const val FIELD_PAYLOAD = "payload"
    }

    private data class CachedKey(
        val accountId: String,
        val authVersion: AuthVersion,
        val codeDigest: ByteArray,
        val recordKey: ByteArray
    )

    private var cached: CachedKey? = null

    @Synchronized
    private fun activeKey(): Triple<SyncCredentials, AuthVersion, ByteArray> {
        val credentials = credentialStore.load()
            ?: throw IllegalStateException("Aucun compte synchronisé : clé indisponible.")
        val digest = sha256(credentials.accountCode)

        cached?.let { current ->
            if (current.accountId == credentials.accountId &&
                current.authVersion == credentials.authVersion &&
                current.codeDigest.contentEquals(digest)
            ) {
                return Triple(credentials, credentials.authVersion, current.recordKey)
            }
        }

        val key = deriveRecordKeyFor(credentials)
        cached?.recordKey?.fill(0)
        cached = CachedKey(
            accountId = credentials.accountId,
            authVersion = credentials.authVersion,
            codeDigest = digest,
            recordKey = key
        )
        return Triple(credentials, credentials.authVersion, key)
    }

    /** Drops the cached key, e.g. after credentials are cleared or upgraded. */
    @Synchronized
    fun invalidate() {
        cached?.recordKey?.fill(0)
        cached = null
    }

    fun seal(
        entityType: String,
        entityId: String,
        clientRev: String,
        payload: JsonElement
    ): String {
        val (credentials, version, key) = activeKey()
        return sealWithKey(version, credentials.accountId, key, entityType, entityId, clientRev, payload)
    }

    /**
     * @throws GeneralSecurityException when the payload was tampered with, was
     * relocated onto a different record, or was sealed under another key.
     * @throws UnsupportedRecordSchemaException when a v2 record declares a
     * newer `schema_version` than this app version supports.
     */
    fun open(
        entityType: String,
        entityId: String,
        clientRev: String,
        ciphertext: String
    ): JsonElement {
        val (credentials, version, key) = activeKey()
        return openWithKey(version, credentials.accountId, key, entityType, entityId, clientRev, ciphertext)
    }

    /**
     * Opens a record using explicit [credentials] without touching the active
     * cache. Used during `v1 -> v2` account upgrades to decrypt exported `0x01`
     * records before rotating credentials.
     */
    fun openWithCredentials(
        credentials: SyncCredentials,
        entityType: String,
        entityId: String,
        clientRev: String,
        ciphertext: String
    ): JsonElement {
        val key = deriveRecordKeyFor(credentials)
        return try {
            openWithKey(
                version = credentials.authVersion,
                accountId = credentials.accountId,
                key = key,
                entityType = entityType,
                entityId = entityId,
                clientRev = clientRev,
                ciphertext = ciphertext
            )
        } finally {
            key.fill(0)
        }
    }

    /**
     * Seals a record under a candidate v2 recovery code without mutating the
     * active credential store. Used during `v1 -> v2` account upgrades.
     */
    fun sealV2WithCode(
        accountId: String,
        accountCodeV2: String,
        entityType: String,
        entityId: String,
        clientRev: String,
        payload: JsonElement
    ): String {
        val key = RecordCrypto.deriveV2RecordKey(accountCodeV2, accountId)
        return try {
            sealWithKey(
                version = AuthVersion.V2,
                accountId = accountId,
                key = key,
                entityType = entityType,
                entityId = entityId,
                clientRev = clientRev,
                payload = payload
            )
        } finally {
            key.fill(0)
        }
    }

    /**
     * Re-seals an opaque v1 settings blob under a candidate v2 settings key.
     * Note: current Luteal builds do not populate `account_settings`; if a v1
     * blob exists (e.g. from another client), it is decrypted with v1 record
     * key material or forwarded only when decryptable.
     */
    fun sealV2SettingsBytes(
        accountId: String,
        accountCodeV2: String,
        plaintext: ByteArray
    ): String {
        val key = RecordCrypto.deriveV2SettingsKey(accountCodeV2, accountId)
        return try {
            val sealed = RecordCrypto.sealV2(
                key = key,
                plaintext = plaintext,
                associatedData = RecordCrypto.associatedDataV2Settings(accountId)
            )
            Base64.getEncoder().encodeToString(sealed)
        } finally {
            key.fill(0)
        }
    }

    private fun sealWithKey(
        version: AuthVersion,
        accountId: String,
        key: ByteArray,
        entityType: String,
        entityId: String,
        clientRev: String,
        payload: JsonElement
    ): String {
        val sealed = when (version) {
            AuthVersion.V1 -> {
                val plaintext = ContractJson.encodeToString(JsonElement.serializer(), payload)
                RecordCrypto.seal(
                    key = key,
                    plaintext = plaintext.toByteArray(Charsets.UTF_8),
                    associatedData = RecordCrypto.associatedData(entityType, entityId, clientRev)
                )
            }
            AuthVersion.V2 -> {
                val wrapped = buildJsonObject {
                    put(FIELD_SCHEMA_VERSION, JsonPrimitive(CURRENT_SCHEMA_VERSION))
                    put(FIELD_PAYLOAD, payload)
                }
                val plaintext = ContractJson.encodeToString(JsonElement.serializer(), wrapped)
                RecordCrypto.sealV2(
                    key = key,
                    plaintext = plaintext.toByteArray(Charsets.UTF_8),
                    associatedData = RecordCrypto.associatedDataV2(
                        accountId = accountId,
                        entityType = entityType,
                        entityId = entityId,
                        clientRev = clientRev
                    )
                )
            }
        }
        return Base64.getEncoder().encodeToString(sealed)
    }

    private fun openWithKey(
        version: AuthVersion,
        accountId: String,
        key: ByteArray,
        entityType: String,
        entityId: String,
        clientRev: String,
        ciphertext: String
    ): JsonElement {
        val sealed = try {
            Base64.getDecoder().decode(ciphertext)
        } catch (e: IllegalArgumentException) {
            throw GeneralSecurityException("Enveloppe chiffrée illisible", e)
        }
        return when (version) {
            AuthVersion.V1 -> {
                val plaintext = RecordCrypto.open(
                    key = key,
                    envelope = sealed,
                    associatedData = RecordCrypto.associatedData(entityType, entityId, clientRev)
                )
                ContractJson.decodeFromString(JsonElement.serializer(), String(plaintext, Charsets.UTF_8))
            }
            AuthVersion.V2 -> {
                val plaintext = RecordCrypto.openV2(
                    key = key,
                    envelope = sealed,
                    associatedData = RecordCrypto.associatedDataV2(
                        accountId = accountId,
                        entityType = entityType,
                        entityId = entityId,
                        clientRev = clientRev
                    )
                )
                val decoded = ContractJson.decodeFromString(
                    JsonElement.serializer(),
                    String(plaintext, Charsets.UTF_8)
                )
                val obj = decoded as? JsonObject
                    ?: throw GeneralSecurityException("Enveloppe v2 malformée")
                val schemaVersion = obj[FIELD_SCHEMA_VERSION]?.jsonPrimitive?.intOrNull
                    ?: throw GeneralSecurityException("schema_version manquant dans l'enveloppe v2")
                if (schemaVersion > CURRENT_SCHEMA_VERSION) {
                    throw UnsupportedRecordSchemaException(
                        entityType = entityType,
                        foundVersion = schemaVersion,
                        maxSupportedVersion = CURRENT_SCHEMA_VERSION
                    )
                }
                if (schemaVersion < 1) {
                    throw GeneralSecurityException("schema_version invalide: $schemaVersion")
                }
                obj[FIELD_PAYLOAD]
                    ?: throw GeneralSecurityException("payload manquant dans l'enveloppe v2")
            }
        }
    }

    private fun deriveRecordKeyFor(credentials: SyncCredentials): ByteArray =
        when (credentials.authVersion) {
            AuthVersion.V1 -> {
                val master = RecordCrypto.deriveMasterKey(
                    accountCode = credentials.accountCode,
                    accountId = credentials.accountId
                )
                try {
                    RecordCrypto.deriveRecordKey(master)
                } finally {
                    master.fill(0)
                }
            }
            AuthVersion.V2 -> RecordCrypto.deriveV2RecordKey(
                accountCodeV2 = credentials.accountCode,
                accountId = credentials.accountId
            )
        }

    private fun sha256(value: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
}
