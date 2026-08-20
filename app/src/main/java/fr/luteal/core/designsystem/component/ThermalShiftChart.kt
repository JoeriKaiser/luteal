package fr.luteal.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.luteal.core.model.BiomarkerObservation
import fr.luteal.core.model.TemperatureUnit
import fr.luteal.core.model.ThermalShiftResult
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun ThermalShiftChart(
    cycleStart: LocalDate,
    observations: List<BiomarkerObservation>,
    shift: ThermalShiftResult,
    unit: TemperatureUnit,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val temps = observations.mapNotNull { observation ->
        val bbt = observation.bbt ?: return@mapNotNull null
        val day = ChronoUnit.DAYS.between(cycleStart, observation.date).toInt() + 1
        Triple(day, bbt.valueInUnit(unit), bbt.isDisturbed)
    }.sortedBy { it.first }
    val lineColor = MaterialTheme.colorScheme.primary
    val disturbedColor = MaterialTheme.colorScheme.tertiary
    val coverlineColor = MaterialTheme.colorScheme.outline
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val coverline = (shift as? ThermalShiftResult.Confirmed)?.coverlineCelsius?.let { celsius ->
        if (unit == TemperatureUnit.FAHRENHEIT) {
            fr.luteal.core.model.BasalBodyTemperature.celsiusToFahrenheit(celsius)
        } else {
            celsius
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(horizontal = 8.dp, vertical = 12.dp)
            .semantics { this.contentDescription = contentDescription }
    ) {
        if (temps.isEmpty()) return@Canvas
        val minTemp = (temps.minOf { it.second } - 0.2).toFloat()
        val maxTemp = (temps.maxOf { it.second } + 0.2).toFloat()
        val maxDay = temps.maxOf { it.first }.coerceAtLeast(9).toFloat()
        fun xFor(day: Int): Float = size.width * ((day - 1) / (maxDay - 1).coerceAtLeast(1f))
        fun yFor(temp: Double): Float {
            val span = (maxTemp - minTemp).coerceAtLeast(0.4f)
            return size.height * (1f - ((temp.toFloat() - minTemp) / span))
        }
        repeat(5) { index ->
            val y = size.height * index / 4f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        coverline?.let { value ->
            val y = yFor(value)
            drawLine(
                color = coverlineColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
            )
        }
        temps.zipWithNext().forEach { (from, to) ->
            drawLine(
                color = lineColor,
                start = Offset(xFor(from.first), yFor(from.second)),
                end = Offset(xFor(to.first), yFor(to.second)),
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
        }
        temps.forEach { (day, temp, disturbed) ->
            val center = Offset(xFor(day), yFor(temp))
            if (disturbed) {
                drawCircle(
                    color = disturbedColor,
                    radius = 10f,
                    center = center,
                    style = Stroke(width = 4f)
                )
            } else {
                drawCircle(color = lineColor, radius = 8f, center = center)
            }
        }
    }
}
