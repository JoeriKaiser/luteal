package fr.luteal.core.common

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object FrenchDateFormatter {
    private val fullDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH)
    private val shortDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH)
    private val monthYearFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH)
    private val dayNumberFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d", Locale.FRENCH)

    fun formatFullDate(date: LocalDate): String {
        val formatted = date.format(fullDateFormatter)
        return formatted.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.FRENCH) else it.toString() }
        }
    }

    fun formatShortDate(date: LocalDate): String {
        return date.format(shortDateFormatter)
    }

    fun formatMonthYear(date: LocalDate): String {
        val formatted = date.format(monthYearFormatter)
        return formatted.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.FRENCH) else it.toString() }
    }

    fun formatDayNumber(date: LocalDate): String {
        return date.format(dayNumberFormatter)
    }
}
