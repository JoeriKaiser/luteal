package fr.luteal.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.luteal.app.R
import fr.luteal.core.designsystem.theme.LutealSpacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val BACKFILL_WINDOW_DAYS = 92L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackfillCycleDialog(
    existingCycleStarts: Set<LocalDate>,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val today = LocalDate.now()
    val earliest = today.minusDays(BACKFILL_WINDOW_DAYS)
    val zone = ZoneId.systemDefault()

    val selectableDates = remember(existingCycleStarts) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = Instant.ofEpochMilli(utcTimeMillis)
                    .atZone(zone)
                    .toLocalDate()
                return !date.isBefore(earliest) &&
                    !date.isAfter(today) &&
                    date !in existingCycleStarts
            }

            override fun isSelectableYear(year: Int): Boolean {
                return year in earliest.year..today.year
            }
        }
    }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = today
            .minusDays(BACKFILL_WINDOW_DAYS / 2)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli(),
        selectableDates = selectableDates
    )

    val selectedDate by remember {
        derivedStateOf {
            datePickerState.selectedDateMillis?.let { millis ->
                Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
            }
        }
    }

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { selectedDate?.let(onConfirm) },
                enabled = selectedDate != null
            ) {
                Text(stringResource(R.string.backfill_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = LutealSpacing.md),
            verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)
        ) {
            Text(
                text = stringResource(R.string.backfill_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.backfill_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = LutealSpacing.xs)
            )
            DatePicker(state = datePickerState)
        }
    }
}
