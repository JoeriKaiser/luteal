package fr.luteal.core.network

import fr.luteal.core.network.contract.models.DuoRole
import fr.luteal.core.network.contract.models.GrantField
import fr.luteal.core.network.contract.models.SupportKind
import java.time.OffsetDateTime
import java.util.Base64
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The generated DTOs render `format: byte` as kotlin.ByteArray, which
 * kotlinx.serialization encodes as a JSON array of signed numbers. The Go
 * backend follows the JSON convention for []byte: a base64 string. These
 * tests pin the Duo wire mirrors to the server's actual wire format.
 */
class DuoWireTest {

    private val linkId = UUID.fromString("f70af86b-deec-44f3-b399-86e006036d5b")

    @Test
    fun `duoView decodes a base64 payload and message ciphertext`() {
        val json = """
            {
              "link_id": "$linkId",
              "role": "tracker",
              "as_of": "2026-08-07T12:00:00Z",
              "payload": "aGVsbG8=",
              "payload_updated_at": "2026-08-07T11:00:00Z",
              "grants": ["cycle_day", "mood"],
              "support_requests": [
                {
                  "id": "019832e1-0000-7000-8000-000000000abc",
                  "author_role": "partner",
                  "kind": "comfort",
                  "created_at": "2026-08-07T10:00:00Z",
                  "message_ciphertext": "c2VjcmV0",
                  "acknowledged_at": null
                }
              ]
            }
        """.trimIndent()

        val view = ContractJson.decodeFromString(DuoViewWire.serializer(), json).toModel()

        assertEquals(linkId, view.linkId)
        assertEquals(DuoRole.TRACKER, view.role)
        // Base64 "aGVsbG8=" -> "hello"
        assertArrayEquals("hello".toByteArray(), view.payload)
        assertEquals(listOf(GrantField.CYCLE_DAY, GrantField.MOOD), view.grants)
        assertEquals(1, view.supportRequests?.size)
        // Base64 "c2VjcmV0" -> "secret"
        assertArrayEquals("secret".toByteArray(), view.supportRequests?.first()?.messageCiphertext)
        assertEquals(SupportKind.COMFORT, view.supportRequests?.first()?.kind)
        assertEquals(DuoRole.PARTNER, view.supportRequests?.first()?.authorRole)
    }

    @Test
    fun `duoView tolerates a missing grants field`() {
        val json = """
            {
              "link_id": "$linkId",
              "role": "partner",
              "as_of": "2026-08-07T12:00:00Z"
            }
        """.trimIndent()

        val view = ContractJson.decodeFromString(DuoViewWire.serializer(), json).toModel()

        assertNull(view.grants)
        assertNull(view.payload)
        assertNull(view.supportRequests)
    }

    @Test
    fun `support request serializes the message as a base64 string`() {
        val sealed = byteArrayOf(-51, 0, 12, 100, -128)
        val encoded = Base64.getEncoder().encodeToString(sealed)
        val json = ContractJson.encodeToString(
            CreateSupportRequestRequestWire.serializer(),
            CreateSupportRequestRequestWire(linkId, SupportKind.PRACTICAL, encoded)
        )

        // The wire carries a base64 string, never a JSON array of numbers.
        assertTrue(json.contains("\"message\":\"$encoded\""))
        assertTrue(!json.contains("["))
        val decoded = ContractJson.decodeFromString(
            CreateSupportRequestRequestWire.serializer(), json
        )
        assertEquals(linkId, decoded.linkId)
        assertEquals(SupportKind.PRACTICAL, decoded.kind)
        assertEquals(encoded, decoded.message)
    }

    @Test
    fun `sealed bytes survive an encode-decode round trip through the wire`() {
        val sealed = ByteArray(256) { (it * 7 - 128).toByte() }
        val wire = CreateSupportRequestRequestWire(
            linkId, SupportKind.GENERAL, Base64.getEncoder().encodeToString(sealed)
        )
        val json = ContractJson.encodeToString(
            CreateSupportRequestRequestWire.serializer(), wire
        )
        val roundTripped = ContractJson.decodeFromString(
            CreateSupportRequestRequestWire.serializer(), json
        )
        assertArrayEquals(sealed, Base64.getDecoder().decode(roundTripped.message))
    }
}
