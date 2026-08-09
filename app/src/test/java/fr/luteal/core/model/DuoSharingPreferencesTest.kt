package fr.luteal.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuoSharingPreferencesTest {
    @Test
    fun `nothing is shared by default - sharing is opt-in`() {
        val preferences = DuoSharingPreferences()

        // Grants mirror the server's duoView and are only ever set by an
        // explicit toggle; a fresh install shares nothing.
        assertFalse(preferences.shareCycleDay)
        assertFalse(preferences.sharePeriodEstimate)
        assertFalse(preferences.shareMood)
        assertFalse(preferences.shareEnergy)
        assertFalse(preferences.shareSupportRequests)
    }
}
