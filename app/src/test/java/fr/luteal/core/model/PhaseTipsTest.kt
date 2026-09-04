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

    @Test
    fun `declared context selects targeted tips during relevant phase`() {
        val date = LocalDate.parse("2026-07-26")

        // Endometriosis in menstrual phase
        val endoTip = PhaseTips.forDate(
            phase = CyclePhase.MENSTRUAL,
            date = date,
            declaredContexts = setOf(TrackingContext.ENDOMETRIOSIS)
        )
        assertEquals(TrackingContext.ENDOMETRIOSIS, endoTip.targetContext)

        // PMDD in luteal phase
        val pmddTip = PhaseTips.forDate(
            phase = CyclePhase.LUTEAL,
            date = date,
            declaredContexts = setOf(TrackingContext.PMDD)
        )
        assertEquals(TrackingContext.PMDD, pmddTip.targetContext)

        // PCOS in follicular phase
        val pcosTip = PhaseTips.forDate(
            phase = CyclePhase.FOLLICULAR,
            date = date,
            declaredContexts = setOf(TrackingContext.PCOS)
        )
        assertEquals(TrackingContext.PCOS, pcosTip.targetContext)
    }

    @Test
    fun `recent symptoms boost specific tip matching`() {
        val date = LocalDate.parse("2026-07-26")

        val tip = PhaseTips.forDate(
            phase = CyclePhase.MENSTRUAL,
            date = date,
            declaredContexts = setOf(TrackingContext.ENDOMETRIOSIS),
            recentSymptomIds = setOf("fatigue")
        )
        assertEquals("menstrual_endo_fatigue_pacing", tip.id)
    }

    @Test
    fun `recent nausea or digestive changes in luteal phase boosts digestive comfort tip`() {
        val date = LocalDate.parse("2026-07-26")

        val tip = PhaseTips.forDate(
            phase = CyclePhase.LUTEAL,
            date = date,
            recentSymptomIds = setOf("nausea")
        )
        assertEquals("luteal_digestive_comfort", tip.id)
    }

    @Test
    fun `empty contexts never select condition-specific tips`() {
        val baseDate = LocalDate.parse("2026-07-01")
        (0..60).forEach { dayOffset ->
            val date = baseDate.plusDays(dayOffset.toLong())
            CyclePhase.entries.forEach { phase ->
                val tip = PhaseTips.forDate(phase, date, declaredContexts = emptySet())
                assertTrue(
                    "Tip ${tip.id} has context ${tip.targetContext} but was selected with empty contexts",
                    tip.targetContext == null
                )
            }
        }
    }
}
