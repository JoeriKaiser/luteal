package fr.luteal.core.network

import fr.luteal.core.network.contract.models.DuoRole
import fr.luteal.core.network.contract.models.DuoView
import fr.luteal.core.network.contract.models.GrantField
import fr.luteal.core.network.contract.models.SupportKind
import fr.luteal.core.network.contract.models.SupportRequest
import java.time.OffsetDateTime
import java.util.Base64
import java.util.UUID
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire DTOs for the Duo endpoints.
 *
 * The OpenAPI generator renders `format: byte` as `kotlin.ByteArray`, but
 * kotlinx.serialization encodes a `ByteArray` as a JSON array of signed
 * numbers, while the Go backend follows the JSON convention for `[]byte`: a
 * base64 string. The generated [DuoView]/[SupportRequest] models therefore
 * cannot decode a real server response (the payload and message ciphertext
 * arrive base64), and a request built from them would be rejected with
 * `cannot unmarshal number -N into ... of type uint8`.
 *
 * These mirrors carry the byte fields as base64 [String]s and map onto the
 * generated models, which stay the app-facing types everywhere else.
 */
@Serializable
internal data class DuoViewWire(
    @Contextual @SerialName("link_id") val linkId: UUID,
    @Contextual @SerialName("role") val role: DuoRole,
    @Contextual @SerialName("as_of") val asOf: OffsetDateTime,
    /* Sealed projection, base64; null until the tracker publishes one. */
    @SerialName("payload") val payload: String? = null,
    @Contextual @SerialName("payload_updated_at")
    val payloadUpdatedAt: OffsetDateTime? = null,
    @Contextual @SerialName("grants") val grants: List<GrantField>? = null,
    @SerialName("support_requests") val supportRequests: List<SupportRequestWire>? = null
)

@Serializable
internal data class SupportRequestWire(
    @Contextual @SerialName("id") val id: UUID,
    @Contextual @SerialName("author_role") val authorRole: DuoRole,
    @Contextual @SerialName("kind") val kind: SupportKind,
    @Contextual @SerialName("created_at") val createdAt: OffsetDateTime,
    /* Sealed message, base64; the server relays it without reading it. */
    @SerialName("message_ciphertext") val messageCiphertext: String? = null,
    @Contextual @SerialName("acknowledged_at") val acknowledgedAt: OffsetDateTime? = null
)

@Serializable
internal data class CreateSupportRequestRequestWire(
    @Contextual @SerialName("link_id") val linkId: UUID,
    @Contextual @SerialName("kind") val kind: SupportKind,
    /* Base64 of the sealed message. */
    @SerialName("message") val message: String
)

internal fun DuoViewWire.toModel(): DuoView = DuoView(
    linkId = linkId,
    role = role,
    asOf = asOf,
    payload = payload?.let { Base64.getDecoder().decode(it) },
    payloadUpdatedAt = payloadUpdatedAt,
    grants = grants,
    supportRequests = supportRequests?.map { it.toModel() }
)

internal fun SupportRequestWire.toModel(): SupportRequest = SupportRequest(
    id = id,
    authorRole = authorRole,
    kind = kind,
    createdAt = createdAt,
    messageCiphertext = messageCiphertext?.let { Base64.getDecoder().decode(it) },
    acknowledgedAt = acknowledgedAt
)
