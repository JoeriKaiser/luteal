package fr.luteal.core.model

import java.time.LocalDate

data class PeriodDay(
    val date: LocalDate,
    val bleedingIntensity: BleedingIntensity,
    val notes: String = "",
    val symptomIds: List<String> = emptyList()
)
