package fr.luteal.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

enum class TemperatureUnit {
    CELSIUS,
    FAHRENHEIT
}

enum class BbtDisturbance {
    FEVER,
    ALCOHOL,
    POOR_SLEEP,
    TIME_SHIFT,
    LATE_MEASUREMENT,
    STRESS,
    MEDICATION
}

enum class CervicalMucusSensation {
    DRY,
    DAMP,
    WET,
    SLIPPERY
}

enum class CervicalMucusTexture {
    STICKY,
    CREAMY,
    EGG_WHITE,
    WATERY
}

enum class LhTestResult {
    NEGATIVE,
    LOW,
    PEAK_POSITIVE,
    INDETERMINATE
}

enum class HcgTestResult {
    NEGATIVE,
    POSITIVE,
    FAINT_UNCERTAIN
}

data class BasalBodyTemperature(
    val valueCelsius: Double,
    val measuredTime: LocalTime? = null,
    val disturbances: Set<BbtDisturbance> = emptySet()
) {
    val isDisturbed: Boolean
        get() = disturbances.isNotEmpty()

    fun valueInUnit(unit: TemperatureUnit): Double = when (unit) {
        TemperatureUnit.CELSIUS -> valueCelsius
        TemperatureUnit.FAHRENHEIT -> celsiusToFahrenheit(valueCelsius)
    }

    companion object {
        val CELSIUS_VALID_RANGE = 34.00..42.00
        val FAHRENHEIT_VALID_RANGE = 93.20..107.60

        fun celsiusToFahrenheit(celsius: Double): Double =
            ((celsius * 9.0 / 5.0) + 32.0).roundToHundredths()

        fun fahrenheitToCelsius(fahrenheit: Double): Double =
            ((fahrenheit - 32.0) * 5.0 / 9.0).roundToHundredths()

        fun fromUnit(value: Double, unit: TemperatureUnit): BasalBodyTemperature? {
            val celsius = when (unit) {
                TemperatureUnit.CELSIUS -> value.roundToHundredths()
                TemperatureUnit.FAHRENHEIT -> fahrenheitToCelsius(value)
            }
            if (celsius !in CELSIUS_VALID_RANGE) return null
            return BasalBodyTemperature(valueCelsius = celsius)
        }
    }
}

data class CervicalFluidObservation(
    val sensation: CervicalMucusSensation? = null,
    val texture: CervicalMucusTexture? = null
) {
    val hasObservation: Boolean
        get() = sensation != null || texture != null
}

data class RapidTestLogs(
    val lhTest: LhTestResult? = null,
    val hcgTest: HcgTestResult? = null
) {
    val hasLogs: Boolean
        get() = lhTest != null || hcgTest != null
}

data class BiomarkerObservation(
    val date: LocalDate,
    val bbt: BasalBodyTemperature? = null,
    val cervicalFluid: CervicalFluidObservation? = null,
    val rapidTests: RapidTestLogs? = null,
    val notes: String = "",
    val updatedAt: Instant = Instant.now()
) {
    val isEmpty: Boolean
        get() = bbt == null &&
            (cervicalFluid == null || !cervicalFluid.hasObservation) &&
            (rapidTests == null || !rapidTests.hasLogs) &&
            notes.isBlank()

    fun sameContentAs(other: BiomarkerObservation?): Boolean {
        if (other == null) return isEmpty
        return bbt == other.bbt &&
            cervicalFluid == other.cervicalFluid &&
            rapidTests == other.rapidTests &&
            notes == other.notes
    }
}

private fun Double.roundToHundredths(): Double = (this * 100.0).roundToInt() / 100.0
