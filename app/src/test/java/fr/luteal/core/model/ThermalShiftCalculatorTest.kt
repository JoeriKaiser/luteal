package fr.luteal.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ThermalShiftCalculatorTest {
    private val start = LocalDate.parse("2026-08-01")

    @Test
    fun `standard biphasic cycle confirms coverline`() {
        val observations = temperatures(
            36.30, 36.35, 36.40, 36.42, 36.38, 36.45,
            36.70, 36.75, 36.85
        )
        val result = ThermalShiftCalculator.evaluateCycle(start, observations)
        val confirmed = result as ThermalShiftResult.Confirmed
        assertEquals(36.45, confirmed.coverlineCelsius, 0.001)
        assertEquals(start.plusDays(6), confirmed.firstHighDay)
    }

    @Test
    fun `disturbed highs are omitted from the shift window`() {
        val observations = temperatures(
            36.30, 36.35, 36.40, 36.42, 36.38, 36.45,
            36.70, 36.75, 36.85
        ).toMutableList()
        observations[6] = observations[6].copy(
            bbt = observations[6].bbt!!.copy(disturbances = setOf(BbtDisturbance.FEVER))
        )
        val result = ThermalShiftCalculator.evaluateCycle(start, observations)
        assertEquals(ThermalShiftResult.None, result)
    }

    @Test
    fun `monophasic series stays unconfirmed`() {
        val observations = temperatures(
            36.30, 36.32, 36.31, 36.34, 36.33, 36.35,
            36.34, 36.36, 36.35
        )
        assertEquals(ThermalShiftResult.None, ThermalShiftCalculator.evaluateCycle(start, observations))
    }

    @Test
    fun `gaps still confirm when nine valid readings exist`() {
        val observations = listOf(0, 2, 3, 5, 6, 8, 10, 11, 12).mapIndexed { index, offset ->
            val celsius = if (index < 6) 36.30 + (index * 0.02) else 36.70 + ((index - 6) * 0.05)
            observation(start.plusDays(offset.toLong()), celsius)
        }
        val result = ThermalShiftCalculator.evaluateCycle(start, observations)
        assertTrue(result is ThermalShiftResult.Confirmed)
    }

    @Test
    fun `disparate readings across months do not confirm thermal shift`() {
        val observations = (0..8).map { index ->
            val celsius = if (index < 6) 36.30 + (index * 0.02) else 36.70 + ((index - 6) * 0.05)
            observation(start.plusDays(index * 15L), celsius)
        }
        val result = ThermalShiftCalculator.evaluateCycle(start, observations)
        assertEquals(ThermalShiftResult.None, result)
    }

    private fun temperatures(vararg values: Double): List<BiomarkerObservation> =
        values.mapIndexed { index, value -> observation(start.plusDays(index.toLong()), value) }

    private fun observation(date: LocalDate, celsius: Double) = BiomarkerObservation(
        date = date,
        bbt = BasalBodyTemperature(valueCelsius = celsius)
    )
}
