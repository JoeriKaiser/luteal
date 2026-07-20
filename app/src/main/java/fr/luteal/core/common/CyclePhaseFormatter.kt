package fr.luteal.core.common

import fr.luteal.core.model.CyclePhase

object CyclePhaseFormatter {
    fun formatPhaseWithDay(dayOfCycle: Int, phase: CyclePhase): String {
        return "Jour $dayOfCycle • Phase ${phase.displayName}"
    }

    fun formatPhaseWithDay(dayOfCycle: Int, phaseName: String): String {
        val formattedPhase = if (phaseName.startsWith("Phase", ignoreCase = true)) {
            phaseName
        } else {
            "Phase ${phaseName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}"
        }
        return "Jour $dayOfCycle • $formattedPhase"
    }

    fun formatPeriodCountdown(daysUntil: Int): String {
        return when {
            daysUntil < 0 -> {
                val overdueDays = -daysUntil
                "Règles en retard de $overdueDays jour${if (overdueDays > 1) "s" else ""}"
            }
            daysUntil == 0 -> "Règles prévues aujourd'hui"
            daysUntil == 1 -> "Règles prévues dans 1 jour"
            else -> "Règles prévues dans $daysUntil jours"
        }
    }

    fun formatPhaseDescription(phase: CyclePhase): String {
        return "Phase ${phase.displayName} : ${phase.description}"
    }

    fun formatPhaseName(phase: CyclePhase): String {
        return "Phase ${phase.displayName}"
    }
}
