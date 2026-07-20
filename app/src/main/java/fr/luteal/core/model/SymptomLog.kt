package fr.luteal.core.model

import java.time.Instant
import java.time.LocalDate

data class SymptomLog(
    val id: String,
    val timestamp: Instant,
    val date: LocalDate,
    val symptomId: String,
    val severity: Int,
    val notes: String = ""
) {
    init {
        require(severity in 1..5) { "Severity must be between 1 and 5" }
    }
}
