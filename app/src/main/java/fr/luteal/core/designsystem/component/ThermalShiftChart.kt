package fr.luteal.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.luteal.core.model.BiomarkerObservation
import fr.luteal.core.model.TemperatureUnit
import fr.luteal.core.model.ThermalShiftResult
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

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
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val locale = LocalConfiguration.current.locales[0]
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) }
    val textMeasurer = rememberTextMeasurer()
    val axisTextStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.sp,
        color = labelColor
    )
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
            .height(230.dp)
            .padding(horizontal = 4.dp, vertical = 8.dp)
            .semantics { this.contentDescription = contentDescription }
    ) {
        if (temps.isEmpty()) return@Canvas

        val leftGutter = 46.dp.toPx()
        val rightGutter = 16.dp.toPx()
        val topGutter = 16.dp.toPx()
        val bottomGutter = 26.dp.toPx()

        val plotWidth = (size.width - leftGutter - rightGutter).coerceAtLeast(1f)
        val plotHeight = (size.height - topGutter - bottomGutter).coerceAtLeast(1f)

        val minTemp = (temps.minOf { it.second } - 0.15).toFloat()
        val maxTemp = (temps.maxOf { it.second } + 0.15).toFloat()
        val maxDay = temps.maxOf { it.first }.coerceAtLeast(2).toFloat()

        fun xFor(day: Int): Float =
            leftGutter + plotWidth * ((day - 1) / (maxDay - 1).coerceAtLeast(1f))

        fun yFor(temp: Double): Float {
            val range = (maxTemp - minTemp).coerceAtLeast(0.1f)
            return topGutter + plotHeight * (1f - ((temp.toFloat() - minTemp) / range))
        }

        // Draw 5 horizontal grid lines with numeric Y-axis temperature values
        val gridSteps = 4
        for (i in 0..gridSteps) {
            val fraction = i.toFloat() / gridSteps
            val y = topGutter + plotHeight * fraction
            val tempAtLine = maxTemp - (maxTemp - minTemp) * fraction

            drawLine(
                color = gridColor,
                start = Offset(leftGutter, y),
                end = Offset(size.width - rightGutter, y),
                strokeWidth = 1.dp.toPx()
            )
            val tempStr = String.format(locale, "%.1f°", tempAtLine)
            val measure = textMeasurer.measure(tempStr, style = axisTextStyle)
            drawText(
                textMeasurer = textMeasurer,
                text = tempStr,
                style = axisTextStyle,
                topLeft = Offset(leftGutter - measure.size.width - 6.dp.toPx(), y - measure.size.height / 2f)
            )
        }

        // Coverline
        coverline?.let { value ->
            val y = yFor(value)
            drawLine(
                color = coverlineColor,
                start = Offset(leftGutter, y),
                end = Offset(size.width - rightGutter, y),
                strokeWidth = 2.dp.toPx(),
                pathEffect = dashEffect
            )
            val coverlineStr = String.format(locale, "%.2f", value)
            val measure = textMeasurer.measure(coverlineStr, style = axisTextStyle.copy(color = coverlineColor))
            drawText(
                textMeasurer = textMeasurer,
                text = coverlineStr,
                style = axisTextStyle.copy(color = coverlineColor),
                topLeft = Offset(size.width - rightGutter - measure.size.width, y - measure.size.height - 2.dp.toPx())
            )
        }

        // Connecting lines between points
        temps.zipWithNext().forEach { (from, to) ->
            drawLine(
                color = lineColor,
                start = Offset(xFor(from.first), yFor(from.second)),
                end = Offset(xFor(to.first), yFor(to.second)),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Data points and X-axis day labels
        temps.forEach { (day, temp, disturbed) ->
            val center = Offset(xFor(day), yFor(temp))
            if (disturbed) {
                drawCircle(
                    color = disturbedColor,
                    radius = 5.dp.toPx(),
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            } else {
                drawCircle(color = lineColor, radius = 4.dp.toPx(), center = center)
            }

            // X-axis cycle day label
            val dayStr = "J$day"
            val measure = textMeasurer.measure(dayStr, style = axisTextStyle)
            drawText(
                textMeasurer = textMeasurer,
                text = dayStr,
                style = axisTextStyle,
                topLeft = Offset(center.x - measure.size.width / 2f, size.height - bottomGutter + 4.dp.toPx())
            )
        }
    }
}
