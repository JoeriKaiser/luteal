package fr.luteal.core.network.mapping

import fr.luteal.core.model.Symptom
import fr.luteal.core.model.SymptomCategory
import fr.luteal.core.network.contract.models.SymptomDefinitionData
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import fr.luteal.core.network.contract.models.SymptomCategory as ContractSymptomCategory

class SymptomCatalogAdopterTest {

    private val now = OffsetDateTime.of(2026, 7, 21, 12, 0, 0, 0, ZoneOffset.UTC)

    private fun def(
        key: String,
        category: ContractSymptomCategory,
        active: Boolean = true,
        deleted: Boolean = false,
    ) = SymptomDefinitionData(
        id = UUID.randomUUID(),
        clientRev = UUID.randomUUID(),
        createdAt = now,
        updatedAt = now,
        deletedAt = if (deleted) now else null,
        key = key,
        label = key.replace('_', ' '),
        category = category,
        builtin = true,
        active = active,
    )

    @Test
    fun `server built-ins are adopted by key and local customs are preserved`() {
        val server = listOf(
            def("cramps", ContractSymptomCategory.PAIN),
            def("headache", ContractSymptomCategory.PAIN),
            def("acne", ContractSymptomCategory.PHYSICAL),
        )
        val local = Symptom.DEFAULT_SYMPTOMS // 8 symptoms, 3 of which overlap

        val result = SymptomCatalogAdopter.adopt(server, local)
        val ids = result.map { it.id }

        // 3 adopted from server + 5 local customs not on the server.
        assertEquals(8, result.size)
        assertTrue("cramps" in ids)
        assertTrue("mood_changes" in ids) // preserved custom
        assertEquals(1, ids.count { it == "cramps" })
    }

    @Test
    fun `inactive and deleted server defs are not adopted`() {
        val server = listOf(
            def("cramps", ContractSymptomCategory.PAIN, active = false),
            def("headache", ContractSymptomCategory.PAIN, deleted = true),
        )
        val result = SymptomCatalogAdopter.adopt(server, emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `a local symptom shadowed by a server key takes the server category`() {
        val server = listOf(def("cramps", ContractSymptomCategory.PHYSICAL))
        val local = listOf(Symptom("cramps", SymptomCategory.PAIN, "cramps"))

        val result = SymptomCatalogAdopter.adopt(server, local)

        assertEquals(1, result.size)
        // Server definition wins: category comes from the server.
        assertEquals(SymptomCategory.PHYSICAL, result[0].category)
    }
}
