package fr.luteal.core.network

import fr.luteal.core.network.contract.models.Certainty
import fr.luteal.core.network.contract.models.CycleData
import fr.luteal.core.network.contract.models.EntityType
import fr.luteal.core.network.contract.models.RecordSource
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncWireTest {

    private val id = UUID.fromString("019832e0-6c14-7000-8000-000000000001")
    private val rev = UUID.fromString("019832e0-6c14-7000-8000-000000000002")
    private val instant = OffsetDateTime.of(2026, 7, 1, 8, 0, 0, 0, ZoneOffset.UTC)

    private fun cycle() = CycleData(
        id = id,
        clientRev = rev,
        createdAt = instant,
        updatedAt = instant,
        deletedAt = null,
        startDate = LocalDate.of(2026, 6, 30),
        endDate = null,
        lengthDays = null,
        bleedingDays = 2,
        certainty = Certainty.RECORDED,
        source = RecordSource.MANUAL,
        notes = ""
    )

    @Test
    fun `push request serializes polymorphic data with snake_case envelope`() {
        val request = PushRequestWire(
            changes = listOf(PushChangeWire(EntityType.CYCLE, cycle().toJsonElement()))
        )

        val parsed = Json.parseToJsonElement(request.toWireString()).jsonObject
        val change = parsed["changes"]!!.jsonArray[0].jsonObject

        assertEquals("cycle", change["entity_type"]!!.jsonPrimitive.content)
        val data = change["data"]!!.jsonObject
        assertEquals(id.toString(), data["id"]!!.jsonPrimitive.content)
        assertEquals(rev.toString(), data["client_rev"]!!.jsonPrimitive.content)
        assertEquals("2026-06-30", data["start_date"]!!.jsonPrimitive.content)
        assertEquals("2026-07-01T08:00:00Z", data["created_at"]!!.jsonPrimitive.content)
        assertEquals("recorded", data["certainty"]!!.jsonPrimitive.content)
    }

    @Test
    fun `push result decodes applied rejected and conflict current`() {
        val json = """
            {
              "applied": [{"entity_type":"cycle","entity_id":"$id","seq":412}],
              "rejected": [{"entity_type":"daily_entry","detail":"pain_level: must be between 1 and 5"}],
              "conflicts": [{
                "entity_type":"cycle","entity_id":"$id","reason":"superseded",
                "current": ${ContractJson.encodeToString(CycleData.serializer(), cycle())}
              }],
              "cursor": 412
            }
        """.trimIndent()

        val result = json.toPushResultWire()

        assertEquals(412L, result.cursor)
        assertEquals(1, result.applied.size)
        assertEquals(EntityType.CYCLE, result.applied[0].entityType)
        assertEquals(1, result.rejected.size)
        assertTrue(result.rejected[0].detail.contains("pain_level"))
        assertEquals(1, result.conflicts.size)
        // The server's current record is recoverable from the JsonElement.
        val current = result.conflicts[0].current.toCycleData()
        assertEquals(LocalDate.of(2026, 6, 30), current.startDate)
    }

    @Test
    fun `pull result decodes changes and tombstones`() {
        val json = """
            {
              "changes": [
                {"seq":410,"entity_type":"cycle","entity_id":"$id","deleted":false,
                 "updated_at":"2026-07-01T08:00:00Z",
                 "data": ${ContractJson.encodeToString(CycleData.serializer(), cycle())}},
                {"seq":411,"entity_type":"cycle","entity_id":"$id","deleted":true,
                 "updated_at":"2026-07-01T09:00:00Z","data":null}
              ],
              "cursor": 411,
              "has_more": true
            }
        """.trimIndent()

        val result = json.toPullResultWire()

        assertEquals(411L, result.cursor)
        assertTrue(result.hasMore)
        assertEquals(2, result.changes.size)

        val live = result.changes[0]
        assertEquals(false, live.deleted)
        assertEquals(LocalDate.of(2026, 6, 30), live.data!!.toCycleData().startDate)

        val tombstone = result.changes[1]
        assertEquals(true, tombstone.deleted)
        assertEquals(null, tombstone.data)
    }
}
