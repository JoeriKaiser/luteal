package fr.luteal.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BasalBodyTemperatureTest {
    @Test
    fun `round trip preserves hundredths`() {
        val created = BasalBodyTemperature.fromUnit(36.65, TemperatureUnit.CELSIUS)!!
        val fahrenheit = created.valueInUnit(TemperatureUnit.FAHRENHEIT)
        val back = BasalBodyTemperature.fromUnit(fahrenheit, TemperatureUnit.FAHRENHEIT)!!
        assertEquals(36.65, back.valueCelsius, 0.01)
    }

    @Test
    fun `rejects temperatures outside the valid range`() {
        assertNull(BasalBodyTemperature.fromUnit(33.9, TemperatureUnit.CELSIUS))
        assertNull(BasalBodyTemperature.fromUnit(42.1, TemperatureUnit.CELSIUS))
        assertNull(BasalBodyTemperature.fromUnit(93.0, TemperatureUnit.FAHRENHEIT))
    }

    @Test
    fun `disturbances mark the reading as disturbed`() {
        val reading = BasalBodyTemperature(
            valueCelsius = 36.80,
            disturbances = setOf(BbtDisturbance.ALCOHOL)
        )
        assertTrue(reading.isDisturbed)
    }
}
