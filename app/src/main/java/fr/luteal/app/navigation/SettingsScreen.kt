package fr.luteal.app.navigation

import android.os.Build
import androidx.core.content.ContextCompat
import fr.luteal.core.model.NotificationVisibility
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import fr.luteal.core.model.AutoLockTimeout
import fr.luteal.core.model.ClinicalReportConfig
import fr.luteal.core.model.ContextGroup
import fr.luteal.core.model.ImportStrategy
import fr.luteal.core.model.LutealBackupPreview
import fr.luteal.core.model.ReportDateRangePreset
import fr.luteal.core.model.ReportFormat
import fr.luteal.core.model.ReportLanguage
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
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.setNotificationsEnabled(true)
        }
    }

    val onToggleNotificationsMaster: (Boolean) -> Unit = { enabled ->
        if (enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    viewModel.setNotificationsEnabled(true)
                } else {
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                viewModel.setNotificationsEnabled(true)
            }
        } else {
            viewModel.setNotificationsEnabled(false)
        }
    }
    var showWipeConfirmDialog by remember { mutableStateOf(false) }
    var showSetPinDialog by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var showDisableLockDialog by remember { mutableStateOf(false) }
    var showTimeoutDialog by remember { mutableStateOf(false) }
    var showClinicalReportDialog by remember { mutableStateOf(false) }
    var pendingReportConfig by remember { mutableStateOf<ClinicalReportConfig?>(null) }

    val exportDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportData(context, uri)
        }
    }

    val importDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.inspectBackup(context, uri)
        }
    }

    val reportPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val config = pendingReportConfig
        if (uri != null && config != null) {
            viewModel.exportClinicalReport(context, uri, config)
        }
        pendingReportConfig = null
    }
    val reportHtmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/html")
    ) { uri ->
        val config = pendingReportConfig
        if (uri != null && config != null) {
            viewModel.exportClinicalReport(context, uri, config)
        }
        pendingReportConfig = null
    }

    val currentImportState = syncState.importState
    if (currentImportState is DataImportState.PreviewReady) {
        ImportBackupDialog(
            preview = currentImportState.preview,
            onConfirm = { strategy ->
                viewModel.confirmRestore(currentImportState.payload, strategy)
            },
            onDismiss = viewModel::dismissImportPreview
        )
    }

    if (showClinicalReportDialog) {
        ClinicalReportDialog(
            onDismiss = { showClinicalReportDialog = false },
            onExport = { config ->
                showClinicalReportDialog = false
                pendingReportConfig = config
                val ext = if (config.format == ReportFormat.HTML) "html" else "pdf"
                val name = "luteal_recapitulatif_medical_${LocalDate.now()}.$ext"
                if (config.format == ReportFormat.HTML) {
                    reportHtmlLauncher.launch(name)
                } else {
                    reportPdfLauncher.launch(name)
                }
            }
        )
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

    if (showSetPinDialog) {
        SetPinDialog(
            onConfirm = { pin ->
                showSetPinDialog = false
                viewModel.setAppLockEnabledWithPin(pin)
            },
            onDismiss = { showSetPinDialog = false }
        )
    }

    if (showChangePinDialog) {
        ChangePinDialog(
            onVerifyCurrent = viewModel::verifyPin,
            onConfirmNew = { newPin ->
                showChangePinDialog = false
                viewModel.setAppLockEnabledWithPin(newPin)
            },
            onDismiss = { showChangePinDialog = false }
        )
    }

    if (showDisableLockDialog) {
        DisableLockDialog(
            onConfirm = { currentPin ->
                val success = viewModel.disableAppLock(currentPin)
                if (success) {
                    showDisableLockDialog = false
                }
                success
            },
            onDismiss = { showDisableLockDialog = false }
        )
    }

    if (showTimeoutDialog) {
        AutoLockTimeoutDialog(
            currentTimeout = syncState.autoLockTimeout,
            onSelect = viewModel::setAutoLockTimeout,
            onDismiss = { showTimeoutDialog = false }
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

        // Online cloud sync
        SyncCard(
            state = syncState,
            onToggleOnline = viewModel::setOnlineSyncEnabled,
            onBaseUrlChange = viewModel::onBaseUrlChange,
            onSave = viewModel::saveSyncSettings,
            onSyncNow = viewModel::syncNow,
            getAccountCode = viewModel::getAccountCode,
            onRecoveryCodeChange = viewModel::onRecoveryCodeChange,
            onRecoverAccount = viewModel::recoverAccount,
            onDismissRecoveryMessage = viewModel::clearRecoveryState,
            onUnlinkSync = viewModel::unlinkSync
        )
        if (BuildConfig.DEBUG) {
            TestDataCard(
                state = syncState.testDataState,
                onSeed = viewModel::seedMockData,
                onClear = viewModel::clearTestData,
                onDismissMessage = viewModel::clearTestDataMessage
            )
        }

        SecurityCard(
            isAppLockEnabled = syncState.isAppLockEnabled,
            isBiometricEnabled = syncState.isBiometricEnabled,
            isBiometricHardwareAvailable = syncState.isBiometricHardwareAvailable,
            autoLockTimeout = syncState.autoLockTimeout,
            isScreenMaskingEnabled = syncState.isScreenMaskingEnabled,
            onToggleAppLock = { enable ->
                if (enable) {
                    showSetPinDialog = true
                } else {
                    showDisableLockDialog = true
                }
            },
            onToggleBiometric = viewModel::setBiometricEnabled,
            onOpenTimeoutSelector = { showTimeoutDialog = true },
            onOpenChangePin = { showChangePinDialog = true },
            onToggleScreenMasking = viewModel::setScreenMaskingEnabled
        )

        NotificationSettingsCard(
            state = syncState,
            onToggleMaster = onToggleNotificationsMaster,
            onToggleDaily = viewModel::setDailyCheckInEnabled,
            onDailyTimeChange = viewModel::setDailyCheckInTime,
            onToggleWindow = viewModel::setPeriodWindowNotificationEnabled,
            onWindowLeadDaysChange = viewModel::setPeriodWindowLeadDays,
            onToggleLate = viewModel::setLateCycleNotificationEnabled,
            onVisibilityChange = viewModel::setNotificationVisibilityMode,
            onCustomTitleChange = viewModel::setNotificationCustomTitle,
            onCustomBodyChange = viewModel::setNotificationCustomBody
        )

        SettingsSectionHeader(title = stringResource(R.string.settings_data_management_title))

        DataManagementCard(
            exportState = syncState.exportState,
            wipeState = syncState.wipeState,
            importState = syncState.importState,
            reportExportState = syncState.reportExportState,
            onExportRequested = {
                exportDocumentLauncher.launch("luteal_backup_${LocalDate.now()}.json")
            },
            onImportRequested = {
                importDocumentLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
            },
            onClinicalReportRequested = { showClinicalReportDialog = true },
            onWipeRequested = { showWipeConfirmDialog = true },
            onDismissExportState = viewModel::clearExportState,
            onDismissWipeState = viewModel::clearWipeState,
            onDismissImportState = viewModel::clearImportState,
            onDismissReportExportState = viewModel::clearReportExportState
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
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = stringResource(R.string.settings_temperature_unit_title),
                style = MaterialTheme.typography.titleMedium
            )
            LutealRadioRow(
                title = stringResource(R.string.bbt_unit_celsius),
                selected = syncState.temperatureUnit == "CELSIUS",
                onClick = { viewModel.setTemperatureUnit("CELSIUS") }
            )
            LutealRadioRow(
                title = stringResource(R.string.bbt_unit_fahrenheit),
                selected = syncState.temperatureUnit == "FAHRENHEIT",
                onClick = { viewModel.setTemperatureUnit("FAHRENHEIT") }
            )
        }
        Spacer(Modifier.height(LutealSpacing.md))
    }
}

@Composable
private fun SecurityCard(
    isAppLockEnabled: Boolean,
    isBiometricEnabled: Boolean,
    isBiometricHardwareAvailable: Boolean,
    autoLockTimeout: AutoLockTimeout,
    isScreenMaskingEnabled: Boolean,
    onToggleAppLock: (Boolean) -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onOpenTimeoutSelector: () -> Unit,
    onOpenChangePin: () -> Unit,
    onToggleScreenMasking: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
        SettingsSectionHeader(title = stringResource(R.string.settings_security_title))

        LutealCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
                LutealToggleRow(
                    title = stringResource(R.string.settings_app_lock_title),
                    description = stringResource(R.string.settings_app_lock_desc),
                    checked = isAppLockEnabled,
                    onCheckedChange = onToggleAppLock
                )

                if (isAppLockEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    LutealToggleRow(
                        title = stringResource(R.string.settings_biometric_title),
                        description = if (isBiometricHardwareAvailable) {
                            stringResource(R.string.settings_biometric_desc)
                        } else {
                            stringResource(R.string.settings_biometric_unavailable)
                        },
                        checked = isBiometricEnabled,
                        onCheckedChange = onToggleBiometric,
                        modifier = Modifier.let {
                            if (!isBiometricHardwareAvailable) it.alpha(0.5f) else it
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenTimeoutSelector)
                            .padding(vertical = LutealSpacing.xs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_timeout_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = when (autoLockTimeout) {
                                    AutoLockTimeout.IMMEDIATE -> stringResource(R.string.settings_timeout_immediate)
                                    AutoLockTimeout.ONE_MINUTE -> stringResource(R.string.settings_timeout_1min)
                                    AutoLockTimeout.FIVE_MINUTES -> stringResource(R.string.settings_timeout_5min)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    LutealSecondaryButton(
                        text = stringResource(R.string.settings_change_pin_action),
                        onClick = onOpenChangePin,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                LutealToggleRow(
                    title = stringResource(R.string.settings_screen_masking_title),
                    description = stringResource(R.string.settings_screen_masking_desc),
                    checked = isScreenMaskingEnabled,
                    onCheckedChange = onToggleScreenMasking
                )
            }
        }
    }
}

@Composable
private fun SetPinDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_set_pin_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
                Text(
                    text = stringResource(R.string.dialog_set_pin_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) pin = it },
                    label = { Text(stringResource(R.string.dialog_set_pin_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) confirmPin = it },
                    label = { Text(stringResource(R.string.dialog_set_pin_confirm_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            val pinLengthError = stringResource(R.string.dialog_set_pin_error_length)
            val pinMatchError = stringResource(R.string.dialog_set_pin_error_match)
            TextButton(
                onClick = {
                    if (pin.length < 4) {
                        error = pinLengthError
                    } else if (pin != confirmPin) {
                        error = pinMatchError
                    } else {
                        onConfirm(pin)
                    }
                }
            ) {
                Text(stringResource(R.string.dialog_set_pin_action), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotificationSettingsCard(
    state: SettingsSyncUiState,
    onToggleMaster: (Boolean) -> Unit,
    onToggleDaily: (Boolean) -> Unit,
    onDailyTimeChange: (String) -> Unit,
    onToggleWindow: (Boolean) -> Unit,
    onWindowLeadDaysChange: (Int) -> Unit,
    onToggleLate: (Boolean) -> Unit,
    onVisibilityChange: (NotificationVisibility) -> Unit,
    onCustomTitleChange: (String) -> Unit,
    onCustomBodyChange: (String) -> Unit
) {
    var showTimeDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
        SettingsSectionHeader(title = stringResource(R.string.settings_notifications_title))

        LutealCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
                LutealToggleRow(
                    title = stringResource(R.string.settings_notifications_master_title),
                    description = stringResource(R.string.settings_notifications_master_desc),
                    checked = state.isNotificationsEnabled,
                    onCheckedChange = onToggleMaster
                )

                if (state.isNotificationsEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Daily Check-in
                    LutealToggleRow(
                        title = stringResource(R.string.settings_notif_daily_title),
                        description = stringResource(R.string.settings_notif_daily_desc),
                        checked = state.isDailyCheckInEnabled,
                        onCheckedChange = onToggleDaily
                    )

                    if (state.isDailyCheckInEnabled) {
                        LutealSecondaryButton(
                            text = stringResource(R.string.settings_notif_time_label, state.dailyCheckInTime),
                            onClick = { showTimeDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Period Window Reminder
                    LutealToggleRow(
                        title = stringResource(R.string.settings_notif_window_title),
                        description = stringResource(R.string.settings_notif_window_desc),
                        checked = state.isPeriodWindowEnabled,
                        onCheckedChange = onToggleWindow
                    )

                    if (state.isPeriodWindowEnabled) {
                        Text(
                            text = stringResource(R.string.settings_notif_lead_label),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
                            listOf(
                                1 to stringResource(R.string.settings_notif_lead_1d),
                                2 to stringResource(R.string.settings_notif_lead_2d),
                                3 to stringResource(R.string.settings_notif_lead_3d)
                            ).forEach { (days, label) ->
                                FilterChip(
                                    selected = state.periodWindowLeadDays == days,
                                    onClick = { onWindowLeadDaysChange(days) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Late Cycle Prompt
                    LutealToggleRow(
                        title = stringResource(R.string.settings_notif_late_title),
                        description = stringResource(R.string.settings_notif_late_desc),
                        checked = state.isLateCycleEnabled,
                        onCheckedChange = onToggleLate
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Visibility selector
                    Text(
                        text = stringResource(R.string.settings_notif_visibility_title),
                        style = MaterialTheme.typography.titleSmall
                    )

                    LutealRadioRow(
                        title = stringResource(R.string.settings_notif_visibility_concealed),
                        description = stringResource(R.string.settings_notif_visibility_concealed_desc),
                        selected = state.notificationVisibilityMode == NotificationVisibility.CONCEALED,
                        onClick = { onVisibilityChange(NotificationVisibility.CONCEALED) }
                    )

                    LutealRadioRow(
                        title = stringResource(R.string.settings_notif_visibility_descriptive),
                        description = stringResource(R.string.settings_notif_visibility_descriptive_desc),
                        selected = state.notificationVisibilityMode == NotificationVisibility.DESCRIPTIVE,
                        onClick = { onVisibilityChange(NotificationVisibility.DESCRIPTIVE) }
                    )

                    LutealRadioRow(
                        title = stringResource(R.string.settings_notif_visibility_custom),
                        description = stringResource(R.string.settings_notif_visibility_custom_desc),
                        selected = state.notificationVisibilityMode == NotificationVisibility.CUSTOM,
                        onClick = { onVisibilityChange(NotificationVisibility.CUSTOM) }
                    )

                    if (state.notificationVisibilityMode == NotificationVisibility.CUSTOM) {
                        OutlinedTextField(
                            value = state.notificationCustomTitle,
                            onValueChange = onCustomTitleChange,
                            label = { Text(stringResource(R.string.settings_notif_custom_title_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = state.notificationCustomBody,
                            onValueChange = onCustomBodyChange,
                            label = { Text(stringResource(R.string.settings_notif_custom_body_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    if (showTimeDialog) {
        TimeSelectionDialog(
            currentTime = state.dailyCheckInTime,
            onTimeSelected = {
                onDailyTimeChange(it)
                showTimeDialog = false
            },
            onDismiss = { showTimeDialog = false }
        )
    }
}

@Composable
private fun TimeSelectionDialog(
    currentTime: String,
    onTimeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val times = listOf("08:00", "09:00", "12:00", "20:00", "20:30", "21:00", "21:30", "22:00", "22:30")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_notif_daily_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)
            ) {
                times.forEach { t ->
                    LutealRadioRow(
                        title = t,
                        selected = t == currentTime,
                        onClick = { onTimeSelected(t) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun ChangePinDialog(
    onVerifyCurrent: suspend (String) -> Boolean,
    onConfirmNew: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmNewPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var verifying by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_change_pin_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
                OutlinedTextField(
                    value = currentPin,
                    onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) currentPin = it },
                    label = { Text(stringResource(R.string.dialog_change_pin_current_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) newPin = it },
                    label = { Text(stringResource(R.string.dialog_change_pin_new_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = confirmNewPin,
                    onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) confirmNewPin = it },
                    label = { Text(stringResource(R.string.dialog_change_pin_confirm_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
        val currentPinError = stringResource(R.string.dialog_change_pin_error_current)
        val pinLengthError = stringResource(R.string.dialog_set_pin_error_length)
        val pinMatchError = stringResource(R.string.dialog_set_pin_error_match)
        TextButton(
            enabled = !verifying,
            onClick = {
                if (verifying) return@TextButton
                verifying = true
                scope.launch {
                    val currentOk = onVerifyCurrent(currentPin)
                    when {
                        !currentOk ->
                            error = currentPinError
                        newPin.length < 4 ->
                            error = pinLengthError
                        newPin != confirmNewPin ->
                            error = pinMatchError
                        else -> {
                            verifying = false
                            onConfirmNew(newPin)
                            return@launch
                        }
                    }
                    verifying = false
                }
            }
        ) {
            Text(stringResource(R.string.dialog_change_pin_action), fontWeight = FontWeight.Bold)
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
private fun DisableLockDialog(
    onConfirm: suspend (String) -> Boolean,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var verifying by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_disable_lock_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
                Text(
                    text = stringResource(R.string.dialog_disable_lock_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) pin = it },
                    label = { Text(stringResource(R.string.dialog_set_pin_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            val disableLockError = stringResource(R.string.dialog_disable_lock_error)
            TextButton(
                enabled = !verifying,
                onClick = {
                    if (verifying) return@TextButton
                    verifying = true
                    scope.launch {
                        val success = onConfirm(pin)
                        verifying = false
                        if (!success) {
                            error = disableLockError
                        }
                    }
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.dialog_disable_lock_action), fontWeight = FontWeight.Bold)
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
private fun AutoLockTimeoutDialog(
    currentTimeout: AutoLockTimeout,
    onSelect: (AutoLockTimeout) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_timeout_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
                AutoLockTimeout.entries.forEach { timeout ->
                    LutealRadioRow(
                        title = when (timeout) {
                            AutoLockTimeout.IMMEDIATE -> stringResource(R.string.settings_timeout_immediate)
                            AutoLockTimeout.ONE_MINUTE -> stringResource(R.string.settings_timeout_1min)
                            AutoLockTimeout.FIVE_MINUTES -> stringResource(R.string.settings_timeout_5min)
                        },
                        selected = timeout == currentTimeout,
                        onClick = {
                            onSelect(timeout)
                            onDismiss()
                        }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun DataManagementCard(
    exportState: DataExportState,
    wipeState: DataWipeState,
    importState: DataImportState,
    reportExportState: DataExportState,
    onExportRequested: () -> Unit,
    onImportRequested: () -> Unit,
    onClinicalReportRequested: () -> Unit,
    onWipeRequested: () -> Unit,
    onDismissExportState: () -> Unit,
    onDismissWipeState: () -> Unit,
    onDismissImportState: () -> Unit,
    onDismissReportExportState: () -> Unit
) {
    LutealCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
            Text(
                text = stringResource(R.string.settings_report_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.settings_report_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LutealSecondaryButton(
                text = when (reportExportState) {
                    is DataExportState.Loading -> stringResource(R.string.settings_export_in_progress)
                    else -> stringResource(R.string.settings_report_action)
                },
                onClick = onClinicalReportRequested,
                modifier = Modifier.fillMaxWidth(),
                enabled = reportExportState !is DataExportState.Loading
            )
            when (reportExportState) {
                is DataExportState.Success -> Text(
                    text = stringResource(R.string.settings_report_success),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                is DataExportState.Error -> Column(
                    verticalArrangement = Arrangement.spacedBy(LutealSpacing.xxs)
                ) {
                    Text(
                        text = stringResource(R.string.settings_report_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = onDismissReportExportState) {
                        Text(text = stringResource(R.string.action_close))
                    }
                }
                else -> Unit
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

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
                text = stringResource(R.string.settings_import_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.settings_import_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LutealSecondaryButton(
                text = when (importState) {
                    is DataImportState.Inspecting -> stringResource(R.string.settings_import_inspecting)
                    is DataImportState.Restoring -> stringResource(R.string.settings_import_in_progress)
                    else -> stringResource(R.string.settings_import_action)
                },
                onClick = onImportRequested,
                modifier = Modifier.fillMaxWidth(),
                enabled = importState !is DataImportState.Inspecting && importState !is DataImportState.Restoring
            )
            when (importState) {
                is DataImportState.Success -> Text(
                    text = stringResource(
                        R.string.settings_import_success,
                        importState.summary.cyclesImported,
                        importState.summary.dailyEntriesImported
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                is DataImportState.Error -> Column(
                    verticalArrangement = Arrangement.spacedBy(LutealSpacing.xxs)
                ) {
                    val errorMsg = importState.messageResId?.let { stringResource(it) } ?: importState.message.orEmpty()
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = onDismissImportState) {
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
private fun ImportBackupDialog(
    preview: LutealBackupPreview,
    onConfirm: (ImportStrategy) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedStrategy by remember { mutableStateOf(ImportStrategy.MERGE_UPSERT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.settings_import_dialog_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                val cycleText = if (preview.earliestCycleDate != null && preview.latestCycleDate != null) {
                    stringResource(
                        R.string.settings_import_dialog_cycles,
                        preview.cycleCount,
                        preview.earliestCycleDate,
                        preview.latestCycleDate
                    )
                } else {
                    stringResource(R.string.settings_import_dialog_cycles_no_dates, preview.cycleCount)
                }
                Text(text = cycleText, style = MaterialTheme.typography.bodyMedium)

                Text(
                    text = stringResource(R.string.settings_import_dialog_entries, preview.dailyEntryCount),
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = stringResource(R.string.settings_import_dialog_symptoms, preview.symptomLogCount),
                    style = MaterialTheme.typography.bodyMedium
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    text = stringResource(R.string.settings_import_dialog_strategy_title),
                    style = MaterialTheme.typography.titleSmall
                )

                LutealRadioRow(
                    selected = selectedStrategy == ImportStrategy.MERGE_UPSERT,
                    onClick = { selectedStrategy = ImportStrategy.MERGE_UPSERT },
                    title = stringResource(R.string.settings_import_strategy_merge),
                    description = stringResource(R.string.settings_import_strategy_merge_desc)
                )

                LutealRadioRow(
                    selected = selectedStrategy == ImportStrategy.REPLACE_ALL,
                    onClick = { selectedStrategy = ImportStrategy.REPLACE_ALL },
                    title = stringResource(R.string.settings_import_strategy_replace),
                    description = stringResource(R.string.settings_import_strategy_replace_desc)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedStrategy) }) {
                Text(
                    text = stringResource(R.string.settings_import_dialog_confirm),
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
    onDismissRecoveryMessage: () -> Unit,
    onUnlinkSync: () -> Unit
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

                if (state.isLinked || state.hasAccount) {
                    LutealSecondaryButton(
                        text = stringResource(R.string.settings_sync_unlink),
                        onClick = onUnlinkSync,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

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
    val accountCode = remember(state.hasAccount, state.lastSyncedEpochMillis) {
        getAccountCode()
    }
    val context = LocalContext.current
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
            val accountCodeTitle = stringResource(R.string.settings_sync_account_code_title)
            LutealSecondaryButton(
                text = if (copied) stringResource(R.string.settings_sync_account_code_copied)
                else stringResource(R.string.settings_sync_account_code_copy),
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    if (clipboard != null) {
                        val clip = android.content.ClipData.newPlainText(
                            accountCodeTitle,
                            accountCode
                        ).apply {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                description.extras = android.os.PersistableBundle().apply {
                                    putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
                                }
                            }
                        }
                        clipboard.setPrimaryClip(clip)
                    }
                    copied = true
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

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

