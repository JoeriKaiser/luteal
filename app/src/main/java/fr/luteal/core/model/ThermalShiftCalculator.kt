package fr.luteal.core.model

import java.time.LocalDate

sealed interface ThermalShiftResult {
    data object None : ThermalShiftResult
    data class Confirmed(
        val coverlineCelsius: Double,
        val firstHighDay: LocalDate,
        val baselineLowTemps: List<Double>,
        val highTemps: List<Double>
    ) : ThermalShiftResult
}

object ThermalShiftCalculator {
    private const val MIN_SHIFT_DELTA_CELSIUS = 0.20
    private const val COVERLINE_OFFSET_CELSIUS = 0.05

    fun evaluateCycle(
        cycleStartDate: LocalDate,
        observations: List<BiomarkerObservation>
    ): ThermalShiftResult {
        val validTemps = observations
            .filter { observation ->
                observation.date >= cycleStartDate &&
                    observation.bbt != null &&
                    !observation.bbt.isDisturbed
            }
            .sortedBy { it.date }

        if (validTemps.size < 9) return ThermalShiftResult.None

        for (index in 0..(validTemps.size - 9)) {
            val sixLows = validTemps.subList(index, index + 6).mapNotNull { it.bbt?.valueCelsius }
            val threeHighs = validTemps.subList(index + 6, index + 9).mapNotNull { it.bbt?.valueCelsius }
            val maxLow = sixLows.maxOrNull() ?: continue
            val allThreeHigher = threeHighs.all { it > maxLow }
            val thirdDaySignificantlyHigher = threeHighs[2] >= (maxLow + MIN_SHIFT_DELTA_CELSIUS)
            if (allThreeHigher && thirdDaySignificantlyHigher) {
                return ThermalShiftResult.Confirmed(
                    coverlineCelsius = maxLow + COVERLINE_OFFSET_CELSIUS,
                    firstHighDay = validTemps[index + 6].date,
                    baselineLowTemps = sixLows,
                    highTemps = threeHighs
                )
            }
        }
        return ThermalShiftResult.None
    }
}
