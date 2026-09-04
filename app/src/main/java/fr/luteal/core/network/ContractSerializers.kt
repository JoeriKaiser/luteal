package fr.luteal.core.network

import fr.luteal.core.network.contract.models.EntityType
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual

/**
 * Wire serializers for the contract DTOs. The generated models mark
 * [UUID], [LocalDate], and [OffsetDateTime] as `@Contextual`, so a
 * `SerializersModule` providing these must be installed on the `Json` used
 * for sync (see [ContractJson]).
 *
 * Date/instant formats match the backend exactly: calendar dates are
 * ISO-8601 (`2026-07-21`); instants are RFC 3339 UTC (`2026-07-21T14:03:00Z`).
 */
object UuidSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.util.UUID", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UUID) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
}

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.time.LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) =
        encoder.encodeString(value.format(DateTimeFormatter.ISO_LOCAL_DATE))

    override fun deserialize(decoder: Decoder): LocalDate =
        LocalDate.parse(decoder.decodeString(), DateTimeFormatter.ISO_LOCAL_DATE)
}

object OffsetDateTimeSerializer : KSerializer<OffsetDateTime> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.time.OffsetDateTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: OffsetDateTime) =
        encoder.encodeString(value.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))

    override fun deserialize(decoder: Decoder): OffsetDateTime =
        OffsetDateTime.parse(decoder.decodeString(), DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}

object SafeEntityTypeSerializer : KSerializer<EntityType?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("fr.luteal.core.network.SafeEntityType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: EntityType?) {
        encoder.encodeString(value?.value ?: "unknown")
    }

    override fun deserialize(decoder: Decoder): EntityType? {
        val raw = decoder.decodeString()
        return EntityType.entries.firstOrNull { it.value == raw }
    }
}

val ContractSerializersModule: SerializersModule = SerializersModule {
    contextual(UUID::class, UuidSerializer)
    contextual(LocalDate::class, LocalDateSerializer)
    contextual(OffsetDateTime::class, OffsetDateTimeSerializer)
}

/**
 * The `Json` instance for all contract (de)serialization. Timestamps should
 * be created at UTC second precision to match the server's RFC 3339 output.
 */
val ContractJson: Json = Json {
    serializersModule = ContractSerializersModule
    ignoreUnknownKeys = true
    encodeDefaults = true
}
