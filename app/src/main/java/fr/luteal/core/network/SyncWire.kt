package fr.luteal.core.network

import fr.luteal.core.network.contract.models.AppliedChange
import fr.luteal.core.network.contract.models.BleedingObservationData
import fr.luteal.core.network.contract.models.CycleData
import fr.luteal.core.network.contract.models.DailyEntryData
import fr.luteal.core.network.contract.models.EntityType
import fr.luteal.core.network.contract.models.RejectedChange
import fr.luteal.core.network.contract.models.SymptomLogData
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

@Serializable
data class PushChangeWire(
    @SerialName("entity_type") val entityType: EntityType,
    @SerialName("data") val data: JsonElement
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
    @SerialName("current") val current: JsonElement
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
    @SerialName("deleted") val deleted: Boolean,
    @Contextual @SerialName("updated_at") val updatedAt: OffsetDateTime,
    @SerialName("data") val data: JsonElement? = null
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

fun JsonElement.toSymptomLogData(): SymptomLogData =
    ContractJson.decodeFromJsonElement(SymptomLogData.serializer(), this)

/** Serializes a push request to its JSON wire string. */
fun PushRequestWire.toWireString(): String =
    ContractJson.encodeToString(PushRequestWire.serializer(), this)

fun String.toPushResultWire(): PushResultWire =
    ContractJson.decodeFromString(PushResultWire.serializer(), this)

fun String.toPullResultWire(): PullResultWire =
    ContractJson.decodeFromString(PullResultWire.serializer(), this)
