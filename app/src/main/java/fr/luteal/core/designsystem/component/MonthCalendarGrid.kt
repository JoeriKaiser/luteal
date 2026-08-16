package fr.luteal.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.luteal.app.R
import fr.luteal.core.common.LocalizedDateFormatter
import fr.luteal.core.designsystem.theme.LocalPhaseColors
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.model.BleedingIntensity
import fr.luteal.core.model.CalendarDayProjection
import fr.luteal.core.model.MonthCalendarProjection
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.WeekFields

@Composable
fun MonthCalendarGrid(
    projection: MonthCalendarProjection,
    selectedDate: LocalDate?,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)
    ) {
        WeekdayHeaderRow()

        projection.weeks.forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                week.forEach { day ->
                    CalendarDayCell(
                        day = day,
                        isSelected = day.date == selectedDate,
                        onClick = { onSelectDate(day.date) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekdayHeaderRow() {
    val locale = LocalConfiguration.current.locales[0]
    val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek
    val daysOfWeek = (0L..6L).map { firstDayOfWeek.plus(it) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = LutealSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        daysOfWeek.forEach { day ->
            val label = day.getDisplayName(TextStyle.NARROW, locale)
            Text(
                text = label.uppercase(locale),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun CalendarDayCell(
    day: CalendarDayProjection,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val phaseColors = LocalPhaseColors.current

    val cellBackground: Color = when {
        day.hasBleeding -> phaseColors.menstrual.container
        day.isEstimatedPeriodWindow -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
        else -> Color.Transparent
    }

    val cellBorder: BorderStroke? = when {
        isSelected -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        day.isEstimatedPeriodWindow -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        day.isToday -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        else -> null
    }

    val textColor: Color = when {
        !day.isCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        day.hasBleeding -> phaseColors.menstrual.content
        day.isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    val locale = LocalConfiguration.current.locales[0]
    val cdFormattedDate = LocalizedDateFormatter.formatFullDate(day.date, locale)
    val cdString = buildString {
        append(cdFormattedDate)
        if (day.isToday) {
            append(", ")
            append(stringResource(R.string.calendar_today_button))
        }
        if (day.hasBleeding) {
            append(", ")
            val intensityLabel = when (day.bleedingIntensity) {
                BleedingIntensity.LIGHT -> stringResource(R.string.bleeding_light)
                BleedingIntensity.MEDIUM -> stringResource(R.string.bleeding_medium)
                BleedingIntensity.HEAVY -> stringResource(R.string.bleeding_heavy)
                BleedingIntensity.SPOTTING -> stringResource(R.string.bleeding_spotting)
                else -> stringResource(R.string.calendar_legend_recorded_period)
            }
            append(stringResource(R.string.calendar_day_cd_recorded, "", intensityLabel).trimStart(',', ' '))
        } else if (day.isEstimatedPeriodWindow) {
            append(", ")
            append(stringResource(R.string.calendar_legend_estimated_period))
        }
        if (day.hasObservations) {
            append(", ")
            append(stringResource(R.string.calendar_legend_observation))
        }
    }

    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(LutealSpacing.xs))
            .background(cellBackground)
            .then(
                if (cellBorder != null) Modifier.border(cellBorder, RoundedCornerShape(LutealSpacing.xs))
                else Modifier
            )
            .clickable(
                role = Role.Button,
                onClick = onClick
            )
            .semantics {
                contentDescription = cdString
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (day.isToday || isSelected || day.hasBleeding) FontWeight.Bold else FontWeight.Normal
                ),
                color = textColor,
                textAlign = TextAlign.Center
            )

            // Observation or cycle-start indicator dot
            if (day.hasObservations || day.isCycleStart) {
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(
                            if (day.isCycleStart) phaseColors.menstrual.content
                            else MaterialTheme.colorScheme.secondary
                        )
                )
            } else {
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalendarLegendCard(modifier: Modifier = Modifier) {
    val phaseColors = LocalPhaseColors.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(LutealSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)
        ) {
            Text(
                text = stringResource(R.string.calendar_legend_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LutealSpacing.md),
                verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)
            ) {
                // Recorded period
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(phaseColors.menstrual.container)
                            .border(1.dp, phaseColors.menstrual.content.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                    )
                    Text(
                        text = stringResource(R.string.calendar_legend_recorded_period),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Estimated period
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp))
                    )
                    Text(
                        text = stringResource(R.string.calendar_legend_estimated_period),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Observations dot
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary)
                    )
                    Text(
                        text = stringResource(R.string.calendar_legend_observation),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
