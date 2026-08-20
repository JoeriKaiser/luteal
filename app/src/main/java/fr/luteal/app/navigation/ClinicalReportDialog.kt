package fr.luteal.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import fr.luteal.app.R
import fr.luteal.core.designsystem.component.LutealRadioRow
import fr.luteal.core.designsystem.component.LutealToggleRow
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.model.ClinicalReportConfig
import fr.luteal.core.model.ReportDateRangePreset
import fr.luteal.core.model.ReportFormat
import fr.luteal.core.model.ReportLanguage

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClinicalReportDialog(
    onDismiss: () -> Unit,
    onExport: (ClinicalReportConfig) -> Unit
) {
    var selectedPreset by remember { mutableStateOf(ReportDateRangePreset.LAST_6_CYCLES) }
    var selectedFormat by remember { mutableStateOf(ReportFormat.PDF) }
    var selectedLanguage by remember { mutableStateOf(ReportLanguage.FRENCH) }
    var includeNotes by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.report_dialog_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)
            ) {
                Text(
                    text = stringResource(R.string.report_dialog_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = stringResource(R.string.report_preset_label),
                    style = MaterialTheme.typography.titleSmall
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(LutealSpacing.xxs)
                ) {
                    listOf(
                        ReportDateRangePreset.LAST_3_CYCLES to stringResource(R.string.report_preset_3_cycles),
                        ReportDateRangePreset.LAST_6_CYCLES to stringResource(R.string.report_preset_6_cycles),
                        ReportDateRangePreset.LAST_12_CYCLES to stringResource(R.string.report_preset_12_cycles),
                        ReportDateRangePreset.ALL_CYCLES to stringResource(R.string.report_preset_all)
                    ).forEach { (preset, label) ->
                        FilterChip(
                            selected = selectedPreset == preset,
                            onClick = { selectedPreset = preset },
                            label = { Text(label) }
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    text = stringResource(R.string.report_format_label),
                    style = MaterialTheme.typography.titleSmall
                )

                LutealRadioRow(
                    title = stringResource(R.string.report_format_pdf),
                    selected = selectedFormat == ReportFormat.PDF,
                    onClick = { selectedFormat = ReportFormat.PDF }
                )

                LutealRadioRow(
                    title = stringResource(R.string.report_format_html),
                    selected = selectedFormat == ReportFormat.HTML,
                    onClick = { selectedFormat = ReportFormat.HTML }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    text = stringResource(R.string.report_language_label),
                    style = MaterialTheme.typography.titleSmall
                )

                Row(horizontalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
                    FilterChip(
                        selected = selectedLanguage == ReportLanguage.FRENCH,
                        onClick = { selectedLanguage = ReportLanguage.FRENCH },
                        label = { Text(stringResource(R.string.report_language_fr)) }
                    )
                    FilterChip(
                        selected = selectedLanguage == ReportLanguage.ENGLISH,
                        onClick = { selectedLanguage = ReportLanguage.ENGLISH },
                        label = { Text(stringResource(R.string.report_language_en)) }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                LutealToggleRow(
                    title = stringResource(R.string.report_include_notes_title),
                    description = stringResource(R.string.report_include_notes_desc),
                    checked = includeNotes,
                    onCheckedChange = { includeNotes = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val config = ClinicalReportConfig(
                        preset = selectedPreset,
                        includeNotes = includeNotes,
                        language = selectedLanguage,
                        format = selectedFormat
                    )
                    onExport(config)
                }
            ) {
                Text(
                    text = stringResource(R.string.report_export_action),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        }
    )
}
