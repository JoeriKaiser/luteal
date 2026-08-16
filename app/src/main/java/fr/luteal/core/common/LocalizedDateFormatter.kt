package fr.luteal.core.common

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Locale-aware date formatting for the resolved UI locale.
 *
 * Callers pass the locale resolved from the Android configuration (Compose:
 * `LocalConfiguration.current.locales[0]`; widgets: `context.resources
 * .configuration.locales[0]`), so dates render in the user's language instead
 * of a hardcoded French one. French sentence-casing is locale-relative: month
 * names that are lowercase in the given locale get an initial capital; locales
 * whose names are already capitalized are left untouched.
 */
object LocalizedDateFormatter {
    private val fullDateFormatters = ConcurrentHashMap<Locale, DateTimeFormatter>()
    private val shortDateFormatters = ConcurrentHashMap<Locale, DateTimeFormatter>()
    private val monthYearFormatters = ConcurrentHashMap<Locale, DateTimeFormatter>()
    private val dayNumberFormatters = ConcurrentHashMap<Locale, DateTimeFormatter>()

    fun formatFullDate(date: LocalDate, locale: Locale): String =
        date.format(formatter(fullDateFormatters, "EEEE d MMMM yyyy", locale)).sentenceCase(locale)

    fun formatShortDate(date: LocalDate, locale: Locale): String =
        date.format(formatter(shortDateFormatters, "d MMM", locale))

    fun formatMonthYear(date: LocalDate, locale: Locale): String =
        date.format(formatter(monthYearFormatters, "MMMM yyyy", locale)).sentenceCase(locale)

    fun formatDayNumber(date: LocalDate, locale: Locale): String =
        date.format(formatter(dayNumberFormatters, "d", locale))

    private fun formatter(
        cache: ConcurrentHashMap<Locale, DateTimeFormatter>,
        pattern: String,
        locale: Locale
    ): DateTimeFormatter = cache.computeIfAbsent(locale) { DateTimeFormatter.ofPattern(pattern, it) }

    private fun String.sentenceCase(locale: Locale): String = replaceFirstChar { character ->
        if (character.isLowerCase()) character.titlecase(locale) else character.toString()
    }
}
