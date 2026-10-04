package fr.luteal.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
data class CalendarDayProjection(
    val date: LocalDate,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val isCycleStart: Boolean,
    val bleedingIntensity: BleedingIntensity?,
    val hasObservations: Boolean,
    val isEstimatedPeriodWindow: Boolean,
    val isEstimatedPeriodTarget: Boolean = false,
    val entry: DailyEntry?,
    val cycleDayNumber: Int? = null,
    val cyclePhase: CyclePhase? = null,
    val phaseCertainty: PhaseCertainty? = null,
    val isSpottingOnly: Boolean = false,
    val hasBiomarkers: Boolean = false,
    val bbtCelsius: Double? = null,
    val hasCervicalFluid: Boolean = false,
    val hasPositiveLh: Boolean = false,
    val biomarker: BiomarkerObservation? = null
) {
    val hasBleeding: Boolean
        get() = bleedingIntensity != null && bleedingIntensity != BleedingIntensity.NONE

    val hasPeriodFlow: Boolean
        get() = bleedingIntensity?.isPeriodFlow() == true
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
        biomarkers: List<BiomarkerObservation> = emptyList(),
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY
    ): MonthCalendarProjection {
        val entriesByDate = entries.associateBy(DailyEntry::date)
        val biomarkersByDate = biomarkers.associateBy(BiomarkerObservation::date)
        val cycleStartDates = cycles.map(Cycle::startDate).toSet()
        val sortedCycles = cycles.sortedBy(Cycle::startDate)
        val estimate = (estimateResult as? CycleEstimateResult.Available)?.estimate
        val centralDate = estimate?.centralDate
        val estimateRange: ClosedRange<LocalDate>? = estimate?.let {
            it.earliestDate..it.latestDate
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
            val isFutureOrToday = !cur.isBefore(today)
            val isEstimatedTarget = !hasPeriod && isFutureOrToday && estimateRange != null && cur in estimateRange && cur == centralDate
            val isEstimatedWindow = !hasPeriod && isFutureOrToday && !isEstimatedTarget && estimateRange != null && cur in estimateRange

            if (isCurrentMonth && (isEstimatedTarget || isEstimatedWindow)) {
                hasEstimateInMonth = true
            }

            var cycleDayNumber: Int? = null
            var cyclePhase: CyclePhase? = null
            var phaseCertainty: PhaseCertainty? = null
            var isSpottingOnly = bleeding == BleedingIntensity.SPOTTING

            val cycleIndex = sortedCycles.indexOfLast { !it.startDate.isAfter(cur) }
            if (cycleIndex != -1) {
                val cycle = sortedCycles[cycleIndex]
                val isOutsideCycle = cycle.endDate != null && cur.isAfter(cycle.endDate)
                if (!isOutsideCycle) {
                    val dayNum = ChronoUnit.DAYS.between(cycle.startDate, cur).toInt() + 1
                    cycleDayNumber = dayNum

                    val hasFlow = bleeding?.isPeriodFlow() == true ||
                        cycle.periodDays.any { it.date == cur } ||
                        cur == cycle.startDate
                    isSpottingOnly = !hasFlow && bleeding == BleedingIntensity.SPOTTING

                    val nextCycle = sortedCycles.getOrNull(cycleIndex + 1)
                    val isCompletedCycle = cycle.endDate != null || nextCycle != null

                    if (hasFlow) {
                        cyclePhase = CyclePhase.MENSTRUAL
                        phaseCertainty = PhaseCertainty.RECORDED
                    } else if (isCompletedCycle) {
                        val completedLength = if (cycle.endDate != null) {
                            ChronoUnit.DAYS.between(cycle.startDate, cycle.endDate).toInt() + 1
                        } else {
                            ChronoUnit.DAYS.between(cycle.startDate, nextCycle!!.startDate).toInt()
                        }
                        val lutealStart = maxOf(6, completedLength - 14)
                        val ovulatoryStart = maxOf(5, lutealStart - 2)
                        cyclePhase = when {
                            dayNum <= 5 -> CyclePhase.MENSTRUAL
                            dayNum in ovulatoryStart until lutealStart -> CyclePhase.OVULATORY
                            dayNum >= lutealStart -> CyclePhase.LUTEAL
                            else -> CyclePhase.FOLLICULAR
                        }
                        phaseCertainty = PhaseCertainty.ESTIMATED
                    } else {
                        // Ongoing cycle (cycle.endDate == null)
                        if (!cur.isAfter(today)) {
                            if (dayNum <= 5) {
                                cyclePhase = CyclePhase.MENSTRUAL
                                phaseCertainty = PhaseCertainty.RECORDED
                            } else if (estimate != null) {
                                val ovCentral = estimate.centralDate.minusDays(13)
                                val ovStart = ovCentral.minusDays(2)
                                val ovEnd = ovCentral.plusDays(2)
                                when {
                                    cur.isBefore(ovStart) -> {
                                        cyclePhase = CyclePhase.FOLLICULAR
                                        phaseCertainty = PhaseCertainty.ESTIMATED
                                    }
                                    cur.isAfter(ovEnd) -> {
                                        cyclePhase = CyclePhase.LUTEAL
                                        phaseCertainty = PhaseCertainty.ESTIMATED
                                    }
                                    else -> {
                                        cyclePhase = CyclePhase.OVULATORY
                                        phaseCertainty = PhaseCertainty.ESTIMATED
                                    }
                                }
                            } else {
                                cyclePhase = when {
                                    dayNum < 12 -> CyclePhase.FOLLICULAR
                                    dayNum in 12..16 -> CyclePhase.OVULATORY
                                    else -> CyclePhase.LUTEAL
                                }
                                phaseCertainty = PhaseCertainty.ESTIMATED
                            }
                        } else {
                            // cur.isAfter(today)
                            if (isEstimatedTarget || isEstimatedWindow) {
                                cyclePhase = CyclePhase.MENSTRUAL
                                phaseCertainty = PhaseCertainty.ESTIMATED
                            } else if (estimate != null && cur.isBefore(estimate.earliestDate)) {
                                cyclePhase = CyclePhase.LUTEAL
                                phaseCertainty = PhaseCertainty.ESTIMATED
                            }
                        }
                    }
                }
            }

            val bm = biomarkersByDate[cur]
            val hasBiomarkers = bm != null && (bm.bbt != null || bm.cervicalFluid != null || bm.rapidTests != null)
            val bbtCelsius = bm?.bbt?.valueCelsius
            val hasCervicalFluid = bm?.cervicalFluid != null
            val hasPositiveLh = bm?.rapidTests?.lhTest == LhTestResult.PEAK_POSITIVE

            days.add(
                CalendarDayProjection(
                    date = cur,
                    isCurrentMonth = isCurrentMonth,
                    isToday = isToday,
                    isCycleStart = isCycleStart,
                    bleedingIntensity = bleeding,
                    hasObservations = hasObservations,
                    isEstimatedPeriodWindow = isEstimatedWindow,
                    isEstimatedPeriodTarget = isEstimatedTarget,
                    entry = entry,
                    cycleDayNumber = cycleDayNumber,
                    cyclePhase = cyclePhase,
                    phaseCertainty = phaseCertainty,
                    isSpottingOnly = isSpottingOnly,
                    hasBiomarkers = hasBiomarkers,
                    bbtCelsius = bbtCelsius,
                    hasCervicalFluid = hasCervicalFluid,
                    hasPositiveLh = hasPositiveLh,
                    biomarker = bm
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
