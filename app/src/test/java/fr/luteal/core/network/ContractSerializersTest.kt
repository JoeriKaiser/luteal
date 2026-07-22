package fr.luteal.core.network

import fr.luteal.core.network.contract.models.Certainty
import fr.luteal.core.network.contract.models.CycleData
import fr.luteal.core.network.contract.models.RecordSource
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractSerializersTest {

    @Test
    fun `cycle data round-trips with snake_case keys and rfc3339 dates`() {
        val id = UUID.fromString("019832e0-6c14-7000-8000-000000000001")
        val rev = UUID.fromString("019832e0-6c14-7000-8000-000000000002")
        val created = OffsetDateTime.of(2026, 7, 1, 8, 0, 0, 0, ZoneOffset.UTC)
        val cycle = CycleData(
            id = id,
            clientRev = rev,
            createdAt = created,
            updatedAt = created,
            deletedAt = null,
            startDate = LocalDate.of(2026, 6, 30),
            endDate = null,
            lengthDays = null,
            bleedingDays = 5,
            certainty = Certainty.RECORDED,
            source = RecordSource.MANUAL,
            notes = "",
        )

        val json = ContractJson.encodeToString(CycleData.serializer(), cycle)
        val obj = Json.parseToJsonElement(json).jsonObject

        // Wire format must match the Go server exactly.
        assertEquals("2026-06-30", obj["start_date"]!!.jsonPrimitive.content)
        assertEquals("2026-07-01T08:00:00Z", obj["created_at"]!!.jsonPrimitive.content)
        assertEquals("recorded", obj["certainty"]!!.jsonPrimitive.content)
        assertEquals("5", obj["bleeding_days"]!!.jsonPrimitive.content)
        assertTrue(obj.containsKey("client_rev"))

        val decoded = ContractJson.decodeFromString(CycleData.serializer(), json)
        assertEquals(cycle, decoded)
    }

    @Test
    fun `uuid and date serializers are inverse`() {
        val uuid = UUID.fromString("019832e0-6c14-7000-8000-000000000099")
        val encoded = ContractJson.encodeToString(UuidSerializer, uuid)
        assertEquals("\"019832e0-6c14-7000-8000-000000000099\"", encoded)
        assertEquals(uuid, ContractJson.decodeFromString(UuidSerializer, encoded))

        val date = LocalDate.of(2026, 12, 31)
        val dEnc = ContractJson.encodeToString(LocalDateSerializer, date)
        assertEquals("\"2026-12-31\"", dEnc)
        assertEquals(date, ContractJson.decodeFromString(LocalDateSerializer, dEnc))
    }
}
