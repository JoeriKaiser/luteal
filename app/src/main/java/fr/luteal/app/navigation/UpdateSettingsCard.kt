package fr.luteal.app.navigation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.luteal.app.R
import fr.luteal.app.update.InstallFailure
import fr.luteal.app.update.UpdateCheckState
import fr.luteal.app.update.UpdateInfo
import fr.luteal.app.update.UpdateInstallState
import fr.luteal.app.update.UpdateUiState
import fr.luteal.app.update.UpdateViewModel
import fr.luteal.core.designsystem.component.LutealCard
import fr.luteal.core.designsystem.component.LutealPrimaryButton
import fr.luteal.core.designsystem.component.LutealSecondaryButton
import fr.luteal.core.designsystem.component.LutealToggleRow
import fr.luteal.core.designsystem.theme.LutealSpacing

@Composable
fun UpdateSettingsCard(
    viewModel: UpdateViewModel = hiltViewModel()
) {
    if (!viewModel.isAvailable) return

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showConsent by rememberSaveable { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onReturnedFromInstallPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Collected only while started, so the installer prompt waits until Luteal is visible.
    val confirmation = (state.install as? UpdateInstallState.NeedsConfirmation)?.intent
    LaunchedEffect(confirmation) {
        if (confirmation != null) viewModel.onConfirmationLaunched(context.startActivitySafely(confirmation))
    }

    if (showConsent) {
        UpdateConsentDialog(
            onConfirm = {
                showConsent = false
                viewModel.enableChecks()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onDismiss = { showConsent = false }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
        SettingsSectionHeader(title = stringResource(R.string.settings_updates_title))

        LutealCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
                LutealToggleRow(
                    title = stringResource(R.string.settings_updates_toggle_title),
                    description = stringResource(R.string.settings_updates_toggle_body),
                    checked = state.checksEnabled,
                    onCheckedChange = { enable ->
                        if (enable) showConsent = true else viewModel.disableChecks()
                    }
                )
                if (state.checksEnabled) {
                    UpdateCardBody(
                        state = state,
                        onCheckNow = viewModel::checkNow,
                        onInstall = viewModel::downloadAndInstall,
                        onOpenInstallPermission = {
                            context.startActivitySafely(viewModel.installPermissionIntent())
                        },
                        onOpenRelease = { url ->
                            context.startActivitySafely(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateCardBody(
    state: UpdateUiState,
    onCheckNow: () -> Unit,
    onInstall: () -> Unit,
    onOpenInstallPermission: () -> Unit,
    onOpenRelease: (String) -> Unit
) {
    val check = state.check
    val install = state.install
    val busy = install is UpdateInstallState.Downloading ||
        install is UpdateInstallState.Verifying ||
        install is UpdateInstallState.Installing ||
        install is UpdateInstallState.NeedsConfirmation

    LutealSecondaryButton(
        text = stringResource(R.string.settings_updates_check_now),
        onClick = onCheckNow,
        modifier = Modifier.fillMaxWidth(),
        enabled = check !is UpdateCheckState.Checking && !busy
    )

    when (check) {
        UpdateCheckState.Idle -> Unit
        UpdateCheckState.Checking -> StatusText(stringResource(R.string.settings_updates_checking))
        is UpdateCheckState.UpToDate -> StatusText(stringResource(R.string.settings_updates_up_to_date, check.installed))
        UpdateCheckState.Failed -> StatusText(stringResource(R.string.settings_updates_error), isError = true)
        is UpdateCheckState.Available -> AvailableUpdate(
            info = check.info,
            install = install,
            busy = busy,
            onInstall = onInstall,
            onOpenInstallPermission = onOpenInstallPermission,
            onOpenRelease = onOpenRelease
        )
    }
}

@Composable
private fun AvailableUpdate(
    info: UpdateInfo,
    install: UpdateInstallState,
    busy: Boolean,
    onInstall: () -> Unit,
    onOpenInstallPermission: () -> Unit,
    onOpenRelease: (String) -> Unit
) {
    Text(
        text = stringResource(R.string.settings_updates_available, info.version.toString()),
        style = MaterialTheme.typography.titleMedium
    )
    info.changelog?.let { notes ->
        Text(
            text = stringResource(R.string.settings_updates_changelog_title),
            style = MaterialTheme.typography.labelLarge
        )
        Text(
            text = notes,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (info.canInstall) {
        LutealPrimaryButton(
            text = stringResource(R.string.settings_updates_install),
            onClick = onInstall,
            modifier = Modifier.fillMaxWidth(),
            loading = busy,
            enabled = !busy
        )
    } else {
        StatusText(stringResource(R.string.settings_updates_no_digest))
    }

    when (install) {
        UpdateInstallState.Idle -> Unit
        UpdateInstallState.NeedsInstallPermission -> {
            StatusText(stringResource(R.string.settings_updates_permission_needed))
            LutealSecondaryButton(
                text = stringResource(R.string.settings_updates_permission_button),
                onClick = onOpenInstallPermission,
                modifier = Modifier.fillMaxWidth()
            )
        }
        UpdateInstallState.Downloading -> StatusText(stringResource(R.string.settings_updates_downloading))
        UpdateInstallState.Verifying -> StatusText(stringResource(R.string.settings_updates_verifying))
        UpdateInstallState.Installing,
        is UpdateInstallState.NeedsConfirmation -> StatusText(stringResource(R.string.settings_updates_installing))
        is UpdateInstallState.Failed -> {
            StatusText(stringResource(install.reason.messageRes()), isError = true)
            install.detail?.takeIf { install.reason == InstallFailure.INSTALLER }?.let { StatusText(it, isError = true) }
        }
    }

    LutealSecondaryButton(
        text = stringResource(R.string.settings_updates_open_github),
        onClick = { onOpenRelease(info.releaseUrl) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun UpdateConsentDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    // Two equal text buttons, so neither choice is pushed.
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_updates_consent_title)) },
        text = { Text(stringResource(R.string.settings_updates_consent_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.settings_updates_consent_confirm))
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
private fun StatusText(text: String, isError: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun InstallFailure.messageRes(): Int = when (this) {
    InstallFailure.DOWNLOAD -> R.string.settings_updates_failed_download
    InstallFailure.MISSING_DIGEST -> R.string.settings_updates_no_digest
    InstallFailure.HASH_MISMATCH -> R.string.settings_updates_failed_hash
    InstallFailure.UNREADABLE -> R.string.settings_updates_failed_unreadable
    InstallFailure.WRONG_PACKAGE -> R.string.settings_updates_failed_package
    InstallFailure.NOT_NEWER -> R.string.settings_updates_failed_not_newer
    InstallFailure.SIGNER_MISMATCH -> R.string.settings_updates_failed_signature
    InstallFailure.INSTALLER -> R.string.settings_updates_failed_install
    InstallFailure.ABORTED -> R.string.settings_updates_failed_aborted
}

private fun Context.startActivitySafely(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}
