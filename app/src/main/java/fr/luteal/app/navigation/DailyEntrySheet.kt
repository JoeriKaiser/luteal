package fr.luteal.app.navigation

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import fr.luteal.app.R
import fr.luteal.core.common.LocalizedDateFormatter
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
    /** Observations offered, derived from declared contexts. */
    offeredSymptomIds: List<String>,
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
    var startsNewCycle by rememberSaveable(
        "${date}-${existingEntry?.updatedAt}-$startPeriodIntent"
    ) {
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

    // rememberModalBottomSheetState captures its confirmValueChange lambda at
    // first composition; rememberUpdatedState keeps the hasChanges read fresh.
    val currentHasChanges by rememberUpdatedState(hasChanges)

    val locale = LocalConfiguration.current.locales[0]
    val scope = rememberCoroutineScope()
    val requestDismiss = {
        when {
            isSaving -> Unit
            hasChanges -> showDiscardConfirmation = true
            else -> onDismiss()
        }
    }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target ->
            if (target == SheetValue.Hidden) {
                if (currentHasChanges) {
                    showDiscardConfirmation = true
                    false
                } else {
                    true
                }
            } else {
                true
            }
        }
    )

    BackHandler(enabled = sheetState.isVisible) {
        if (hasChanges) {
            showDiscardConfirmation = true
        } else {
            scope.launch {
                sheetState.hide()
                onDismiss()
            }
        }
    }

    val painDescriptions = scaleDescriptions(R.string.editor_pain_value_description)
    val moodDescriptions = scaleDescriptions(R.string.editor_mood_value_description)
    val energyDescriptions = scaleDescriptions(R.string.editor_energy_value_description)

    ModalBottomSheet(
        onDismissRequest = requestDismiss,
        sheetState = sheetState,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
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
                            LocalizedDateFormatter.formatShortDate(date, locale)
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
                            offeredSymptomIds = offeredSymptomIds,
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
            onDismissRequest = {
                showDiscardConfirmation = false
                if (sheetState.currentValue == SheetValue.Hidden) {
                    scope.launch { sheetState.show() }
                }
            },
            title = { Text(stringResource(R.string.editor_discard_title)) },
            text = { Text(stringResource(R.string.editor_discard_body)) },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.editor_discard_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirmation = false
                        if (sheetState.currentValue == SheetValue.Hidden) {
                            scope.launch { sheetState.show() }
                        }
                    }
                ) {
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
        // Two per row rather than three: the graduated drop needs room beside
        // the label, and flow is an ordered scale worth reading as one.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs),
            maxItemsInEachRow = 2
        ) {
            BleedingChip(
                label = stringResource(R.string.bleeding_not_set),
                intensity = null,
                selected = selected == null,
                onClick = { onSelected(null) },
                modifier = Modifier.weight(1f)
            )
            BleedingIntensity.entries.forEach { intensity ->
                BleedingChip(
                    label = bleedingLabel(intensity),
                    intensity = intensity,
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
    intensity: BleedingIntensity?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // The drop grows across the scale so the ordering is visible, while the
    // check keeps selection from resting on colour alone.
    val dropSize = when (intensity) {
        BleedingIntensity.HEAVY -> 22.dp
        BleedingIntensity.MEDIUM -> 19.dp
        BleedingIntensity.LIGHT -> 16.dp
        BleedingIntensity.SPOTTING -> 13.dp
        else -> null
    }
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 2) },
        leadingIcon = when {
            dropSize != null -> {
                {
                    Icon(
                        imageVector = Icons.Rounded.WaterDrop,
                        contentDescription = null,
                        modifier = Modifier.size(dropSize)
                    )
                }
            }
            intensity == BleedingIntensity.NONE -> {
                {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            else -> null
        },
        trailingIcon = if (selected) {
            { Icon(Icons.Rounded.Check, contentDescription = null) }
        } else {
            null
        },
        modifier = modifier,
        shape = MaterialTheme.shapes.small
    )
}

/**
 * Label for a catalog symptom id, or null when the id has no French label yet.
 *
 * Returning null rather than throwing means a symptom key arriving from a
 * future catalog cannot crash the editor; it is simply not offered until it has
 * copy.
 */
@Composable
private fun symptomLabel(id: String): String? = when (id) {
    "cramps" -> stringResource(R.string.symptom_cramps)
    "headache" -> stringResource(R.string.symptom_headache)
    "abdominal_pain" -> stringResource(R.string.symptom_abdominal_pain)
    "backache" -> stringResource(R.string.symptom_backache)
    "muscle_aches" -> stringResource(R.string.symptom_muscle_aches)
    "fatigue" -> stringResource(R.string.symptom_fatigue)
    "sleep_issue" -> stringResource(R.string.symptom_sleep_issue)
    "bloating" -> stringResource(R.string.symptom_bloating)
    "nausea" -> stringResource(R.string.symptom_nausea)
    "digestive_changes" -> stringResource(R.string.symptom_digestive_changes)
    "breast_tenderness" -> stringResource(R.string.symptom_breast_tenderness)
    "mood_changes" -> stringResource(R.string.symptom_mood_changes)
    "anxiety" -> stringResource(R.string.symptom_anxiety)
    "acne" -> stringResource(R.string.symptom_acne)
    "pelvic_pain_outside_period" -> stringResource(R.string.symptom_pelvic_pain_outside_period)
    else -> null
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

private enum class SymptomGroup(
    val titleRes: Int,
    val symptomIds: List<String>
) {
    PAIN(
        R.string.symptom_group_pain,
        listOf("cramps", "headache", "abdominal_pain", "backache", "muscle_aches", "pelvic_pain_outside_period")
    ),
    PHYSICAL(
        R.string.symptom_group_physical,
        listOf("bloating", "nausea", "digestive_changes", "breast_tenderness", "acne")
    ),
    MOOD_ENERGY(
        R.string.symptom_group_mood_energy,
        listOf("fatigue", "sleep_issue", "mood_changes", "anxiety")
    );

    companion object {
        fun forOffered(offeredSymptomIds: List<String>): List<Pair<SymptomGroup, List<String>>> {
            val offeredSet = offeredSymptomIds.toSet()
            val grouped = entries.mapNotNull { group ->
                val matching = group.symptomIds.filter { it in offeredSet }
                if (matching.isNotEmpty()) group to matching else null
            }
            val groupedIds = entries.flatMap { it.symptomIds }.toSet()
            val remaining = offeredSymptomIds.filterNot { it in groupedIds }
            return if (remaining.isNotEmpty()) {
                grouped + (PHYSICAL to remaining)
            } else {
                grouped
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SymptomSelector(
    selected: Set<String>,
    offeredSymptomIds: List<String>,
    onToggle: (String) -> Unit
) {
    val groups = remember(offeredSymptomIds) {
        SymptomGroup.forOffered(offeredSymptomIds)
    }

    Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
        Text(
            text = stringResource(R.string.editor_symptoms),
            style = MaterialTheme.typography.titleSmall
        )
        groups.forEach { (group, ids) ->
            val symptomsInGroup = ids.mapNotNull { id ->
                symptomLabel(id)?.let { id to it }
            }
            if (symptomsInGroup.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
                    Text(
                        text = stringResource(group.titleRes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs),
                        verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)
                    ) {
                        symptomsInGroup.forEach { (id, label) ->
                            val isSelected = id in selected
                            FilterChip(
                                selected = isSelected,
                                onClick = { onToggle(id) },
                                label = { Text(label, maxLines = 2) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Rounded.Check, contentDescription = null) }
                                } else {
                                    null
                                },
                                shape = MaterialTheme.shapes.small
                            )
                        }
                    }
                }
            }
        }
    }
}
