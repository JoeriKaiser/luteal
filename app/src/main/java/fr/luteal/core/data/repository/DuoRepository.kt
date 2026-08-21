package fr.luteal.core.data.repository

import fr.luteal.core.network.FolicularApiClient
import fr.luteal.core.network.auth.SyncCredentialStore
import fr.luteal.core.network.contract.models.AcceptLink201Response
import fr.luteal.core.network.contract.models.DuoView
import fr.luteal.core.network.contract.models.GrantField
import fr.luteal.core.network.contract.models.Invitation
import fr.luteal.core.network.contract.models.ListLinks200Response
import fr.luteal.core.network.contract.models.SupportKind
import fr.luteal.core.network.contract.models.SupportRequest
import fr.luteal.core.network.sync.FolicularApiClientFactory
import fr.luteal.core.network.sync.SyncCursorStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Duo operations. Wraps the API client with credential
 * resolution: every call reads the device token from the Keystore-backed
 * [SyncCredentialStore] and constructs a client for the configured base URL.
 *
 * Throws [IllegalStateException] when no credentials exist (the user must
 * sync at least once before Duo is available).
 */
@Singleton
class DuoRepository @Inject constructor(
    private val credentialStore: SyncCredentialStore,
    private val apiClientFactory: FolicularApiClientFactory,
    private val cursorStore: SyncCursorStore
) {
    private suspend fun client(): Pair<FolicularApiClient, String> {
        val creds = credentialStore.load()
            ?: throw IllegalStateException("Aucun compte synchronisé. Activez la synchronisation d'abord.")
        return apiClientFactory.create(cursorStore.getBaseUrl()) to creds.deviceToken
    }

    suspend fun createInvitation(): Invitation {
        val (client, token) = client()
        return client.createInvitation(token)
    }

    suspend fun acceptLink(pairingCode: String): AcceptLink201Response {
        val (client, token) = client()
        return client.acceptLink(token, pairingCode)
    }

    suspend fun listLinks(): ListLinks200Response {
        val (client, token) = client()
        return client.listLinks(token)
    }

    suspend fun patchGrants(linkId: String, field: GrantField, granted: Boolean) {
        val (client, token) = client()
        client.patchGrants(token, linkId, field, granted)
    }

    suspend fun revokeLink(linkId: String) {
        val (client, token) = client()
        client.revokeLink(token, linkId)
    }

    suspend fun duoView(): DuoView {
        val (client, token) = client()
        return client.duoView(token)
    }

    /** Publishes the sealed projection composed by the tracker's device. */
    suspend fun putDuoPayload(payload: String) {
        val (client, token) = client()
        client.putDuoPayload(token, payload)
    }

    /**
     * [sealedMessage] is base64 of the message already sealed under the Duo
     * link key.
     */
    suspend fun createSupportRequest(
        linkId: String, kind: SupportKind, sealedMessage: String
    ): SupportRequest {
        val (client, token) = client()
        return client.createSupportRequest(token, linkId, kind, sealedMessage)
    }

    suspend fun ackSupportRequest(requestId: String) {
        val (client, token) = client()
        client.ackSupportRequest(token, requestId)
    }

    /**
     * Keystore-backed credential load (AES-GCM decrypts) must not run on the
     * caller's dispatcher; UI callers sit on Main.
     */
    suspend fun hasAccount(): Boolean = withContext(Dispatchers.IO) {
        credentialStore.load() != null
    }
}
