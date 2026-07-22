package fr.luteal.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuoSharingPreferencesTest {
    @Test
    fun `sensitive daily observations are private by default`() {
        val preferences = DuoSharingPreferences()

        assertTrue(preferences.shareCycleDay)
        assertFalse(preferences.sharePeriodEstimate)
        assertFalse(preferences.shareMood)
        assertFalse(preferences.shareEnergy)
    }
}
