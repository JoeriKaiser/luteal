package fr.luteal.app.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
import fr.luteal.core.model.Cycle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import fr.luteal.core.designsystem.component.LutealRadioRow
import fr.luteal.core.designsystem.component.LutealToggleRow
import fr.luteal.core.model.CycleExclusionReason

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCycleDialog(
    cycle: Cycle,
    existingCycles: List<Cycle>,
    today: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (newStartDate: LocalDate) -> Unit
) {
    val initialMillis = cycle.startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val todayMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                return utcTimeMillis <= todayMillis
            }
        }
    )

    val selectedDate by remember {
        derivedStateOf {
            datePickerState.selectedDateMillis?.let {
                Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
            }
        }
    }

    val isCollision by remember(selectedDate, existingCycles, cycle.id) {
        derivedStateOf {
            val date = selectedDate
            date != null && existingCycles.any { it.id != cycle.id && it.startDate == date }
        }
    }

    val isValidDate by remember(selectedDate, isCollision) {
        derivedStateOf {
            val date = selectedDate
            date != null && !isCollision
        }
    }

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    selectedDate?.let { onConfirm(it) }
                },
                enabled = isValidDate
            ) {
                Text(stringResource(R.string.cycle_edit_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LutealSpacing.md)
        ) {
            Text(
                text = stringResource(R.string.cycle_edit_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = LutealSpacing.sm)
            )
            if (isCollision) {
                Spacer(modifier = Modifier.height(LutealSpacing.xs))
                Text(
                    text = stringResource(R.string.cycle_edit_collision_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            DatePicker(
                state = datePickerState,
                showModeToggle = false
            )
        }
    }
}

@Composable
fun DeleteCycleConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.cycle_delete_title),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Text(
                text = stringResource(R.string.cycle_delete_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.cycle_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun CycleExclusionDialog(
    cycle: Cycle,
    onDismiss: () -> Unit,
    onConfirm: (isExcluded: Boolean, reason: CycleExclusionReason?) -> Unit
) {
    var isExcluded by remember { mutableStateOf(cycle.isExcludedFromEstimates) }
    var selectedReason by remember { mutableStateOf(cycle.exclusionReason ?: CycleExclusionReason.ILLNESS) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.cycle_exclusion_dialog_title),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.cycle_exclusion_dialog_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LutealToggleRow(
                    title = stringResource(R.string.cycle_exclusion_switch_label),
                    description = "",
                    checked = isExcluded,
                    onCheckedChange = { isExcluded = it }
                )

                if (isExcluded) {
                    Text(
                        text = stringResource(R.string.cycle_exclusion_reason_label),
                        style = MaterialTheme.typography.titleSmall
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xxs)) {
                        CycleExclusionReason.entries.forEach { reason ->
                            LutealRadioRow(
                                title = when (reason) {
                                    CycleExclusionReason.ILLNESS -> stringResource(R.string.cycle_exclusion_reason_illness)
                                    CycleExclusionReason.MEDICAL_TREATMENT -> stringResource(R.string.cycle_exclusion_reason_medical_treatment)
                                    CycleExclusionReason.CONTRACEPTION_CHANGE -> stringResource(R.string.cycle_exclusion_reason_contraception_change)
                                    CycleExclusionReason.STRESS_OR_TRAVEL -> stringResource(R.string.cycle_exclusion_reason_stress_or_travel)
                                    CycleExclusionReason.OTHER -> stringResource(R.string.cycle_exclusion_reason_other)
                                },
                                selected = selectedReason == reason,
                                onClick = { selectedReason = reason }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(isExcluded, if (isExcluded) selectedReason else null)
                }
            ) {
                Text(stringResource(R.string.cycle_exclusion_save), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
