package fr.luteal.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class VisibleDestinationsTest {

    @Test
    fun duoTabIsHiddenUntilDuoIsEnabled() {
        assertEquals(
            listOf(LutealDestination.TODAY, LutealDestination.JOURNAL, LutealDestination.SETTINGS),
            visibleDestinations(isDuoEnabled = false)
        )
    }

    @Test
    fun enabledDuoKeepsTheOriginalOrder() {
        assertEquals(LutealDestination.entries.toList(), visibleDestinations(isDuoEnabled = true))
    }
}
