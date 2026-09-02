package fr.luteal.core.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

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
            val transitionValid = ChronoUnit.DAYS.between(
                validTemps[index + 5].date,
                validTemps[index + 6].date
            ) <= 2
            if (!transitionValid) continue

            val continuityValid = (index until (index + 8)).all { i ->
                ChronoUnit.DAYS.between(validTemps[i].date, validTemps[i + 1].date) <= 3
            }
            if (!continuityValid) continue

            val sixLows = validTemps.subList(index, index + 6).mapNotNull { it.bbt?.valueCelsius }
            val threeHighs = validTemps.subList(index + 6, index + 9).mapNotNull { it.bbt?.valueCelsius }
            val maxLow = sixLows.maxOrNull() ?: continue
            val allThreeHigher = threeHighs.all { it > maxLow }
            val thirdDaySignificantlyHigher = threeHighs[2] >= (maxLow + MIN_SHIFT_DELTA_CELSIUS)
            if (allThreeHigher && thirdDaySignificantlyHigher) {
                return ThermalShiftResult.Confirmed(
                    coverlineCelsius = maxLow,
                    firstHighDay = validTemps[index + 6].date,
                    baselineLowTemps = sixLows,
                    highTemps = threeHighs
                )
            }
        }
        return ThermalShiftResult.None
    }
}
