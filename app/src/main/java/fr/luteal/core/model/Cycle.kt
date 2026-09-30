package fr.luteal.core.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class CycleExclusionReason {
    ILLNESS,
    MEDICAL_TREATMENT,
    CONTRACEPTION_CHANGE,
    STRESS_OR_TRAVEL,
    OTHER;

    companion object {
        fun fromKey(key: String?): CycleExclusionReason? {
            if (key.isNullOrBlank()) return null
            return entries.firstOrNull { it.name.equals(key, ignoreCase = true) }
        }
    }
}

data class Cycle(
    val id: String,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val averageLengthDays: Int = 28,
    val lutealPhaseLengthDays: Int = 14,
    val periodDays: List<PeriodDay> = emptyList(),
    val isExcludedFromEstimates: Boolean = false,
    val exclusionReason: CycleExclusionReason? = null
) {
    val isCurrent: Boolean
        get() = endDate == null

    fun lengthInDays(today: LocalDate = LocalDate.now()): Int {
        return if (endDate != null) {
            ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1
        } else {
            ChronoUnit.DAYS.between(startDate, today).toInt() + 1
        }
    }

    val lengthInDays: Int
        get() = lengthInDays(LocalDate.now())

}
