package fr.luteal.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TemperatureInputTest {
    @Test
    fun `fahrenheit increment stays inside the valid range`() {
        val next = TemperatureInput.clampHundredths(9770 + TemperatureInput.step(TemperatureUnit.FAHRENHEIT), TemperatureUnit.FAHRENHEIT)
        assertEquals(9780, next)
        assertEquals(10760, TemperatureInput.clampHundredths(20000, TemperatureUnit.FAHRENHEIT))
        assertEquals(9320, TemperatureInput.clampHundredths(1000, TemperatureUnit.FAHRENHEIT))
    }

    @Test
    fun `celsius hundredths round half up`() {
        assertEquals(3655, TemperatureInput.toHundredths(36.55))
        assertEquals(3650, TemperatureInput.toHundredths(36.50))
    }
}
