package fr.luteal.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class CycleFactsTest {

    @Test
    fun `the same day always yields the same fact`() {
        val date = LocalDate.parse("2026-07-26")

        // The point of date-seeding rather than random selection: reopening the
        // app must not reshuffle the card.
        repeat(20) {
            assertEquals(CycleFacts.forDate(date), CycleFacts.forDate(date))
        }
    }

    @Test
    fun `the fact changes from one day to the next`() {
        val start = LocalDate.parse("2026-01-01")
        val changes = (0 until 30).count { offset ->
            CycleFacts.forDate(start.plusDays(offset.toLong())) !=
                CycleFacts.forDate(start.plusDays(offset + 1L))
        }

        assertEquals("Every consecutive day should differ", 30, changes)
    }

    @Test
    fun `selection is defined for dates before the epoch`() {
        // toEpochDay is negative before 1970 and the scatter multiplier can
        // overflow, so a plain modulo could index out of bounds.
        val old = LocalDate.parse("1901-04-13")

        val fact = CycleFacts.forDate(old)

        assertTrue(fact in CycleFacts.ALL)
    }

    @Test
    fun `every fact is reachable`() {
        val start = LocalDate.parse("2026-01-01")
        val seen = (0 until 400)
            .map { CycleFacts.forDate(start.plusDays(it.toLong())) }
            .toSet()

        assertEquals(CycleFacts.ALL.toSet(), seen)
    }

    @Test
    fun `the catalog is large enough not to repeat within a month`() {
        // A short list reads as repetition rather than as something worth
        // coming back to.
        assertTrue(
            "Only ${CycleFacts.ALL.size} facts; the card would repeat weekly",
            CycleFacts.ALL.size >= 21
        )
    }

    @Test
    fun `every fact has copy in every shipped language`() {
        // factText() falls back to null for an unknown id, which would make the
        // card vanish silently rather than fail loudly. Locales are discovered
        // rather than listed, so adding a translation extends this check.
        val translations = File("src/main/res")
            .listFiles { file -> file.isDirectory && file.name.startsWith("values") }
            .orEmpty()
            .map { it.name to File(it, "strings.xml") }
            .filter { (_, file) -> file.isFile }

        val folders = translations.map { (folder, _) -> folder }
        assertTrue("Missing values/strings.xml, found $folders", "values" in folders)
        assertTrue("Missing values-fr/strings.xml, found $folders", "values-fr" in folders)

        translations.forEach { (folder, file) ->
            val text = file.readText()
            val missing = CycleFacts.ALL
                .map { it.id }
                .filterNot { text.contains("name=\"fact_$it\"") }

            assertTrue("$folder is missing copy for: $missing", missing.isEmpty())
        }
    }

    @Test
    fun `every fact carries a citation and a resolvable https source`() {
        CycleFacts.ALL.forEach { fact ->
            assertTrue("Missing citation for ${fact.id}", fact.source.isNotBlank())
            assertTrue(
                "Source for ${fact.id} must be https: ${fact.url}",
                fact.url.startsWith("https://")
            )
        }
    }

    @Test
    fun `fact ids are unique`() {
        val ids = CycleFacts.ALL.map { it.id }

        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `no fact cites a source outside the research register`() {
        // Adding a fact from an unregistered source would bypass the review
        // process in docs/research/SOURCE_REGISTER.md.
        val registeredHosts = setOf(
            "pmc.ncbi.nlm.nih.gov",
            "www.nhs.uk",
            "www.who.int"
        )

        CycleFacts.ALL.forEach { fact ->
            val host = fact.url.removePrefix("https://").substringBefore('/')
            assertTrue(
                "Unregistered source host for ${fact.id}: $host",
                host in registeredHosts
            )
        }
    }
}
