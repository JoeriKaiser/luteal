package fr.luteal.core.network

import fr.luteal.core.network.contract.models.AppliedChange
import fr.luteal.core.network.contract.models.BleedingObservationData
import fr.luteal.core.network.contract.models.CycleData
import fr.luteal.core.network.contract.models.DailyEntryData
import fr.luteal.core.network.contract.models.EntityType
import fr.luteal.core.network.contract.models.RejectedChange
import fr.luteal.core.network.contract.models.SymptomLogData
import fr.luteal.core.network.crypto.RecordSealer
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Wire envelopes for sync push/pull.
 *
 * The generated [fr.luteal.core.network.contract.models.SyncChangeInput],
 * [fr.luteal.core.network.contract.models.SyncPullChange], and
 * [fr.luteal.core.network.contract.models.ConflictChange] type their polymorphic
 * `data`/`current` payload as `kotlin.Any`, which kotlinx.serialization cannot
 * (de)serialize. These mirrors keep the exact same wire shape but carry that
 * payload as a [JsonElement], which we then (de)serialize against the concrete
 * generated record type ([CycleData], [BleedingObservationData], ...) using
 * [ContractJson]. Enums and the fully-typed [AppliedChange]/[RejectedChange]
 * are reused from the generated models.
 */

// --- Push ------------------------------------------------------------------

/**
 * One end-to-end encrypted change.
 *
 * [ciphertext] is base64 of the sealed record (see
 * [fr.luteal.core.network.crypto.RecordCrypto]). The remaining fields are
 * plaintext because the server needs them to route the upsert, order the
 * last-write-wins guard, and append the change log - it can no longer read
 * them out of the payload. They are also bound into the AEAD associated data,
 * so the server cannot move a payload between records undetected.
 */
@Serializable
data class PushChangeWire(
    @SerialName("entity_type") val entityType: EntityType,
    @Contextual @SerialName("entity_id") val entityId: UUID,
    @Contextual @SerialName("client_rev") val clientRev: UUID,
    @Contextual @SerialName("updated_at") val updatedAt: OffsetDateTime,
    @SerialName("deleted") val deleted: Boolean,
    @SerialName("ciphertext") val ciphertext: String? = null
)

@Serializable
data class PushRequestWire(
    @SerialName("changes") val changes: List<PushChangeWire>
)

@Serializable
data class ConflictWire(
    @SerialName("entity_type") val entityType: EntityType,
    @Contextual @SerialName("entity_id") val entityId: UUID,
    @SerialName("reason") val reason: String,
    @Contextual @SerialName("current_client_rev") val currentClientRev: UUID,
    @Contextual @SerialName("current_updated_at") val currentUpdatedAt: OffsetDateTime,
    @SerialName("current_deleted") val currentDeleted: Boolean = false,
    @SerialName("current_ciphertext") val currentCiphertext: String? = null
)

@Serializable
data class PushResultWire(
    @SerialName("applied") val applied: List<AppliedChange> = emptyList(),
    @SerialName("rejected") val rejected: List<RejectedChange> = emptyList(),
    @SerialName("conflicts") val conflicts: List<ConflictWire> = emptyList(),
    @SerialName("cursor") val cursor: Long
)

// --- Pull ------------------------------------------------------------------

@Serializable
data class PullChangeWire(
    @SerialName("seq") val seq: Long,
    @SerialName("entity_type") val entityType: EntityType,
    @Contextual @SerialName("entity_id") val entityId: UUID,
    @Contextual @SerialName("client_rev") val clientRev: UUID,
    @SerialName("deleted") val deleted: Boolean,
    @Contextual @SerialName("updated_at") val updatedAt: OffsetDateTime,
    @SerialName("ciphertext") val ciphertext: String? = null
)

@Serializable
data class PullResultWire(
    @SerialName("changes") val changes: List<PullChangeWire>,
    @SerialName("cursor") val cursor: Long,
    @SerialName("has_more") val hasMore: Boolean
)

// --- (De)serialization helpers against the concrete record types -----------

fun CycleData.toJsonElement(): JsonElement =
    ContractJson.encodeToJsonElement(CycleData.serializer(), this)

fun BleedingObservationData.toJsonElement(): JsonElement =
    ContractJson.encodeToJsonElement(BleedingObservationData.serializer(), this)

fun JsonElement.toCycleData(): CycleData =
    ContractJson.decodeFromJsonElement(CycleData.serializer(), this)

fun JsonElement.toBleedingObservationData(): BleedingObservationData =
    ContractJson.decodeFromJsonElement(BleedingObservationData.serializer(), this)

fun DailyEntryData.toJsonElement(): JsonElement =
    ContractJson.encodeToJsonElement(DailyEntryData.serializer(), this)

fun JsonElement.toDailyEntryData(): DailyEntryData =
    ContractJson.decodeFromJsonElement(DailyEntryData.serializer(), this)

fun SymptomLogData.toJsonElement(): JsonElement =
    ContractJson.encodeToJsonElement(SymptomLogData.serializer(), this)

fun fr.luteal.core.network.mapping.BiomarkerObservationPayload.toJsonElement(): JsonElement =
    ContractJson.encodeToJsonElement(
        fr.luteal.core.network.mapping.BiomarkerObservationPayload.serializer(),
        this
    )

fun JsonElement.toBiomarkerObservationPayload(): fr.luteal.core.network.mapping.BiomarkerObservationPayload =
    ContractJson.decodeFromJsonElement(
        fr.luteal.core.network.mapping.BiomarkerObservationPayload.serializer(),
        this
    )

fun JsonElement.toSymptomLogData(): SymptomLogData =
    ContractJson.decodeFromJsonElement(SymptomLogData.serializer(), this)

/** Serializes a push request to its JSON wire string. */
fun PushRequestWire.toWireString(): String =
    ContractJson.encodeToString(PushRequestWire.serializer(), this)

fun String.toPushResultWire(): PushResultWire =
    ContractJson.decodeFromString(PushResultWire.serializer(), this)

fun String.toPullResultWire(): PullResultWire =
    ContractJson.decodeFromString(PullResultWire.serializer(), this)

// --- Sealing ---------------------------------------------------------------
//
// Each record carries its own envelope (id, client_rev, updated_at,
// deleted_at), so the routing fields the server needs are derived from the
// record itself rather than threaded separately. The same three values are
// bound into the AEAD associated data.

private fun sealedChange(
    sealer: RecordSealer,
    entityType: EntityType,
    entityId: UUID,
    clientRev: UUID,
    updatedAt: OffsetDateTime,
    deletedAt: OffsetDateTime?,
    payload: JsonElement
): PushChangeWire {
    val deleted = deletedAt != null
    return PushChangeWire(
        entityType = entityType,
        entityId = entityId,
        clientRev = clientRev,
        updatedAt = updatedAt,
        deleted = deleted,
        // A tombstone carries no content: there is nothing to protect, and the
        // server rejects ciphertext on a delete.
        ciphertext = if (deleted) {
            null
        } else {
            sealer.seal(entityType.value, entityId.toString(), clientRev.toString(), payload)
        }
    )
}

fun CycleData.toPushChange(sealer: RecordSealer): PushChangeWire = sealedChange(
    sealer, EntityType.CYCLE, id, clientRev, updatedAt, deletedAt, toJsonElement()
)

fun BleedingObservationData.toPushChange(sealer: RecordSealer): PushChangeWire = sealedChange(
    sealer, EntityType.BLEEDING_OBSERVATION, id, clientRev, updatedAt, deletedAt, toJsonElement()
)

fun DailyEntryData.toPushChange(sealer: RecordSealer): PushChangeWire = sealedChange(
    sealer, EntityType.DAILY_ENTRY, id, clientRev, updatedAt, deletedAt, toJsonElement()
)

fun SymptomLogData.toPushChange(sealer: RecordSealer): PushChangeWire = sealedChange(
    sealer, EntityType.SYMPTOM_LOG, id, clientRev, updatedAt, deletedAt, toJsonElement()
)

fun fr.luteal.core.network.mapping.BiomarkerObservationPayload.toPushChange(
    sealer: RecordSealer
): PushChangeWire = sealedChange(
    sealer,
    EntityType.BIOMARKER_OBSERVATION,
    wireId,
    wireClientRev,
    wireUpdatedAt,
    wireDeletedAt,
    toJsonElement()
)

/** Opens a pulled change, or returns null for a tombstone. */
fun PullChangeWire.openPayload(sealer: RecordSealer): JsonElement? {
    val sealed = ciphertext ?: return null
    return sealer.open(entityType.value, entityId.toString(), clientRev.toString(), sealed)
}

/** Opens the server's current state from a conflict, or null for a tombstone. */
fun ConflictWire.openCurrent(sealer: RecordSealer): JsonElement? {
    val sealed = currentCiphertext ?: return null
    return sealer.open(
        entityType.value, entityId.toString(), currentClientRev.toString(), sealed
    )
}
