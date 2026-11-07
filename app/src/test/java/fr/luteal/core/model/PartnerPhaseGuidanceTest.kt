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
    fun `day 1 resolves to menstrual recorded while days 2 to 7 remain conservative without bleeding logs`() {
        val today = LocalDate.parse("2026-08-19")
        val day1 = PartnerPhaseResolver.resolve(DuoProjection(cycleDay = 1), today)
        assertEquals(
            CurrentCyclePhase.Available(CyclePhase.MENSTRUAL, PhaseCertainty.RECORDED),
            day1
        )

        (2..7).forEach { day ->
            val result = PartnerPhaseResolver.resolve(DuoProjection(cycleDay = day), today)
            assertEquals(
                "Day $day without bleeding details must stay conservative",
                CurrentCyclePhase.Indeterminate(PhaseIndeterminateReason.EARLY_CYCLE_WITHOUT_BLEEDING_DETAIL),
                result
            )
        }
    }

    @Test
    fun `resolver stays conservative and never assumes a 28-day cycle from day alone`() {
        val today = LocalDate.parse("2026-08-19")
        val dayOnly = PartnerPhaseResolver.resolve(DuoProjection(cycleDay = 18), today)
        assertEquals(
            PhaseIndeterminateReason.ESTIMATE_NOT_SHARED,
            (dayOnly as CurrentCyclePhase.Indeterminate).reason
        )

        val missingCentral = PartnerPhaseResolver.resolve(
            DuoProjection(
                cycleDay = 22,
                periodEstimate = SharedEstimate("2026-08-17", "2026-09-04")
            ),
            today
        )
        assertTrue(missingCentral is CurrentCyclePhase.Indeterminate)

        val empty = PartnerPhaseResolver.resolve(null, today)
        assertEquals(
            PhaseIndeterminateReason.NO_CURRENT_CYCLE,
            (empty as CurrentCyclePhase.Indeterminate).reason
        )
    }

    @Test
    fun `every capsule has a registered citation and localized copy`() {
        val sourceRegister = File("../docs/research/SOURCE_REGISTER.md").readText()
        val translations = File("src/main/res")
            .listFiles { file -> file.isDirectory && file.name.startsWith("values") }
            .orEmpty()
            .map { File(it, "strings.xml") }
            .filter(File::isFile)

        CyclePhase.entries.forEach { phase ->
            val capsule = PartnerPhaseCapsules.forPhase(phase)
            assertTrue(capsule.url.startsWith("https://"))
            assertTrue("Unregistered source for capsule $phase", capsule.url in sourceRegister)
            val key = "duo_capsule_${phase.name.lowercase()}_desc"
            translations.forEach { file ->
                assertTrue(
                    "${file.parentFile?.name} is missing $key",
                    file.readText().contains("name=\"$key\"")
                )
            }
        }
    }

    @Test
    fun `partner phase matches the tracker calculator`() {
        val today = LocalDate.parse("2026-08-19")
        val start = LocalDate.parse("2026-07-29")
        val estimate = CycleEstimate(
            earliestDate = LocalDate.parse("2026-08-17"),
            centralDate = LocalDate.parse("2026-08-26"),
            latestDate = LocalDate.parse("2026-09-04"),
            cycleCount = 6,
            variabilityDays = 4
        )
        val tracker = CurrentCyclePhaseCalculator.evaluate(
            today = today,
            currentCycle = Cycle(id = "current", startDate = start),
            todayEntry = null,
            estimateResult = CycleEstimateResult.Available(estimate)
        )
        val partner = PartnerPhaseResolver.resolve(
            DuoProjection(
                cycleDay = 22,
                periodEstimate = SharedEstimate(
                    windowStart = estimate.earliestDate.toString(),
                    windowEnd = estimate.latestDate.toString(),
                    centralDate = estimate.centralDate.toString(),
                    cycleCount = estimate.cycleCount,
                    variabilityDays = estimate.variabilityDays
                )
            ),
            today
        )
        assertEquals(tracker, partner)
        assertEquals(
            CurrentCyclePhase.Available(CyclePhase.LUTEAL, PhaseCertainty.ESTIMATED),
            partner
        )
    }

    @Test
    fun `follicular is reachable on the partner path`() {
        val today = LocalDate.parse("2026-08-08")
        val partner = PartnerPhaseResolver.resolve(
            DuoProjection(
                cycleDay = 9,
                periodEstimate = SharedEstimate(
                    windowStart = "2026-08-21",
                    windowEnd = "2026-08-31",
                    centralDate = "2026-08-26",
                    cycleCount = 6,
                    variabilityDays = 4
                )
            ),
            today
        )
        assertEquals(
            CurrentCyclePhase.Available(CyclePhase.FOLLICULAR, PhaseCertainty.ESTIMATED),
            partner
        )
    }

    @Test
    fun `partner tip adapts to declared tracker context`() {
        val date = LocalDate.parse("2026-08-19")

        val endoPartnerTip = PartnerPhaseTips.forDate(
            phase = CyclePhase.MENSTRUAL,
            date = date,
            declaredContexts = setOf(TrackingContext.ENDOMETRIOSIS)
        )
        assertEquals("partner_menstrual_endo_support", endoPartnerTip.id)

        val pmddPartnerTip = PartnerPhaseTips.forDate(
            phase = CyclePhase.LUTEAL,
            date = date,
            declaredContexts = setOf(TrackingContext.PMDD)
        )
        assertEquals("partner_luteal_pmdd_space", pmddPartnerTip.id)
    }
}
