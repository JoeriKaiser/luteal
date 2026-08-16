package fr.luteal.app.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.luteal.app.BuildConfig
import fr.luteal.app.R
import fr.luteal.core.common.LocalizedDateFormatter
import fr.luteal.core.designsystem.component.LutealCard
import fr.luteal.core.designsystem.component.LutealCheckboxRow
import fr.luteal.core.designsystem.component.LutealPrimaryButton
import fr.luteal.core.designsystem.component.LutealRadioRow
import fr.luteal.core.designsystem.component.LutealSecondaryButton
import fr.luteal.core.designsystem.component.LutealToggleRow
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.model.AgeBand
import fr.luteal.core.model.ContextGroup
import fr.luteal.core.model.TrackingContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val syncState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showWipeConfirmDialog by remember { mutableStateOf(false) }

    val exportDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportData(context, uri)
        }
    }

    if (showWipeConfirmDialog) {
        ClearAllDataDialog(
            onConfirm = {
                showWipeConfirmDialog = false
                viewModel.purgeAllLocalData()
            },
            onDismiss = { showWipeConfirmDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = LutealSpacing.md, vertical = LutealSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(LutealSpacing.md)
    ) {
        ScreenHeader(
            title = stringResource(R.string.settings_title),
            subtitle = stringResource(R.string.settings_subtitle)
        )

        SettingsSectionHeader(title = stringResource(R.string.settings_privacy_title))

        LutealCard(modifier = Modifier.fillMaxWidth()) {
            SettingsInformationRow(
                icon = Icons.Rounded.PhoneAndroid,
                title = stringResource(R.string.settings_storage_title),
                body = stringResource(R.string.settings_storage_body)
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsInformationRow(
                icon = Icons.Rounded.Shield,
                title = stringResource(R.string.settings_backup_title),
                body = stringResource(R.string.settings_backup_body)
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsInformationRow(
                icon = Icons.Rounded.VisibilityOff,
                title = stringResource(R.string.settings_widgets_privacy_title),
                body = stringResource(R.string.widget_privacy_notice)
            )
        }

        // Online cloud sync, available in every build. The base-URL editor
        // inside the card and the demo-data tools below stay debug-only; the
        // release build syncs against the production API over HTTPS.
        SyncCard(
            state = syncState,
            onToggleOnline = viewModel::setOnlineSyncEnabled,
            onBaseUrlChange = viewModel::onBaseUrlChange,
            onSave = viewModel::saveSyncSettings,
            onSyncNow = viewModel::syncNow,
            getAccountCode = viewModel::getAccountCode,
            onRecoveryCodeChange = viewModel::onRecoveryCodeChange,
            onRecoverAccount = viewModel::recoverAccount,
            onDismissRecoveryMessage = viewModel::clearRecoveryState
        )
        if (BuildConfig.DEBUG) {
            TestDataCard(
                state = syncState.testDataState,
                onSeed = viewModel::seedMockData,
                onClear = viewModel::clearTestData,
                onDismissMessage = viewModel::clearTestDataMessage
            )
        }

        SettingsSectionHeader(title = stringResource(R.string.settings_data_management_title))

        DataManagementCard(
            exportState = syncState.exportState,
            wipeState = syncState.wipeState,
            onExportRequested = {
                exportDocumentLauncher.launch("luteal_backup_${LocalDate.now()}.json")
            },
            onWipeRequested = { showWipeConfirmDialog = true },
            onDismissExportState = viewModel::clearExportState,
            onDismissWipeState = viewModel::clearWipeState
        )

        TrackingContextCard(
            declared = syncState.declaredContexts,
            ageBand = syncState.ageBand,
            onToggleContext = viewModel::setTrackingContext,
            onSelectAgeBand = viewModel::setAgeBand
        )

        SettingsSectionHeader(title = stringResource(R.string.settings_appearance_title))

        LutealCard(modifier = Modifier.fillMaxWidth()) {
            SettingsInformationRow(
                icon = Icons.Rounded.DarkMode,
                title = stringResource(R.string.settings_appearance_title),
                body = stringResource(R.string.settings_appearance_body)
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsInformationRow(
                icon = Icons.Rounded.HealthAndSafety,
                title = stringResource(R.string.settings_about_title),
                body = stringResource(R.string.settings_about_body)
            )
        }
        Spacer(Modifier.height(LutealSpacing.md))
    }
}

/**
 * Declared contexts and age band, editable after onboarding.
 *
 * Both change over time, and anyone who skipped the introduction never set
 * them at all, so leaving them write-once made the onboarding step a one-shot
 * question about something inherently revisable.
 *
 * The copy states what each group actually does, because a user cannot
 * otherwise tell why one checkbox changes their estimates and another changes
 * the editor.
 */
@Composable
private fun TrackingContextCard(
    declared: Set<TrackingContext>,
    ageBand: AgeBand?,
    onToggleContext: (TrackingContext, Boolean) -> Unit,
    onSelectAgeBand: (AgeBand?) -> Unit
) {
    var showAgePicker by remember { mutableStateOf(false) }

    SettingsSectionHeader(title = stringResource(R.string.settings_contexts_title))

    LutealCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_contexts_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(LutealSpacing.sm))

        TrackingContext.entries.forEach { context ->
            LutealCheckboxRow(
                title = stringResource(trackingContextLabel(context)),
                description = stringResource(contextGroupExplanation(context.group)),
                checked = context in declared,
                onCheckedChange = { onToggleContext(context, it) }
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(LutealSpacing.sm))

        Text(
            text = stringResource(R.string.settings_age_band_title),
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(Modifier.height(LutealSpacing.xxs))
        Text(
            text = stringResource(R.string.settings_age_band_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(LutealSpacing.xs))
        LutealSecondaryButton(
            text = ageBand?.let { stringResource(settingsAgeBandLabel(it)) }
                ?: stringResource(R.string.settings_age_band_none),
            onClick = { showAgePicker = true },
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (showAgePicker) {
        AgeBandPickerDialog(
            selected = ageBand,
            onSelect = {
                onSelectAgeBand(it)
                showAgePicker = false
            },
            onDismiss = { showAgePicker = false }
        )
    }
}

@Composable
private fun AgeBandPickerDialog(
    selected: AgeBand?,
    onSelect: (AgeBand?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_age_band_title)) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .selectableGroup()
            ) {
                AgeBand.entries.forEach { band ->
                    LutealRadioRow(
                        title = stringResource(settingsAgeBandLabel(band)),
                        selected = selected == band,
                        onClick = { onSelect(band) }
                    )
                }
                LutealRadioRow(
                    title = stringResource(R.string.onboarding_age_skip),
                    selected = selected == null,
                    onClick = { onSelect(null) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}

@StringRes
private fun trackingContextLabel(context: TrackingContext): Int = when (context) {
    TrackingContext.PMS -> R.string.onboarding_focus_pms
    TrackingContext.PMDD -> R.string.onboarding_focus_pmdd
    TrackingContext.ENDOMETRIOSIS -> R.string.onboarding_focus_endometriosis
    TrackingContext.PCOS -> R.string.onboarding_focus_pcos
    TrackingContext.PERIMENOPAUSE -> R.string.onboarding_focus_perimenopause
    TrackingContext.THYROID -> R.string.onboarding_focus_thyroid
}

@StringRes
private fun contextGroupExplanation(group: ContextGroup): Int = when (group) {
    ContextGroup.TIMING -> R.string.settings_context_group_timing
    ContextGroup.OBSERVATION -> R.string.settings_context_group_observation
}

@StringRes
private fun settingsAgeBandLabel(band: AgeBand): Int = when (band) {
    AgeBand.UNDER_20 -> R.string.age_band_under_20
    AgeBand.AGE_20_24 -> R.string.age_band_20_24
    AgeBand.AGE_25_29 -> R.string.age_band_25_29
    AgeBand.AGE_30_34 -> R.string.age_band_30_34
    AgeBand.AGE_35_39 -> R.string.age_band_35_39
    AgeBand.AGE_40_44 -> R.string.age_band_40_44
    AgeBand.AGE_45_49 -> R.string.age_band_45_49
    AgeBand.AGE_50_PLUS -> R.string.age_band_50_plus
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = LutealSpacing.xs)
    )
}

@Composable
private fun TestDataCard(
    state: TestDataActionState,
    onSeed: () -> Unit,
    onClear: () -> Unit,
    onDismissMessage: () -> Unit
) {
    var showClearConfirmation by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
        SettingsSectionHeader(title = stringResource(R.string.settings_test_data_title))

        LutealCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)
            ) {
                Text(
                    text = stringResource(R.string.settings_test_data_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LutealPrimaryButton(
                    text = stringResource(R.string.settings_test_data_seed_button),
                    onClick = {
                        onDismissMessage()
                        onSeed()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    loading = state is TestDataActionState.Loading,
                    enabled = state !is TestDataActionState.Loading
                )

                LutealSecondaryButton(
                    text = stringResource(R.string.settings_test_data_clear_button),
                    onClick = { showClearConfirmation = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state !is TestDataActionState.Loading
                )

                when (state) {
                    is TestDataActionState.SuccessSeeded -> Text(
                        text = stringResource(R.string.settings_test_data_success),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    is TestDataActionState.SuccessCleared -> Text(
                        text = stringResource(R.string.settings_test_data_cleared),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    is TestDataActionState.Error -> {
                        val errorMsg = state.messageResId?.let { stringResource(it) } ?: state.message.orEmpty()
                        Text(
                            text = stringResource(R.string.settings_test_data_error, errorMsg),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> { /* Idle or Loading: no message */ }
                }
            }
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text(stringResource(R.string.settings_test_data_clear_title)) },
            text = { Text(stringResource(R.string.settings_test_data_clear_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmation = false
                        onDismissMessage()
                        onClear()
                    }
                ) {
                    Text(stringResource(R.string.settings_test_data_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text(stringResource(R.string.settings_test_data_keep))
                }
            }
        )
    }
}
@Composable
private fun SyncCard(
    state: SettingsSyncUiState,
    onToggleOnline: (Boolean) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onSave: () -> Unit,
    onSyncNow: () -> Unit,
    getAccountCode: () -> String?,
    onRecoveryCodeChange: (String) -> Unit,
    onRecoverAccount: () -> Unit,
    onDismissRecoveryMessage: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
        SettingsSectionHeader(title = stringResource(R.string.settings_sync_title))

        LutealCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
                Text(
                    text = stringResource(R.string.settings_sync_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Stated before the toggle: enabling sync is the moment data
                // leaves the device.
                Text(
                    text = stringResource(R.string.sync_transport_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OnlineSyncToggle(
                    enabled = state.onlineSyncEnabled,
                    onToggle = onToggleOnline
                )

                if (state.onlineSyncEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Text(
                        text = stringResource(R.string.settings_sync_server_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = state.baseUrlDraft,
                        onValueChange = onBaseUrlChange,
                        label = { Text(stringResource(R.string.settings_sync_base_url_label)) },
                        placeholder = { Text(BuildConfig.SYNC_BASE_URL) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "${stringResource(R.string.settings_sync_base_url_label)} : ${state.storedBaseUrl.ifBlank { BuildConfig.SYNC_BASE_URL }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(LutealSpacing.sm)
                    ) {
                        LutealSecondaryButton(
                            text = stringResource(R.string.settings_sync_save),
                            onClick = onSave,
                            modifier = Modifier.weight(1f)
                        )

                        LutealPrimaryButton(
                            text = stringResource(R.string.settings_sync_now),
                            onClick = onSyncNow,
                            icon = Icons.Rounded.Sync,
                            loading = state.inProgress,
                            enabled = !state.inProgress,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                SyncStatusText(state = state)

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                AccountCodeSection(state = state, getAccountCode = getAccountCode)

                if (!state.hasAccount) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AccountRecoverySection(
                        state = state,
                        onCodeChange = onRecoveryCodeChange,
                        onRecover = onRecoverAccount,
                        onDismissMessage = onDismissRecoveryMessage
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountCodeSection(state: SettingsSyncUiState, getAccountCode: () -> String?) {
    // Re-read whenever the account or the last sync changes. A bare
    // `remember {}` latched the value from first composition, so registering
    // while sitting on this screen left "aucun compte enregistré" on display
    // until the user switched tabs.
    val accountCode = remember(state.hasAccount, state.lastSyncedEpochMillis) {
        getAccountCode()
    }
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
        Text(
            text = stringResource(R.string.settings_sync_account_code_title),
            style = MaterialTheme.typography.titleMedium
        )
        if (accountCode == null) {
            Text(
                text = stringResource(R.string.settings_sync_account_code_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = stringResource(R.string.settings_sync_account_code_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = accountCode,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = LutealSpacing.xxs)
            )
            LutealSecondaryButton(
                text = if (copied) stringResource(R.string.settings_sync_account_code_copied)
                    else stringResource(R.string.settings_sync_account_code_copy),
                onClick = {
                    clipboardManager.setText(AnnotatedString(accountCode))
                    copied = true
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Attaches this device to an existing account.
 *
 * Shown only before this device has credentials. The account code is the root
 * of the key hierarchy: without it a reinstall cannot decrypt anything the
 * server holds, because the server has no key that could substitute for it.
 */
@Composable
private fun AccountRecoverySection(
    state: SettingsSyncUiState,
    onCodeChange: (String) -> Unit,
    onRecover: () -> Unit,
    onDismissMessage: () -> Unit
) {
    if (state.hasAccount) return

    Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
        Text(
            text = stringResource(R.string.settings_recovery_title),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(R.string.settings_recovery_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = state.recoveryCodeDraft,
            onValueChange = onCodeChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.settings_recovery_label)) },
            placeholder = { Text("LTL-XXXXX-XXXXX-XXXXX-XXXXX") }
        )
        LutealSecondaryButton(
            text = stringResource(R.string.settings_recovery_action),
            onClick = onRecover,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.recoveryCodeDraft.isNotBlank() &&
                state.recoveryState !is RecoveryState.Loading
        )

        when (val recovery = state.recoveryState) {
            is RecoveryState.Loading -> Text(
                text = stringResource(R.string.settings_recovery_in_progress),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            is RecoveryState.Success -> Text(
                text = stringResource(R.string.settings_recovery_success),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            is RecoveryState.Error -> Column(
                verticalArrangement = Arrangement.spacedBy(LutealSpacing.xxs)
            ) {
                val errorMsg = recovery.messageResId?.let { stringResource(it) } ?: recovery.message.orEmpty()
                Text(
                    text = errorMsg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(onClick = onDismissMessage) {
                    Text(text = stringResource(R.string.action_close))
                }
            }
            RecoveryState.Idle -> Unit
        }
    }
}

@Composable
private fun OnlineSyncToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    LutealToggleRow(
        title = stringResource(R.string.settings_sync_online_title),
        description = stringResource(R.string.settings_sync_online_body),
        checked = enabled,
        onCheckedChange = onToggle
    )
}

@Composable
private fun SyncStatusText(state: SettingsSyncUiState) {
    val locale = LocalConfiguration.current.locales[0]
    val text = when {
        state.inProgress -> stringResource(R.string.settings_sync_in_progress)
        state.lastError != null -> stringResource(R.string.settings_sync_last_error, state.lastError)
        state.lastSyncedEpochMillis != null -> stringResource(
            R.string.settings_sync_last_success,
            formatSyncInstant(state.lastSyncedEpochMillis, locale)
        )
        else -> stringResource(R.string.settings_sync_idle)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun formatSyncInstant(epochMillis: Long, locale: Locale): String {
    val date: LocalDate = Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    return LocalizedDateFormatter.formatFullDate(date, locale)
}

@Composable
private fun SettingsInformationRow(
    icon: ImageVector,
    title: String,
    body: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = LutealSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(LutealSpacing.md),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DataManagementCard(
    exportState: DataExportState,
    wipeState: DataWipeState,
    onExportRequested: () -> Unit,
    onWipeRequested: () -> Unit,
    onDismissExportState: () -> Unit,
    onDismissWipeState: () -> Unit
) {
    LutealCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
            Text(
                text = stringResource(R.string.settings_export_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.settings_export_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LutealSecondaryButton(
                text = when (exportState) {
                    is DataExportState.Loading -> stringResource(R.string.settings_export_in_progress)
                    else -> stringResource(R.string.settings_export_action)
                },
                onClick = onExportRequested,
                modifier = Modifier.fillMaxWidth(),
                enabled = exportState !is DataExportState.Loading
            )
            when (exportState) {
                is DataExportState.Success -> Text(
                    text = stringResource(R.string.settings_export_success),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                is DataExportState.Error -> Column(
                    verticalArrangement = Arrangement.spacedBy(LutealSpacing.xxs)
                ) {
                    Text(
                        text = stringResource(R.string.settings_export_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = onDismissExportState) {
                        Text(text = stringResource(R.string.action_close))
                    }
                }
                else -> Unit
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(
                text = stringResource(R.string.settings_wipe_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.settings_wipe_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LutealSecondaryButton(
                text = when (wipeState) {
                    is DataWipeState.Loading -> stringResource(R.string.settings_wipe_in_progress)
                    else -> stringResource(R.string.settings_wipe_action)
                },
                onClick = onWipeRequested,
                modifier = Modifier.fillMaxWidth(),
                enabled = wipeState !is DataWipeState.Loading
            )
            when (wipeState) {
                is DataWipeState.Success -> Text(
                    text = stringResource(R.string.settings_wipe_success),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                is DataWipeState.Error -> Column(
                    verticalArrangement = Arrangement.spacedBy(LutealSpacing.xxs)
                ) {
                    Text(
                        text = stringResource(R.string.settings_wipe_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = onDismissWipeState) {
                        Text(text = stringResource(R.string.action_close))
                    }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun ClearAllDataDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.settings_wipe_dialog_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = stringResource(R.string.settings_wipe_dialog_body),
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
                Text(
                    text = stringResource(R.string.settings_wipe_dialog_confirm),
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
