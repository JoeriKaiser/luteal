package fr.luteal.core.model

import fr.luteal.core.data.datastore.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingPreferencesTest {

    @Test
    fun `default user preferences has onboarding uncompleted`() {
        val prefs = UserPreferences()
        assertFalse(prefs.hasCompletedOnboarding)
        assertEquals("PRIMARY_TRACKER", prefs.userRole)
        assertFalse(prefs.trackPms)
        assertFalse(prefs.trackPmdd)
        assertFalse(prefs.trackEndometriosis)
        assertFalse(prefs.trackPcos)
    }

    @Test
    fun `completed onboarding preferences carries selected role and focus`() {
        val prefs = UserPreferences(
            hasCompletedOnboarding = true,
            userRole = UserRole.PARTNER_VIEWER.name,
            trackPms = true,
            trackEndometriosis = true
        )
        assertTrue(prefs.hasCompletedOnboarding)
        assertEquals("PARTNER_VIEWER", prefs.userRole)
        assertTrue(prefs.trackPms)
        assertTrue(prefs.trackEndometriosis)
        assertFalse(prefs.trackPmdd)
        assertFalse(prefs.trackPcos)
    }

    @Test
    fun `declaring only observation contexts never reaches estimation`() {
        // Endometriosis in particular must not widen an estimate: the
        // meta-analytic association runs from short cycles to endometriosis
        // risk, not the other way, so widening on it would invert the
        // inference. See docs/research/CONDITION_CYCLE_IMPACTS.md Finding 2.
        val prefs = UserPreferences(
            trackPms = true,
            trackPmdd = true,
            trackEndometriosis = true
        )

        assertFalse(prefs.hasTimingContext)
        assertEquals(
            setOf(
                TrackingContext.PMS,
                TrackingContext.PMDD,
                TrackingContext.ENDOMETRIOSIS
            ),
            prefs.declaredContexts
        )
    }

    @Test
    fun `each timing context on its own reaches estimation`() {
        TrackingContext.entries
            .filter { it.group == ContextGroup.TIMING }
            .forEach { context ->
                val prefs = when (context) {
                    TrackingContext.PCOS -> UserPreferences(trackPcos = true)
                    TrackingContext.PERIMENOPAUSE -> UserPreferences(trackPerimenopause = true)
                    TrackingContext.THYROID -> UserPreferences(trackThyroid = true)
                    else -> error("Not a timing context: $context")
                }

                assertTrue(
                    "$context is a TIMING context and must widen estimates",
                    prefs.hasTimingContext
                )
            }
    }

    @Test
    fun `age band round-trips through preferences`() {
        val prefs = UserPreferences(ageBand = AgeBand.AGE_45_49.id)

        assertEquals(AgeBand.AGE_45_49, AgeBand.fromId(prefs.ageBand))
        assertEquals(null, AgeBand.fromId(UserPreferences().ageBand))
    }
}
