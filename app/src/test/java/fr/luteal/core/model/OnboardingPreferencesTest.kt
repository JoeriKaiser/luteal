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
}
