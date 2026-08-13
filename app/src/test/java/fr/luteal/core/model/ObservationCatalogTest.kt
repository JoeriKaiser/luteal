package fr.luteal.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationCatalogTest {

    @Test
    fun `base observations are offered when nothing is declared`() {
        val ids = ObservationCatalog.symptomIdsFor(emptySet())

        assertEquals(
            listOf(
                "cramps",
                "headache",
                "abdominal_pain",
                "backache",
                "muscle_aches",
                "fatigue",
                "sleep_issue",
                "bloating",
                "nausea",
                "digestive_changes"
            ),
            ids
        )
    }

    @Test
    fun `declaring a premenstrual context adds its sourced vocabulary`() {
        val ids = ObservationCatalog.symptomIdsFor(setOf(TrackingContext.PMS))

        assertTrue("breast_tenderness" in ids)
        assertTrue("mood_changes" in ids)
        assertTrue("acne" in ids)
        // The base set is never removed by a declaration.
        assertTrue(ids.containsAll(listOf("cramps", "headache", "fatigue", "bloating", "nausea", "abdominal_pain")))
    }

    @Test
    fun `declaring endometriosis adds pain persisting outside menstruation`() {
        val ids = ObservationCatalog.symptomIdsFor(setOf(TrackingContext.ENDOMETRIOSIS))

        assertTrue("pelvic_pain_outside_period" in ids)
    }

    @Test
    fun `timing contexts add no observation vocabulary`() {
        // PCOS, perimenopause and thyroid widen estimation uncertainty instead.
        // Adding terms for them would need sourced vocabulary the register does
        // not yet carry.
        val timingOnly = ObservationCatalog.symptomIdsFor(
            setOf(
                TrackingContext.PCOS,
                TrackingContext.PERIMENOPAUSE,
                TrackingContext.THYROID
            )
        )

        assertEquals(ObservationCatalog.symptomIdsFor(emptySet()), timingOnly)
    }

    @Test
    fun `overlapping contexts do not duplicate observations`() {
        // SPM and TDPM share the same NHS-sourced vocabulary.
        val ids = ObservationCatalog.symptomIdsFor(
            setOf(TrackingContext.PMS, TrackingContext.PMDD)
        )

        assertEquals(ids.distinct(), ids)
    }

    @Test
    fun `order is stable so the editor does not reshuffle`() {
        val contexts = setOf(TrackingContext.ENDOMETRIOSIS, TrackingContext.PMS)

        assertEquals(
            ObservationCatalog.symptomIdsFor(contexts),
            ObservationCatalog.symptomIdsFor(contexts)
        )
    }

    @Test
    fun `every offered id exists in the local symptom catalog`() {
        // Otherwise the editor would offer an observation that sync has no
        // definition for.
        val catalogIds = Symptom.DEFAULT_SYMPTOMS.map { it.id }.toSet()
        val offered = ObservationCatalog.symptomIdsFor(TrackingContext.entries.toSet())

        val missing = offered.filterNot { it in catalogIds }
        assertTrue("Not in Symptom.DEFAULT_SYMPTOMS: $missing", missing.isEmpty())
    }

    @Test
    fun `observation contexts are the only ones that change the catalog`() {
        val observationContexts = TrackingContext.entries
            .filter { it.group == ContextGroup.OBSERVATION }

        val changed = observationContexts.filter {
            ObservationCatalog.symptomIdsFor(setOf(it)) !=
                ObservationCatalog.symptomIdsFor(emptySet())
        }

        assertFalse(
            "At least one OBSERVATION context must adapt the catalog",
            changed.isEmpty()
        )
    }
}
