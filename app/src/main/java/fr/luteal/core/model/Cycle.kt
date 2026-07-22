package fr.luteal.core.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class Cycle(
    val id: String,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val averageLengthDays: Int = 28,
    val lutealPhaseLengthDays: Int = 14,
    val periodDays: List<PeriodDay> = emptyList()
) {
    val isCurrent: Boolean
        get() = endDate == null

    val lengthInDays: Int
        get() = if (endDate != null) {
            ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1
        } else {
            ChronoUnit.DAYS.between(startDate, LocalDate.now()).toInt() + 1
        }

}
