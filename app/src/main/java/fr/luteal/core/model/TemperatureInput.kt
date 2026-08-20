package fr.luteal.core.model

import kotlin.math.roundToInt

object TemperatureInput {
    fun toHundredths(value: Double): Int = (value * 100.0).roundToInt()

    fun clampHundredths(hundredths: Int, unit: TemperatureUnit): Int = when (unit) {
        TemperatureUnit.CELSIUS -> hundredths.coerceIn(3400, 4200)
        TemperatureUnit.FAHRENHEIT -> hundredths.coerceIn(9320, 10760)
    }

    fun step(unit: TemperatureUnit): Int =
        if (unit == TemperatureUnit.CELSIUS) 5 else 10

    fun defaultHundredths(unit: TemperatureUnit): Int =
        if (unit == TemperatureUnit.CELSIUS) 3650 else 9770
}
