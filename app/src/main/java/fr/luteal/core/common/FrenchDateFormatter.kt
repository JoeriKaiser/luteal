package fr.luteal.core.common

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object FrenchDateFormatter {
    private val fullDateFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH)
    private val shortDateFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH)
    private val monthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH)
    private val dayNumberFormatter = DateTimeFormatter.ofPattern("d", Locale.FRENCH)

    fun formatFullDate(date: LocalDate): String =
        date.format(fullDateFormatter).sentenceCase()

    fun formatShortDate(date: LocalDate): String =
        date.format(shortDateFormatter)

    fun formatMonthYear(date: LocalDate): String =
        date.format(monthYearFormatter).sentenceCase()

    fun formatDayNumber(date: LocalDate): String =
        date.format(dayNumberFormatter)

    private fun String.sentenceCase(): String = replaceFirstChar { character ->
        if (character.isLowerCase()) character.titlecase(Locale.FRENCH) else character.toString()
    }
}
