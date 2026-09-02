package fr.luteal.core.network.crypto

import fr.luteal.core.model.DuoProjection
import fr.luteal.core.network.ContractJson
import fr.luteal.core.network.contract.models.DuoView
import javax.inject.Inject
import javax.inject.Singleton

/** Opens the grant-filtered Duo projection through one shared crypto path. */
@Singleton
class DuoProjectionDecoder @Inject constructor(
    private val keyStore: DuoKeyStore
) {
    fun decode(view: DuoView): DuoProjectionDecodeResult {
        val payload = view.payload ?: return DuoProjectionDecodeResult.NoPayload
        val linkId = view.linkId.toString()
        val key = keyStore.load(linkId) ?: return DuoProjectionDecodeResult.KeyMissing

        return runCatching {
            val plaintext = DuoCrypto.openRaw(key, linkId, payload)
            ContractJson.decodeFromString(DuoProjection.serializer(), String(plaintext))
        }.fold(
            onSuccess = { DuoProjectionDecodeResult.Available(it) },
            onFailure = { DuoProjectionDecodeResult.InvalidPayload }
        )
    }
}

sealed interface DuoProjectionDecodeResult {
    data class Available(val projection: DuoProjection) : DuoProjectionDecodeResult
    data object NoPayload : DuoProjectionDecodeResult
    data object KeyMissing : DuoProjectionDecodeResult
    data object InvalidPayload : DuoProjectionDecodeResult
}
