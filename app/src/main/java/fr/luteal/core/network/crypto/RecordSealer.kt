package fr.luteal.core.network.crypto

import fr.luteal.core.network.ContractJson
import fr.luteal.core.network.auth.SyncCredentialStore
import java.security.GeneralSecurityException
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonElement

/**
 * Seals and opens synchronized record payloads for the sync layer.
 *
 * Sits between the sync engine, which thinks in [JsonElement] record bodies,
 * and the wire, which carries base64 ciphertext. The server never sees either
 * the plaintext or the key.
 *
 * The record key is derived from the account code held in the Keystore-backed
 * [SyncCredentialStore] and cached in memory per account, because deriving it
 * once per record would mean an HKDF pass for every row in a sync batch.
 */
@Singleton
class RecordSealer @Inject constructor(
    private val credentialStore: SyncCredentialStore
) {
    private var cachedAccountId: String? = null
    private var cachedRecordKey: ByteArray? = null

    /**
     * @throws IllegalStateException when no account exists yet. Callers must
     * register before sealing: there is no key without an account code.
     */
    @Synchronized
    private fun recordKey(): ByteArray {
        val credentials = credentialStore.load()
            ?: throw IllegalStateException("Aucun compte synchronisé : clé indisponible.")

        cachedRecordKey?.let { key ->
            if (cachedAccountId == credentials.accountId) return key
        }

        val master = RecordCrypto.deriveMasterKey(
            accountCode = credentials.accountCode,
            accountId = credentials.accountId
        )
        val key = RecordCrypto.deriveRecordKey(master)
        master.fill(0)

        cachedAccountId = credentials.accountId
        cachedRecordKey = key
        return key
    }

    /** Drops the cached key, e.g. after the credentials are cleared. */
    @Synchronized
    fun invalidate() {
        cachedRecordKey?.fill(0)
        cachedRecordKey = null
        cachedAccountId = null
    }

    fun seal(
        entityType: String,
        entityId: String,
        clientRev: String,
        payload: JsonElement
    ): String {
        val plaintext = ContractJson.encodeToString(JsonElement.serializer(), payload)
        val sealed = RecordCrypto.seal(
            key = recordKey(),
            plaintext = plaintext.toByteArray(),
            associatedData = RecordCrypto.associatedData(entityType, entityId, clientRev)
        )
        return Base64.getEncoder().encodeToString(sealed)
    }

    /**
     * @throws GeneralSecurityException when the payload was tampered with, was
     * relocated onto a different record, or was sealed under another key.
     */
    fun open(
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
        val plaintext = RecordCrypto.open(
            key = recordKey(),
            envelope = sealed,
            associatedData = RecordCrypto.associatedData(entityType, entityId, clientRev)
        )
        return ContractJson.decodeFromString(JsonElement.serializer(), String(plaintext))
    }
}
