package fr.luteal.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.luteal.app.BuildConfig
import fr.luteal.app.R
import fr.luteal.core.common.FrenchDateFormatter
import fr.luteal.core.designsystem.component.LutealCard
import fr.luteal.core.designsystem.component.LutealPrimaryButton
import fr.luteal.core.designsystem.component.LutealSecondaryButton
import fr.luteal.core.designsystem.component.LutealToggleRow
import fr.luteal.core.designsystem.theme.LutealSpacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val syncState by viewModel.uiState.collectAsState()

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
            if (!BuildConfig.DEBUG) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsInformationRow(
                    icon = Icons.Rounded.CloudOff,
                    title = stringResource(R.string.settings_sync_title),
                    body = stringResource(R.string.settings_sync_body)
                )
            }
        }

        // Local sync trial controls, only in the debug/dev build that declares
        // the network permission. The release build never shows this.
        if (BuildConfig.DEBUG) {
            SyncTrialCard(
                state = syncState,
                onToggleOnline = viewModel::setOnlineSyncEnabled,
                onBaseUrlChange = viewModel::onBaseUrlChange,
                onSaveBaseUrl = viewModel::saveBaseUrl,
                onSyncNow = viewModel::syncNow,
                getAccountCode = viewModel::getAccountCode
            )
            TestDataCard(
                state = syncState.testDataState,
                onSeed = viewModel::seedMockData,
                onClear = viewModel::clearTestData,
                onDismissMessage = viewModel::clearTestDataMessage
            )
        }

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
                    is TestDataActionState.Error -> Text(
                        text = stringResource(R.string.settings_test_data_error, state.message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
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
private fun SyncTrialCard(
    state: SettingsSyncUiState,
    onToggleOnline: (Boolean) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onSaveBaseUrl: () -> Unit,
    onSyncNow: () -> Unit,
    getAccountCode: () -> String?
) {
    Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
        SettingsSectionHeader(title = stringResource(R.string.settings_sync_trial_title))

        LutealCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
                OnlineSyncToggle(
                    enabled = state.onlineSyncEnabled,
                    onToggle = onToggleOnline
                )

                if (state.onlineSyncEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Text(
                        text = stringResource(R.string.settings_sync_trial_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = state.baseUrlDraft,
                        onValueChange = onBaseUrlChange,
                        label = { Text(stringResource(R.string.settings_sync_base_url_label)) },
                        placeholder = { Text(stringResource(R.string.settings_sync_base_url_default)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "${stringResource(R.string.settings_sync_base_url_label)} : ${state.storedBaseUrl.ifBlank { stringResource(R.string.settings_sync_base_url_default) }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(LutealSpacing.sm)
                    ) {
                        LutealSecondaryButton(
                            text = stringResource(R.string.settings_sync_base_url_save),
                            onClick = onSaveBaseUrl,
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

                AccountCodeSection(getAccountCode = getAccountCode)
            }
        }
    }
}

@Composable
private fun AccountCodeSection(getAccountCode: () -> String?) {
    val accountCode = remember { getAccountCode() }
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
    val text = when {
        state.inProgress -> stringResource(R.string.settings_sync_in_progress)
        state.lastError != null -> stringResource(R.string.settings_sync_last_error, state.lastError)
        state.lastSyncedEpochMillis != null -> stringResource(
            R.string.settings_sync_last_success,
            formatSyncInstant(state.lastSyncedEpochMillis)
        )
        else -> stringResource(R.string.settings_sync_idle)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun formatSyncInstant(epochMillis: Long): String {
    val date: LocalDate = Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    return FrenchDateFormatter.formatFullDate(date)
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
