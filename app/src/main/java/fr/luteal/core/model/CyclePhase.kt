package fr.luteal.core.model

enum class CyclePhase(
    val displayName: String,
    val description: String,
    val typicalDurationDays: IntRange
) {
    MENSTRUAL(
        displayName = "Menstruelle",
        description = "Phase de saignements menstruels",
        typicalDurationDays = 1..5
    ),
    FOLLICULAR(
        displayName = "Folliculaire",
        description = "Phase de développement folliculaire avant l'ovulation",
        typicalDurationDays = 6..13
    ),
    OVULATORY(
        displayName = "Ovulatoire",
        description = "Phase de libération de l'ovocyte par l'ovaire",
        typicalDurationDays = 14..16
    ),
    LUTEAL(
        displayName = "Lutéale",
        description = "Phase après l'ovulation préparant l'utérus",
        typicalDurationDays = 17..28
    );

    companion object {
        fun currentPhase(dayOfCycle: Int, totalCycleLength: Int = 28): CyclePhase {
            val lutealLength = 14
            val ovulationDay = (totalCycleLength - lutealLength).coerceAtLeast(1)
            val ovulatoryWindow = (ovulationDay - 1)..(ovulationDay + 1)

            return when {
                dayOfCycle <= 5 -> MENSTRUAL
                dayOfCycle < ovulatoryWindow.first -> FOLLICULAR
                dayOfCycle in ovulatoryWindow -> OVULATORY
                else -> LUTEAL
            }
        }
    }
}
