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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
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
import fr.luteal.core.model.BasalBodyTemperature
import fr.luteal.core.model.BbtDisturbance
import fr.luteal.core.model.BiomarkerObservation
import fr.luteal.core.model.BleedingIntensity
import fr.luteal.core.model.CervicalFluidObservation
import fr.luteal.core.model.CervicalMucusSensation
import fr.luteal.core.model.CervicalMucusTexture
import fr.luteal.core.model.Cycle
import fr.luteal.core.model.DailyEntry
import fr.luteal.core.model.HcgTestResult
import fr.luteal.core.model.LhTestResult
import fr.luteal.core.model.RapidTestLogs
import fr.luteal.core.model.TemperatureInput
import fr.luteal.core.model.TemperatureUnit
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DailyEntrySheet(
    date: LocalDate,
    existingEntry: DailyEntry?,
    existingBiomarker: BiomarkerObservation? = null,
    temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    currentCycle: Cycle?,
    /** Observations offered, derived from declared contexts. */
    offeredSymptomIds: List<String>,
    startPeriodIntent: Boolean,
    isSaving: Boolean,
    saveFailed: Boolean,
    onDismiss: () -> Unit,
    onSave: (DailyEntry, BiomarkerObservation, Boolean) -> Unit
) {
    val stateKey = "${date}-${existingEntry?.updatedAt}-${existingBiomarker?.updatedAt}"
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
    var showBiomarkers by rememberSaveable(stateKey) {
        mutableStateOf(existingBiomarker != null && !existingBiomarker.isEmpty)
    }
    var bbtHundredths by rememberSaveable(stateKey) {
        mutableStateOf(
            existingBiomarker?.bbt?.valueInUnit(temperatureUnit)?.let(TemperatureInput::toHundredths)
        )
    }
    var bbtTime by rememberSaveable(stateKey) {
        mutableStateOf(existingBiomarker?.bbt?.measuredTime?.toString()?.take(5).orEmpty())
    }
    var disturbanceNames by rememberSaveable(stateKey) {
        mutableStateOf(existingBiomarker?.bbt?.disturbances.orEmpty().map { it.name }.sorted())
    }
    var sensationName by rememberSaveable(stateKey) {
        mutableStateOf(existingBiomarker?.cervicalFluid?.sensation?.name)
    }
    var textureName by rememberSaveable(stateKey) {
        mutableStateOf(existingBiomarker?.cervicalFluid?.texture?.name)
    }
    var lhName by rememberSaveable(stateKey) {
        mutableStateOf(existingBiomarker?.rapidTests?.lhTest?.name)
    }
    var hcgName by rememberSaveable(stateKey) {
        mutableStateOf(existingBiomarker?.rapidTests?.hcgTest?.name)
    }
    var showDiscardConfirmation by remember { mutableStateOf(false) }

    val bleeding = bleedingName?.let(BleedingIntensity::valueOf)
    val selectedSymptoms = symptomIds.toSet()
    val currentBiomarker = remember(
        date, bbtHundredths, bbtTime, disturbanceNames, sensationName, textureName, lhName, hcgName, temperatureUnit
    ) {
        val temperature = bbtHundredths?.let { hundredths ->
            BasalBodyTemperature.fromUnit(hundredths / 100.0, temperatureUnit)?.copy(
                measuredTime = bbtTime.takeIf { it.isNotBlank() }?.let {
                    runCatching { LocalTime.parse(it) }.getOrNull()
                },
                disturbances = disturbanceNames.mapNotNull { name ->
                    BbtDisturbance.entries.firstOrNull { it.name == name }
                }.toSet()
            )
        }
        BiomarkerObservation(
            date = date,
            bbt = temperature,
            cervicalFluid = CervicalFluidObservation(
                sensation = sensationName?.let { name ->
                    CervicalMucusSensation.entries.firstOrNull { it.name == name }
                },
                texture = textureName?.let { name ->
                    CervicalMucusTexture.entries.firstOrNull { it.name == name }
                }
            ).takeIf { it.hasObservation },
            rapidTests = RapidTestLogs(
                lhTest = lhName?.let { name -> LhTestResult.entries.firstOrNull { it.name == name } },
                hcgTest = hcgName?.let { name -> HcgTestResult.entries.firstOrNull { it.name == name } }
            ).takeIf { it.hasLogs },
            updatedAt = Instant.now()
        )
    }
    val hasChanges = bleeding != existingEntry?.bleedingIntensity ||
        pain != existingEntry?.painLevel ||
        mood != existingEntry?.moodLevel ||
        energy != existingEntry?.energyLevel ||
        selectedSymptoms != existingEntry?.symptomIds.orEmpty() ||
        notes != existingEntry?.notes.orEmpty() ||
        startsNewCycle != initialStartsNewCycle ||
        !currentBiomarker.sameContentAs(existingBiomarker)

    // Material3's ModalBottomSheet lives in its own dialog window: back presses
    // and gestures there are handled by M3 itself, which hides the sheet first
    // and then invokes onDismissRequest. We therefore gate everything in
    // requestDismiss and restore the sheet from the confirm dialog.

    val locale = LocalConfiguration.current.locales[0]
    val scope = rememberCoroutineScope()
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
                        text = if (showBiomarkers) {
                            stringResource(R.string.editor_biomarkers_hide)
                        } else {
                            stringResource(R.string.editor_biomarkers_show)
                        },
                        onClick = { showBiomarkers = !showBiomarkers },
                        icon = if (showBiomarkers) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (showBiomarkers) {
                    item {
                        BiomarkerEditor(
                            temperatureUnit = temperatureUnit,
                            bbtHundredths = bbtHundredths,
                            onBbtHundredthsChange = { bbtHundredths = it },
                            bbtTime = bbtTime,
                            onBbtTimeChange = { bbtTime = it },
                            disturbanceNames = disturbanceNames.toSet(),
                            onToggleDisturbance = { name ->
                                disturbanceNames = if (name in disturbanceNames) {
                                    (disturbanceNames - name).sorted()
                                } else {
                                    (disturbanceNames + name).sorted()
                                }
                            },
                            sensationName = sensationName,
                            onSensationChange = { sensationName = it },
                            textureName = textureName,
                            onTextureChange = { textureName = it },
                            lhName = lhName,
                            onLhChange = { lhName = it },
                            hcgName = hcgName,
                            onHcgChange = { hcgName = it }
                        )
                    }
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
                            currentBiomarker,
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
                scope.launch { sheetState.show() }
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
                        scope.launch { sheetState.show() }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BiomarkerEditor(
    temperatureUnit: TemperatureUnit,
    bbtHundredths: Int?,
    onBbtHundredthsChange: (Int?) -> Unit,
    bbtTime: String,
    onBbtTimeChange: (String) -> Unit,
    disturbanceNames: Set<String>,
    onToggleDisturbance: (String) -> Unit,
    sensationName: String?,
    onSensationChange: (String?) -> Unit,
    textureName: String?,
    onTextureChange: (String?) -> Unit,
    lhName: String?,
    onLhChange: (String?) -> Unit,
    hcgName: String?,
    onHcgChange: (String?) -> Unit
) {
    val step = TemperatureInput.step(temperatureUnit)
    val defaultHundredths = TemperatureInput.defaultHundredths(temperatureUnit)
    val unitLabel = if (temperatureUnit == TemperatureUnit.CELSIUS) {
        stringResource(R.string.bbt_unit_celsius)
    } else {
        stringResource(R.string.bbt_unit_fahrenheit)
    }
    Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.md)) {
        Text(
            text = stringResource(R.string.biomarker_section_title),
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = stringResource(R.string.bbt_title),
            style = MaterialTheme.typography.labelLarge
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LutealSpacing.sm)
        ) {
            IconButton(
                onClick = {
                    val current = bbtHundredths ?: defaultHundredths
                    onBbtHundredthsChange(TemperatureInput.clampHundredths(current - step, temperatureUnit))
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.bbt_decrease))
            }
            Text(
                text = bbtHundredths?.let {
                    String.format(java.util.Locale.getDefault(), "%.2f %s", it / 100.0, unitLabel)
                } ?: stringResource(R.string.bbt_unset),
                style = MaterialTheme.typography.titleMedium
            )
            IconButton(
                onClick = {
                    val current = bbtHundredths ?: defaultHundredths
                    onBbtHundredthsChange(TemperatureInput.clampHundredths(current + step, temperatureUnit))
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.bbt_increase))
            }
            TextButton(onClick = { onBbtHundredthsChange(null) }) {
                Text(stringResource(R.string.bbt_clear))
            }
        }
        OutlinedTextField(
            value = bbtTime,
            onValueChange = { onBbtTimeChange(it.take(5)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.bbt_time_label)) },
            placeholder = { Text(stringResource(R.string.bbt_time_placeholder)) },
            singleLine = true
        )
        Text(
            text = stringResource(R.string.bbt_disturbances_title),
            style = MaterialTheme.typography.labelMedium
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)
        ) {
            BbtDisturbance.entries.forEach { disturbance ->
                val selected = disturbance.name in disturbanceNames
                FilterChip(
                    selected = selected,
                    onClick = { onToggleDisturbance(disturbance.name) },
                    label = { Text(disturbanceLabel(disturbance)) },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null) }
                    } else null,
                    shape = MaterialTheme.shapes.small
                )
            }
        }
        Text(
            text = stringResource(R.string.cervical_fluid_title),
            style = MaterialTheme.typography.labelLarge
        )
        Text(stringResource(R.string.cervical_sensation_header), style = MaterialTheme.typography.labelMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)
        ) {
            CervicalMucusSensation.entries.forEach { sensation ->
                val selected = sensationName == sensation.name
                FilterChip(
                    selected = selected,
                    onClick = { onSensationChange(if (selected) null else sensation.name) },
                    label = { Text(sensationLabel(sensation)) },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null) }
                    } else null,
                    shape = MaterialTheme.shapes.small
                )
            }
        }
        Text(stringResource(R.string.cervical_texture_header), style = MaterialTheme.typography.labelMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)
        ) {
            CervicalMucusTexture.entries.forEach { texture ->
                val selected = textureName == texture.name
                FilterChip(
                    selected = selected,
                    onClick = { onTextureChange(if (selected) null else texture.name) },
                    label = { Text(textureLabel(texture)) },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null) }
                    } else null,
                    shape = MaterialTheme.shapes.small
                )
            }
        }
        Text(
            text = stringResource(R.string.rapid_tests_title),
            style = MaterialTheme.typography.labelLarge
        )
        Text(stringResource(R.string.lh_test_title), style = MaterialTheme.typography.labelMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)
        ) {
            LhTestResult.entries.forEach { result ->
                val selected = lhName == result.name
                FilterChip(
                    selected = selected,
                    onClick = { onLhChange(if (selected) null else result.name) },
                    label = { Text(lhLabel(result)) },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null) }
                    } else null,
                    shape = MaterialTheme.shapes.small
                )
            }
        }
        Text(stringResource(R.string.hcg_test_title), style = MaterialTheme.typography.labelMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)
        ) {
            HcgTestResult.entries.forEach { result ->
                val selected = hcgName == result.name
                FilterChip(
                    selected = selected,
                    onClick = { onHcgChange(if (selected) null else result.name) },
                    label = { Text(hcgLabel(result)) },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null) }
                    } else null,
                    shape = MaterialTheme.shapes.small
                )
            }
        }
    }
}

@Composable
private fun disturbanceLabel(disturbance: BbtDisturbance): String = stringResource(
    when (disturbance) {
        BbtDisturbance.FEVER -> R.string.disturbance_fever
        BbtDisturbance.ALCOHOL -> R.string.disturbance_alcohol
        BbtDisturbance.POOR_SLEEP -> R.string.disturbance_poor_sleep
        BbtDisturbance.TIME_SHIFT -> R.string.disturbance_time_shift
        BbtDisturbance.LATE_MEASUREMENT -> R.string.disturbance_late_measurement
        BbtDisturbance.STRESS -> R.string.disturbance_stress
        BbtDisturbance.MEDICATION -> R.string.disturbance_medication
    }
)

@Composable
private fun sensationLabel(sensation: CervicalMucusSensation): String = stringResource(
    when (sensation) {
        CervicalMucusSensation.DRY -> R.string.sensation_dry
        CervicalMucusSensation.DAMP -> R.string.sensation_damp
        CervicalMucusSensation.WET -> R.string.sensation_wet
        CervicalMucusSensation.SLIPPERY -> R.string.sensation_slippery
    }
)

@Composable
private fun textureLabel(texture: CervicalMucusTexture): String = stringResource(
    when (texture) {
        CervicalMucusTexture.STICKY -> R.string.texture_sticky
        CervicalMucusTexture.CREAMY -> R.string.texture_creamy
        CervicalMucusTexture.EGG_WHITE -> R.string.texture_egg_white
        CervicalMucusTexture.WATERY -> R.string.texture_watery
    }
)

@Composable
private fun lhLabel(result: LhTestResult): String = stringResource(
    when (result) {
        LhTestResult.NEGATIVE -> R.string.lh_negative
        LhTestResult.LOW -> R.string.lh_low
        LhTestResult.PEAK_POSITIVE -> R.string.lh_peak_positive
        LhTestResult.INDETERMINATE -> R.string.lh_indeterminate
    }
)

@Composable
private fun hcgLabel(result: HcgTestResult): String = stringResource(
    when (result) {
        HcgTestResult.NEGATIVE -> R.string.hcg_negative
        HcgTestResult.POSITIVE -> R.string.hcg_positive
        HcgTestResult.FAINT_UNCERTAIN -> R.string.hcg_faint_uncertain
    }
)
