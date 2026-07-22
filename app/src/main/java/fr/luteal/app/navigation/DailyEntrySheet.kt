package fr.luteal.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.luteal.app.R
import fr.luteal.core.common.FrenchDateFormatter
import fr.luteal.core.designsystem.component.LutealCheckboxRow
import fr.luteal.core.designsystem.component.LutealPrimaryButton
import fr.luteal.core.designsystem.component.LutealSecondaryButton
import fr.luteal.core.designsystem.component.ObservationScale
import fr.luteal.core.designsystem.component.StatusPill
import fr.luteal.core.designsystem.component.StatusTone
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.model.BleedingIntensity
import fr.luteal.core.model.Cycle
import fr.luteal.core.model.DailyEntry
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DailyEntrySheet(
    date: LocalDate,
    existingEntry: DailyEntry?,
    currentCycle: Cycle?,
    startPeriodIntent: Boolean,
    isSaving: Boolean,
    saveFailed: Boolean,
    onDismiss: () -> Unit,
    onSave: (DailyEntry, Boolean) -> Unit
) {
    val stateKey = "${date}-${existingEntry?.updatedAt}"
    var bleedingName by rememberSaveable(stateKey) {
        mutableStateOf(existingEntry?.bleedingIntensity?.name)
    }
    var pain by rememberSaveable(stateKey) { mutableStateOf(existingEntry?.painLevel) }
    var mood by rememberSaveable(stateKey) { mutableStateOf(existingEntry?.moodLevel) }
    var energy by rememberSaveable(stateKey) { mutableStateOf(existingEntry?.energyLevel) }
    var symptomIds by rememberSaveable(stateKey) {
        mutableStateOf(existingEntry?.symptomIds.orEmpty().sorted())
    }
    var notes by rememberSaveable(stateKey) { mutableStateOf(existingEntry?.notes.orEmpty()) }
    val initialStartsNewCycle = startPeriodIntent || currentCycle == null
    var startsNewCycle by rememberSaveable(stateKey) {
        mutableStateOf(initialStartsNewCycle)
    }
    var showOptional by rememberSaveable(stateKey) {
        mutableStateOf(existingEntry?.symptomIds?.isNotEmpty() == true || existingEntry?.notes?.isNotBlank() == true)
    }
    var showDiscardConfirmation by remember { mutableStateOf(false) }

    val bleeding = bleedingName?.let(BleedingIntensity::valueOf)
    val selectedSymptoms = symptomIds.toSet()
    val hasChanges = bleeding != existingEntry?.bleedingIntensity ||
        pain != existingEntry?.painLevel ||
        mood != existingEntry?.moodLevel ||
        energy != existingEntry?.energyLevel ||
        selectedSymptoms != existingEntry?.symptomIds.orEmpty() ||
        notes != existingEntry?.notes.orEmpty() ||
        startsNewCycle != initialStartsNewCycle

    val requestDismiss = {
        when {
            isSaving -> Unit
            hasChanges -> showDiscardConfirmation = true
            else -> onDismiss()
        }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val painDescriptions = scaleDescriptions(R.string.editor_pain_value_description)
    val moodDescriptions = scaleDescriptions(R.string.editor_mood_value_description)
    val energyDescriptions = scaleDescriptions(R.string.editor_energy_value_description)

    ModalBottomSheet(
        onDismissRequest = requestDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
                .imePadding()
                .navigationBarsPadding()
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = LutealSpacing.lg,
                    end = LutealSpacing.lg,
                    bottom = LutealSpacing.lg
                ),
                verticalArrangement = Arrangement.spacedBy(LutealSpacing.lg)
            ) {
                item {
                    Text(
                        text = stringResource(
                            R.string.editor_title,
                            FrenchDateFormatter.formatShortDate(date)
                        ),
                        style = MaterialTheme.typography.headlineMedium
                    )
                }

                item {
                    BleedingSelector(
                        selected = bleeding,
                        onSelected = { bleedingName = it?.name }
                    )
                }

                if (startPeriodIntent && (bleeding == null || bleeding == BleedingIntensity.NONE)) {
                    item {
                        Text(
                            text = stringResource(R.string.editor_start_cycle_instruction),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (
                    bleeding != null &&
                    bleeding != BleedingIntensity.NONE &&
                    currentCycle?.startDate != date
                ) {
                    item {
                        LutealCheckboxRow(
                            title = stringResource(R.string.editor_new_cycle),
                            description = stringResource(R.string.editor_new_cycle_support),
                            checked = startsNewCycle,
                            onCheckedChange = { startsNewCycle = it }
                        )
                    }
                }

                item {
                    ObservationScale(
                        label = stringResource(R.string.editor_pain),
                        supportingText = stringResource(R.string.editor_pain_scale_support),
                        value = pain,
                        onValueChange = { pain = it },
                        valueDescription = painDescriptions::getValue
                    )
                }
                item {
                    ObservationScale(
                        label = stringResource(R.string.editor_mood),
                        supportingText = stringResource(R.string.editor_mood_scale_support),
                        value = mood,
                        onValueChange = { mood = it },
                        valueDescription = moodDescriptions::getValue
                    )
                }
                item {
                    ObservationScale(
                        label = stringResource(R.string.editor_energy),
                        supportingText = stringResource(R.string.editor_energy_scale_support),
                        value = energy,
                        onValueChange = { energy = it },
                        valueDescription = energyDescriptions::getValue
                    )
                }

                item {
                    LutealSecondaryButton(
                        text = if (showOptional) {
                            stringResource(R.string.editor_optional_hide)
                        } else {
                            stringResource(R.string.editor_optional_show)
                        },
                        onClick = { showOptional = !showOptional },
                        icon = if (showOptional) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (showOptional) {
                    item {
                        SymptomSelector(
                            selected = selectedSymptoms,
                            onToggle = { symptom ->
                                symptomIds = if (symptom in selectedSymptoms) {
                                    (selectedSymptoms - symptom).sorted()
                                } else {
                                    (selectedSymptoms + symptom).sorted()
                                }
                            }
                        )
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
                            StatusPill(
                                text = stringResource(R.string.private_label),
                                tone = StatusTone.PRIVATE
                            )
                            OutlinedTextField(
                                value = notes,
                                onValueChange = { notes = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.editor_notes)) },
                                placeholder = { Text(stringResource(R.string.editor_notes_hint)) },
                                minLines = 3,
                                maxLines = 6,
                                shape = MaterialTheme.shapes.medium
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = LutealSpacing.lg,
                        vertical = LutealSpacing.sm
                    ),
                verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)
            ) {
                if (saveFailed) {
                    Text(
                        text = stringResource(R.string.save_error_preserved),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                LutealPrimaryButton(
                    text = if (saveFailed) {
                        stringResource(R.string.action_retry_save_entry)
                    } else {
                        stringResource(R.string.action_save_entry)
                    },
                    onClick = {
                        onSave(
                            DailyEntry(
                                date = date,
                                bleedingIntensity = bleeding,
                                painLevel = pain,
                                moodLevel = mood,
                                energyLevel = energy,
                                symptomIds = selectedSymptoms,
                                notes = notes,
                                updatedAt = Instant.now()
                            ),
                            startsNewCycle &&
                                bleeding != null &&
                                bleeding != BleedingIntensity.NONE
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.Save,
                    enabled = !startPeriodIntent ||
                        (bleeding != null && bleeding != BleedingIntensity.NONE),
                    loading = isSaving
                )
            }
        }
    }

    if (showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmation = false },
            title = { Text(stringResource(R.string.editor_discard_title)) },
            text = { Text(stringResource(R.string.editor_discard_body)) },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.editor_discard_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirmation = false }) {
                    Text(stringResource(R.string.editor_keep_editing))
                }
            }
        )
    }
}

@Composable
private fun scaleDescriptions(resourceId: Int): Map<Int, String> =
    (1..5).associateWith { value -> stringResource(resourceId, value) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BleedingSelector(
    selected: BleedingIntensity?,
    onSelected: (BleedingIntensity?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
        Text(
            text = stringResource(R.string.editor_bleeding),
            style = MaterialTheme.typography.titleSmall
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs),
            maxItemsInEachRow = 3
        ) {
            BleedingChip(
                label = stringResource(R.string.bleeding_not_set),
                selected = selected == null,
                onClick = { onSelected(null) },
                modifier = Modifier.weight(1f)
            )
            BleedingIntensity.entries.forEach { intensity ->
                BleedingChip(
                    label = bleedingLabel(intensity),
                    selected = selected == intensity,
                    onClick = { onSelected(intensity) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun BleedingChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 2) },
        leadingIcon = if (selected) {
            { Icon(Icons.Rounded.Check, contentDescription = null) }
        } else {
            null
        },
        modifier = modifier,
        shape = MaterialTheme.shapes.small
    )
}

@Composable
private fun bleedingLabel(intensity: BleedingIntensity): String = stringResource(
    when (intensity) {
        BleedingIntensity.NONE -> R.string.bleeding_none
        BleedingIntensity.SPOTTING -> R.string.bleeding_spotting
        BleedingIntensity.LIGHT -> R.string.bleeding_light
        BleedingIntensity.MEDIUM -> R.string.bleeding_medium
        BleedingIntensity.HEAVY -> R.string.bleeding_heavy
    }
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SymptomSelector(
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    val symptoms = listOf(
        "cramps" to R.string.symptom_cramps,
        "headache" to R.string.symptom_headache,
        "fatigue" to R.string.symptom_fatigue,
        "bloating" to R.string.symptom_bloating
    )
    Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
        Text(
            text = stringResource(R.string.editor_symptoms),
            style = MaterialTheme.typography.titleSmall
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs),
            maxItemsInEachRow = 2
        ) {
            symptoms.forEach { (id, labelRes) ->
                val isSelected = id in selected
                FilterChip(
                    selected = isSelected,
                    onClick = { onToggle(id) },
                    label = { Text(stringResource(labelRes), maxLines = 2) },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small
                )
            }
        }
    }
}
