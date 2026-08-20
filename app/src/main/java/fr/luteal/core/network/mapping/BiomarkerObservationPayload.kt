package fr.luteal.core.network.mapping

import fr.luteal.core.data.entity.BiomarkerObservationEntity
import fr.luteal.core.model.BasalBodyTemperature
import fr.luteal.core.model.BbtDisturbance
import fr.luteal.core.model.BiomarkerObservation
import fr.luteal.core.model.CervicalFluidObservation
import fr.luteal.core.model.CervicalMucusSensation
import fr.luteal.core.model.CervicalMucusTexture
import fr.luteal.core.model.HcgTestResult
import fr.luteal.core.model.LhTestResult
import fr.luteal.core.model.RapidTestLogs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.UUID

@Serializable
data class BiomarkerObservationPayload(
    val id: String,
    @SerialName("client_rev") val clientRev: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("observed_date") val observedDate: String,
    @SerialName("bbt_celsius") val bbtCelsius: Double? = null,
    @SerialName("bbt_time") val bbtTime: String? = null,
    @SerialName("bbt_quality") val bbtQuality: String = "normal",
    @SerialName("bbt_disturbances") val bbtDisturbances: List<String> = emptyList(),
    @SerialName("cervical_sensation") val cervicalSensation: String? = null,
    @SerialName("cervical_texture") val cervicalTexture: String? = null,
    @SerialName("lh_test_result") val lhTestResult: String? = null,
    @SerialName("hcg_test_result") val hcgTestResult: String? = null,
    val notes: String = ""
) {
    val wireId: UUID get() = UUID.fromString(id)
    val wireClientRev: UUID get() = UUID.fromString(clientRev)
    val wireUpdatedAt: OffsetDateTime get() = OffsetDateTime.parse(updatedAt)
    val wireDeletedAt: OffsetDateTime? get() = deletedAt?.let(OffsetDateTime::parse)
}

fun BiomarkerObservation.toPayload(meta: SyncMeta): BiomarkerObservationPayload =
    BiomarkerObservationPayload(
        id = deterministicId("biomarker", date.toString()).toString(),
        clientRev = meta.clientRev.toString(),
        createdAt = meta.createdAt.toString(),
        updatedAt = meta.updatedAt.toString(),
        deletedAt = meta.deletedAt?.toString(),
        observedDate = date.toString(),
        bbtCelsius = bbt?.valueCelsius,
        bbtTime = bbt?.measuredTime?.toString()?.take(5),
        bbtQuality = if (bbt?.isDisturbed == true) "disturbed" else "normal",
        bbtDisturbances = bbt?.disturbances.orEmpty().map { it.name.lowercase() },
        cervicalSensation = cervicalFluid?.sensation?.name?.lowercase(),
        cervicalTexture = cervicalFluid?.texture?.name?.lowercase(),
        lhTestResult = rapidTests?.lhTest?.name?.lowercase(),
        hcgTestResult = rapidTests?.hcgTest?.name?.lowercase(),
        notes = notes
    )

fun BiomarkerObservationEntity.toDomainObservation(): BiomarkerObservation {
    val temperature = bbtCelsius?.let { celsius ->
        BasalBodyTemperature(
            valueCelsius = celsius,
            measuredTime = bbtTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
            disturbances = bbtDisturbancesJson.toNamedSet(BbtDisturbance.entries)
        )
    }
    val fluid = CervicalFluidObservation(
        sensation = cervicalSensation.toEnumOrNull(CervicalMucusSensation.entries),
        texture = cervicalTexture.toEnumOrNull(CervicalMucusTexture.entries)
    )
    val tests = RapidTestLogs(
        lhTest = lhTestResult.toEnumOrNull(LhTestResult.entries),
        hcgTest = hcgTestResult.toEnumOrNull(HcgTestResult.entries)
    )
    return BiomarkerObservation(
        date = LocalDate.parse(date),
        bbt = temperature,
        cervicalFluid = fluid.takeIf { it.hasObservation },
        rapidTests = tests.takeIf { it.hasLogs },
        notes = notes,
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis)
    )
}

fun BiomarkerObservationPayload.toEntity(): BiomarkerObservationEntity =
    BiomarkerObservationEntity(
        date = observedDate,
        bbtCelsius = bbtCelsius,
        bbtTime = bbtTime,
        bbtQuality = bbtQuality,
        bbtDisturbancesJson = org.json.JSONArray(bbtDisturbances.map { it.uppercase() }).toString(),
        cervicalSensation = cervicalSensation?.uppercase(),
        cervicalTexture = cervicalTexture?.uppercase(),
        lhTestResult = lhTestResult?.uppercase(),
        hcgTestResult = hcgTestResult?.uppercase(),
        notes = notes,
        updatedAtEpochMillis = OffsetDateTime.parse(updatedAt).toInstant().toEpochMilli()
    )

private fun <T : Enum<T>> String?.toEnumOrNull(entries: List<T>): T? =
    this?.let { value -> entries.firstOrNull { it.name.equals(value, ignoreCase = true) } }

private fun <T : Enum<T>> String.toNamedSet(entries: List<T>): Set<T> = runCatching {
    val array = org.json.JSONArray(this)
    buildSet {
        for (index in 0 until array.length()) {
            val name = array.getString(index)
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let(::add)
        }
    }
}.getOrDefault(emptySet())
