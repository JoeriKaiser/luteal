package fr.luteal.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DailyEntryTest {
    @Test
    fun `empty entry has no observations`() {
        assertFalse(DailyEntry(date = LocalDate.parse("2025-01-01")).hasObservations)
    }

    @Test
    fun `notes count as a recorded observation`() {
        val entry = DailyEntry(
            date = LocalDate.parse("2025-01-01"),
            notes = "Contexte personnel"
        )

        assertTrue(entry.hasObservations)
    }

    @Test
    fun `tracking scales reject values outside one to five`() {
        assertThrows(IllegalArgumentException::class.java) {
            DailyEntry(
                date = LocalDate.parse("2025-01-01"),
                painLevel = 6
            )
        }
    }
}
