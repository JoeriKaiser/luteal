package fr.luteal.core.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class LocalizedDateFormatterTest {

    private val date = LocalDate.of(2026, 8, 15)

    @Test
    fun formatsFullDateInFrench() {
        assertEquals("Samedi 15 août 2026", LocalizedDateFormatter.formatFullDate(date, Locale.FRENCH))
    }

    @Test
    fun formatsFullDateInEnglish() {
        assertEquals("Saturday 15 August 2026", LocalizedDateFormatter.formatFullDate(date, Locale.ENGLISH))
    }

    @Test
    fun formatsShortDatePerLocale() {
        assertEquals("15 août", LocalizedDateFormatter.formatShortDate(date, Locale.FRENCH))
        assertEquals("15 Aug", LocalizedDateFormatter.formatShortDate(date, Locale.ENGLISH))
    }

    @Test
    fun formatsMonthYearPerLocale() {
        assertEquals("Août 2026", LocalizedDateFormatter.formatMonthYear(date, Locale.FRENCH))
        assertEquals("August 2026", LocalizedDateFormatter.formatMonthYear(date, Locale.ENGLISH))
    }

    @Test
    fun formatsDayNumberPerLocale() {
        assertEquals("15", LocalizedDateFormatter.formatDayNumber(date, Locale.FRENCH))
        assertEquals("15", LocalizedDateFormatter.formatDayNumber(date, Locale.ENGLISH))
    }
}
