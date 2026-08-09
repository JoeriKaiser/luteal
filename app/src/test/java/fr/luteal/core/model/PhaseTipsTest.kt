package fr.luteal.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class PhaseTipsTest {
    @Test
    fun `every phase has multiple tips`() {
        CyclePhase.entries.forEach { phase ->
            assertTrue(PhaseTips.ALL.count { it.phase == phase } >= 3)
        }
    }

    @Test
    fun `selection is stable and remains in its phase`() {
        val date = LocalDate.parse("2026-07-26")

        CyclePhase.entries.forEach { phase ->
            val selected = PhaseTips.forDate(phase, date)
            assertEquals(selected, PhaseTips.forDate(phase, date))
            assertEquals(phase, selected.phase)
        }
    }

    @Test
    fun `every tip has localized copy and a registered https citation`() {
        val sourceRegister = File("../docs/research/SOURCE_REGISTER.md").readText()
        val translations = File("src/main/res")
            .listFiles { file -> file.isDirectory && file.name.startsWith("values") }
            .orEmpty()
            .map { File(it, "strings.xml") }
            .filter(File::isFile)

        PhaseTips.ALL.forEach { tip ->
            assertTrue(tip.source.isNotBlank())
            assertTrue(tip.url.startsWith("https://"))
            assertTrue("Unregistered source for ${tip.id}", tip.url in sourceRegister)
            translations.forEach { file ->
                assertTrue(
                    "${file.parentFile?.name} is missing phase_tip_${tip.id}",
                    file.readText().contains("name=\"phase_tip_${tip.id}\"")
                )
            }
        }
    }

    @Test
    fun `tip ids are unique`() {
        val ids = PhaseTips.ALL.map(PhaseTip::id)
        assertEquals(ids.size, ids.distinct().size)
    }
}
