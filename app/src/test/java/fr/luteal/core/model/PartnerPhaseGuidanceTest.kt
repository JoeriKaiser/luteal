package fr.luteal.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class PartnerPhaseGuidanceTest {
    @Test
    fun `every phase has at least two partner tips`() {
        CyclePhase.entries.forEach { phase ->
            assertTrue(PartnerPhaseTips.ALL.count { it.phase == phase } >= 2)
        }
    }

    @Test
    fun `selection is stable`() {
        val date = LocalDate.parse("2026-08-19")
        CyclePhase.entries.forEach { phase ->
            assertEquals(PartnerPhaseTips.forDate(phase, date), PartnerPhaseTips.forDate(phase, date))
        }
    }

    @Test
    fun `every tip has localized copy and a registered citation`() {
        val sourceRegister = File("../docs/research/SOURCE_REGISTER.md").readText()
        val translations = File("src/main/res")
            .listFiles { file -> file.isDirectory && file.name.startsWith("values") }
            .orEmpty()
            .map { File(it, "strings.xml") }
            .filter(File::isFile)

        PartnerPhaseTips.ALL.forEach { tip ->
            assertTrue(tip.url.startsWith("https://"))
            assertTrue("Unregistered source for ${tip.id}", tip.url in sourceRegister)
            translations.forEach { file ->
                assertTrue(
                    "${file.parentFile?.name} is missing partner_tip_${tip.id}",
                    file.readText().contains("name=\"partner_tip_${tip.id}\"")
                )
            }
        }
    }

    @Test
    fun `resolver stays conservative and never assumes a 28-day cycle from day alone`() {
        val today = LocalDate.parse("2026-08-19")
        val menstrual = PartnerPhaseResolver.resolve(DuoProjection(cycleDay = 3), today)
        assertEquals(CyclePhase.MENSTRUAL, (menstrual as CurrentCyclePhase.Available).phase)

        val dayOnly = PartnerPhaseResolver.resolve(DuoProjection(cycleDay = 18), today)
        assertTrue(dayOnly is CurrentCyclePhase.Indeterminate)

        val luteal = PartnerPhaseResolver.resolve(
            DuoProjection(periodEstimate = SharedEstimate("2026-08-26", "2026-08-30")),
            today
        )
        assertEquals(CyclePhase.LUTEAL, (luteal as CurrentCyclePhase.Available).phase)

        val empty = PartnerPhaseResolver.resolve(null, today)
        assertTrue(empty is CurrentCyclePhase.Indeterminate)
    }
}
