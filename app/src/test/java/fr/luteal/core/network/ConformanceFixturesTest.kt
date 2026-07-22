package fr.luteal.core.network

import fr.luteal.core.model.BleedingIntensity
import fr.luteal.core.network.contract.models.EntityType
import fr.luteal.core.network.contract.models.Flow
import fr.luteal.core.network.contract.models.Register201Response
import fr.luteal.core.network.mapping.toBleedingIntensity
import java.io.File
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Client-side half of the shared conformance fixtures. The same golden
 * response bodies in `folicular/conformance/` are validated against the
 * OpenAPI spec by folicular's `internal/contract` fixtures test (the server
 * proves it can produce them); here the client proves it can parse them with
 * [ContractJson] and map them to domain types. The fixture directory is passed
 * via the `folicular.conformance` system property (see app/build.gradle.kts),
 * defaulting to the sibling folicular checkout.
 */
class ConformanceFixturesTest {

    private fun body(name: String): JsonElement {
        val dir = System.getProperty("folicular.conformance")
            ?: error("folicular.conformance system property is not set")
        val file = File(dir, "$name.json")
        assertTrue("missing fixture $file", file.exists())
        return Json.parseToJsonElement(file.readText()).jsonObject["body"]!!
    }

    @Test
    fun `register response decodes to account code and device token`() {
        val response = ContractJson.decodeFromJsonElement(
            Register201Response.serializer(),
            body("register")
        )

        assertEquals("LTL-8K3FQ-Z2WNT-7HJMC-4XRDB", response.account.code)
        assertEquals("Pixel 9", response.device.name)
        assertEquals("ltok_Ab3-ExampleToken", response.device.token)
        assertTrue(response.warning.isNotBlank())
    }

    @Test
    fun `sync push response decodes applied rejected and conflict current`() {
        val result = ContractJson.decodeFromJsonElement(PushResultWire.serializer(), body("sync_push"))

        assertEquals(412L, result.cursor)

        assertEquals(1, result.applied.size)
        assertEquals(EntityType.CYCLE, result.applied[0].entityType)
        assertEquals(412L, result.applied[0].seq)

        assertEquals(1, result.rejected.size)
        assertEquals(EntityType.DAILY_ENTRY, result.rejected[0].entityType)
        assertTrue(result.rejected[0].detail.contains("pain_level"))

        assertEquals(1, result.conflicts.size)
        val conflict = result.conflicts[0]
        assertEquals(EntityType.CYCLE, conflict.entityType)
        assertEquals("superseded", conflict.reason)
        // The server's current record is recoverable from the JsonElement.
        val current = conflict.current.toCycleData()
        assertEquals(LocalDate.of(2026, 6, 30), current.startDate)
    }

    @Test
    fun `sync pull response decodes cycle bleeding and tombstone changes`() {
        val result = ContractJson.decodeFromJsonElement(PullResultWire.serializer(), body("sync_pull"))

        assertEquals(412L, result.cursor)
        assertEquals(false, result.hasMore)
        assertEquals(3, result.changes.size)

        val cycle = result.changes[0]
        assertEquals(EntityType.CYCLE, cycle.entityType)
        assertEquals(false, cycle.deleted)
        val cycleData = cycle.data!!.toCycleData()
        assertEquals(LocalDate.of(2026, 6, 30), cycleData.startDate)
        assertEquals(LocalDate.of(2026, 7, 27), cycleData.endDate)

        val bleeding = result.changes[1]
        assertEquals(EntityType.BLEEDING_OBSERVATION, bleeding.entityType)
        val obs = bleeding.data!!.toBleedingObservationData()
        assertEquals(Flow.MEDIUM, obs.flow)
        assertEquals(LocalDate.of(2026, 6, 30), obs.observedDate)
        assertEquals(BleedingIntensity.MEDIUM, obs.flow.toBleedingIntensity())

        val tombstone = result.changes[2]
        assertEquals(EntityType.CYCLE, tombstone.entityType)
        assertEquals(true, tombstone.deleted)
        assertNull(tombstone.data)
    }
}
