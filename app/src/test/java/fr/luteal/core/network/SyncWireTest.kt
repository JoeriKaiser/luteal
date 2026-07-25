package fr.luteal.core.network

import fr.luteal.core.network.contract.models.Certainty
import fr.luteal.core.network.contract.models.CycleData
import fr.luteal.core.network.contract.models.EntityType
import fr.luteal.core.network.contract.models.RecordSource
import fr.luteal.core.network.crypto.RecordSealer
import fr.luteal.core.network.auth.SyncCredentialStore
import fr.luteal.core.network.auth.SyncCredentials
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncWireTest {

    private val id = UUID.fromString("019832e0-6c14-7000-8000-000000000001")
    private val rev = UUID.fromString("019832e0-6c14-7000-8000-000000000002")
    private val instant = OffsetDateTime.of(2026, 7, 1, 8, 0, 0, 0, ZoneOffset.UTC)

    /** In-memory credential store so the sealer can derive a real key. */
    private val credentials = object : SyncCredentialStore {
        override fun load() = SyncCredentials(
            accountId = "019832e0-6c14-7000-8000-0000000000aa",
            accountCode = "LTL-A1B2C-D3E4F-G5H6J-K7M8N",
            deviceToken = "ltok_test"
        )
        override fun save(credentials: SyncCredentials) = Unit
        override fun clear() = Unit
    }

    private val sealer = RecordSealer(credentials)

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
        notes = "note privee"
    )

    @Test
    fun `push request carries routing metadata in the clear and content sealed`() {
        val request = PushRequestWire(changes = listOf(cycle().toPushChange(sealer)))

        val parsed = Json.parseToJsonElement(request.toWireString()).jsonObject
        val change = parsed["changes"]!!.jsonArray[0].jsonObject

        // Routing metadata the server needs to target the upsert and order it.
        assertEquals("cycle", change["entity_type"]!!.jsonPrimitive.content)
        assertEquals(id.toString(), change["entity_id"]!!.jsonPrimitive.content)
        assertEquals(rev.toString(), change["client_rev"]!!.jsonPrimitive.content)
        assertEquals("2026-07-01T08:00:00Z", change["updated_at"]!!.jsonPrimitive.content)
        assertEquals(false, change["deleted"]!!.jsonPrimitive.content.toBoolean())

        // Content must be sealed, and nothing observational may appear.
        assertNotNull(change["ciphertext"])
        assertNull(change["data"])
        val wire = request.toWireString()
        assertTrue("start_date must not appear in the clear", !wire.contains("start_date"))
        assertTrue("notes must not appear in the clear", !wire.contains("note privee"))
    }

    @Test
    fun `sealed push change round trips back to the record`() {
        val change = cycle().toPushChange(sealer)

        val opened = sealer.open(
            entityType = change.entityType.value,
            entityId = change.entityId.toString(),
            clientRev = change.clientRev.toString(),
            ciphertext = change.ciphertext!!
        ).toCycleData()

        assertEquals(LocalDate.of(2026, 6, 30), opened.startDate)
        assertEquals("note privee", opened.notes)
    }

    @Test
    fun `tombstone carries no ciphertext`() {
        val deleted = cycle().copy(deletedAt = instant)
        val change = deleted.toPushChange(sealer)

        assertTrue(change.deleted)
        assertNull("a delete has no content to protect", change.ciphertext)
    }

    @Test
    fun `push result decodes applied rejected and sealed conflict state`() {
        val sealed = cycle().toPushChange(sealer).ciphertext!!
        val json = """
            {
              "applied": [{"entity_type":"cycle","entity_id":"$id","seq":412}],
              "rejected": [{"entity_type":"daily_entry","detail":"ciphertext: requis"}],
              "conflicts": [{
                "entity_type":"cycle","entity_id":"$id","reason":"superseded",
                "current_client_rev":"$rev",
                "current_updated_at":"2026-07-01T08:00:00Z",
                "current_deleted":false,
                "current_ciphertext":"$sealed"
              }],
              "cursor": 412
            }
        """.trimIndent()

        val result = json.toPushResultWire()

        assertEquals(412L, result.cursor)
        assertEquals(1, result.applied.size)
        assertEquals(EntityType.CYCLE, result.applied[0].entityType)
        assertEquals(1, result.rejected.size)
        assertTrue(result.rejected[0].detail.contains("ciphertext"))
        assertEquals(1, result.conflicts.size)

        // The server's current record is recoverable only by decrypting it.
        val current = result.conflicts[0].openCurrent(sealer)!!.toCycleData()
        assertEquals(LocalDate.of(2026, 6, 30), current.startDate)
    }

    @Test
    fun `pull result decodes sealed changes and tombstones`() {
        val sealed = cycle().toPushChange(sealer).ciphertext!!
        val json = """
            {
              "changes": [
                {"seq":410,"entity_type":"cycle","entity_id":"$id","client_rev":"$rev",
                 "deleted":false,"updated_at":"2026-07-01T08:00:00Z",
                 "ciphertext":"$sealed"},
                {"seq":411,"entity_type":"cycle","entity_id":"$id","client_rev":"$rev",
                 "deleted":true,"updated_at":"2026-07-01T09:00:00Z","ciphertext":null}
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
        assertEquals(
            LocalDate.of(2026, 6, 30),
            live.openPayload(sealer)!!.toCycleData().startDate
        )

        val tombstone = result.changes[1]
        assertEquals(true, tombstone.deleted)
        assertNull(tombstone.openPayload(sealer))
    }
}
