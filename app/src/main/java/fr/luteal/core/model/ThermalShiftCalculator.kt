package fr.luteal.core.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

sealed interface ThermalShiftResult {
    data object None : ThermalShiftResult
    data class Confirmed(
        val coverlineCelsius: Double,
        val firstHighDay: LocalDate,
        val baselineLowTemps: List<Double>,
        val highTemps: List<Double>
    ) : ThermalShiftResult
}

/**
 * Evaluates a conservative 3-over-6 retrospective basal body temperature shift
 * (Sensiplan base rule, Frank-Herrmann et al. 2007, PMID 17314078): three
 * consecutive undisturbed readings strictly above the highest of the previous
 * six undisturbed readings, with the third high at least +0.20 C above that
 * coverline.
 *
 * Temperatures are compared in integer centidegrees so IEEE-754 rounding
 * artifacts (such as 36.1 + 0.2 = 36.300000000000004) cannot reject an exact
 * +0.20 C rise. Suppressed during active hormonal contraception because
 * synthetic progestins elevate basal body temperature independently of
 * ovulation.
 */
object ThermalShiftCalculator {
    private const val MIN_SHIFT_DELTA_CENTIDEGREES = 20

    fun evaluateCycle(
        cycleStartDate: LocalDate,
        observations: List<BiomarkerObservation>,
        cycleEndDate: LocalDate? = null,
        isPillRegimenActive: Boolean = false
    ): ThermalShiftResult {
        if (isPillRegimenActive) return ThermalShiftResult.None

        val validTemps = observations
            .filter { observation ->
                !observation.date.isBefore(cycleStartDate) &&
                    (cycleEndDate == null || !observation.date.isAfter(cycleEndDate)) &&
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
            if (sixLows.size != 6 || threeHighs.size != 3) continue

            val sixLowsCenti = sixLows.map(::toCentidegrees)
            val threeHighsCenti = threeHighs.map(::toCentidegrees)
            val maxLowCenti = sixLowsCenti.maxOrNull() ?: continue
            val maxLow = sixLows.maxOrNull() ?: continue

            val allThreeHigher = threeHighsCenti.all { it > maxLowCenti }
            val thirdDaySignificantlyHigher =
                threeHighsCenti[2] >= (maxLowCenti + MIN_SHIFT_DELTA_CENTIDEGREES)
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

    private fun toCentidegrees(celsius: Double): Int = (celsius * 100.0).roundToInt()
}
