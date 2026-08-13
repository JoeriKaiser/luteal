package fr.luteal.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

data class CalendarDayProjection(
    val date: LocalDate,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val isCycleStart: Boolean,
    val bleedingIntensity: BleedingIntensity?,
    val hasObservations: Boolean,
    val isEstimatedPeriodWindow: Boolean,
    val entry: DailyEntry?
) {
    val hasBleeding: Boolean
        get() = bleedingIntensity != null && bleedingIntensity != BleedingIntensity.NONE
}

data class MonthCalendarProjection(
    val yearMonth: YearMonth,
    val weeks: List<List<CalendarDayProjection>>,
    val recordedPeriodDaysCount: Int,
    val hasEstimatedPeriodInMonth: Boolean
)

object MonthCalendarProjectionCalculator {

    fun project(
        targetMonth: YearMonth,
        today: LocalDate,
        cycles: List<Cycle>,
        entries: List<DailyEntry>,
        estimateResult: CycleEstimateResult,
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY
    ): MonthCalendarProjection {
        val entriesByDate = entries.associateBy(DailyEntry::date)
        val cycleStartDates = cycles.map(Cycle::startDate).toSet()

        val estimateRange: ClosedRange<LocalDate>? = when (estimateResult) {
            is CycleEstimateResult.Available -> {
                val est = estimateResult.estimate
                est.earliestDate..est.latestDate
            }
            else -> null
        }

        val firstDayOfMonth = targetMonth.atDay(1)
        val lastDayOfMonth = targetMonth.atEndOfMonth()

        val gridStart = firstDayOfMonth.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        val lastDayOfWeek = firstDayOfWeek.minus(1)
        val gridEnd = lastDayOfMonth.with(TemporalAdjusters.nextOrSame(lastDayOfWeek))

        val days = mutableListOf<CalendarDayProjection>()
        var recordedPeriodCount = 0
        var hasEstimateInMonth = false

        var cur = gridStart
        while (!cur.isAfter(gridEnd)) {
            val isCurrentMonth = YearMonth.from(cur) == targetMonth
            val isToday = cur == today
            val entry = entriesByDate[cur]
            val isCycleStart = cur in cycleStartDates

            val bleeding = entry?.bleedingIntensity
            val hasObservations = entry?.let {
                it.painLevel != null ||
                    it.moodLevel != null ||
                    it.energyLevel != null ||
                    it.symptomIds.isNotEmpty() ||
                    it.notes.isNotBlank()
            } ?: false

            val hasPeriod = bleeding != null && bleeding != BleedingIntensity.NONE
            if (isCurrentMonth && hasPeriod) {
                recordedPeriodCount++
            }

            // Estimate applies only to future/today dates that don't have actual recorded period
            val isEstimated = !hasPeriod &&
                estimateRange != null &&
                cur in estimateRange

            if (isCurrentMonth && isEstimated) {
                hasEstimateInMonth = true
            }

            days.add(
                CalendarDayProjection(
                    date = cur,
                    isCurrentMonth = isCurrentMonth,
                    isToday = isToday,
                    isCycleStart = isCycleStart,
                    bleedingIntensity = bleeding,
                    hasObservations = hasObservations,
                    isEstimatedPeriodWindow = isEstimated,
                    entry = entry
                )
            )

            cur = cur.plusDays(1)
        }

        val weeks = days.chunked(7)

        return MonthCalendarProjection(
            yearMonth = targetMonth,
            weeks = weeks,
            recordedPeriodDaysCount = recordedPeriodCount,
            hasEstimatedPeriodInMonth = hasEstimateInMonth
        )
    }
}
