package fr.luteal.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.luteal.app.sync.SyncScheduler
import fr.luteal.core.data.datastore.SyncDataStore
import fr.luteal.core.data.repository.UserRepository
import fr.luteal.core.data.seed.TestDataSeeder
import fr.luteal.core.model.AgeBand
import fr.luteal.core.model.SyncMode
import fr.luteal.core.model.TrackingContext
import fr.luteal.core.network.auth.SyncCredentialStore
import fr.luteal.core.network.auth.SyncCredentials
import fr.luteal.core.network.sync.FolicularApiClientFactory
import fr.luteal.core.network.sync.SyncCursorStore
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import android.content.Context
import android.net.Uri
import fr.luteal.core.data.DataExportManager
import fr.luteal.core.data.LocalDataPurgeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import androidx.annotation.StringRes
import fr.luteal.app.R
import fr.luteal.core.data.datastore.UserPreferencesDataStore
import fr.luteal.core.data.security.AppLockManager
import fr.luteal.core.data.security.PinCryptoManager
import fr.luteal.core.model.AutoLockTimeout
import fr.luteal.core.data.DataImportManager
import fr.luteal.core.model.DataImportError
import fr.luteal.core.data.ClinicalReportAggregator
import fr.luteal.core.data.report.HtmlReportBuilder
import fr.luteal.core.data.report.PdfReportBuilder
import fr.luteal.core.model.ClinicalReportConfig
import fr.luteal.core.model.ReportFormat
import fr.luteal.app.notification.NotificationScheduler
import fr.luteal.core.model.NotificationVisibility
import fr.luteal.core.model.ImportStrategy
import fr.luteal.core.model.ImportSummary
import fr.luteal.core.model.LutealBackupPayload
import fr.luteal.core.model.LutealBackupPreview

/**
 * Settings tab view model.
 *
 * Connects the UI to [UserRepository] for local configuration, [SyncDataStore]
 * and [SyncScheduler] for online sync, [DataExportManager] for JSON backups,
 * and [LocalDataPurgeManager] for local data wiping.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val syncDataStore: SyncDataStore,
    private val syncScheduler: SyncScheduler,
    private val testDataSeeder: TestDataSeeder,
    private val credentialStore: SyncCredentialStore,
    private val apiClientFactory: FolicularApiClientFactory,
    private val cursorStore: SyncCursorStore,
    private val dataExportManager: DataExportManager,
    private val dataImportManager: DataImportManager,
    private val localDataPurgeManager: LocalDataPurgeManager,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val pinCryptoManager: PinCryptoManager,
    private val appLockManager: AppLockManager,
    private val clinicalReportAggregator: ClinicalReportAggregator,
    private val notificationScheduler: NotificationScheduler
) : ViewModel() {

    /** Local edit buffer for the base URL field, kept out of the DataStore. */
    private val baseUrlDraft = MutableStateFlow("")

    private val testDataActionState = MutableStateFlow<TestDataActionState>(TestDataActionState.Idle)
    /** Account-code recovery buffers. Declared before [uiState] so the
     *  initializer below can observe them. */
    private val recoveryCodeDraft = MutableStateFlow("")
    private val recoveryState = MutableStateFlow<RecoveryState>(RecoveryState.Idle)

    private val exportState = MutableStateFlow<DataExportState>(DataExportState.Idle)
    private val wipeState = MutableStateFlow<DataWipeState>(DataWipeState.Idle)
    private val importState = MutableStateFlow<DataImportState>(DataImportState.Idle)

    private val reportExportState = MutableStateFlow<DataExportState>(DataExportState.Idle)

    private val fileOps: Flow<FileOps> =
        combine(exportState, wipeState, importState, reportExportState) { export, wipe, importOp, report ->
            FileOps(export, wipe, importOp, report)
        }

    private val drafts: Flow<Drafts> =
        combine(baseUrlDraft, recoveryCodeDraft, fileOps) { base, recovery, files ->
            Drafts(base, recovery, files.export, files.wipe, files.importOp, files.report)
        }
    val uiState: StateFlow<SettingsSyncUiState> = combine(
        userRepository.getUserPreferences(),
        syncDataStore.syncPreferencesFlow,
        drafts,
        testDataActionState,
        recoveryState
    ) { preferences, syncPreferences, draft, actionState, recovery ->
        SettingsSyncUiState(
            onlineSyncEnabled = preferences.syncMode == SyncMode.ONLINE_CLOUD.name,
            baseUrlDraft = draft.baseUrl,
            storedBaseUrl = syncPreferences.baseUrl.orEmpty(),
            inProgress = syncPreferences.inProgress,
            lastSyncedEpochMillis = syncPreferences.lastSyncedEpochMillis,
            lastError = syncPreferences.lastError,
            testDataState = actionState,
            recoveryCodeDraft = draft.recoveryCode,
            recoveryState = recovery,
            exportState = draft.exportState,
            wipeState = draft.wipeState,
            importState = draft.importState,
            reportExportState = draft.reportExportState,
            hasAccount = credentialStore.load() != null,
            declaredContexts = preferences.declaredContexts,
            ageBand = AgeBand.fromId(preferences.ageBand),
            isAppLockEnabled = preferences.isAppLockEnabled,
            isBiometricEnabled = preferences.isBiometricEnabled,
            isBiometricHardwareAvailable = appLockManager.isBiometricHardwareAvailable(),
            autoLockTimeout = AutoLockTimeout.fromName(preferences.autoLockTimeout),
            isScreenMaskingEnabled = preferences.isScreenMaskingEnabled,
            hasPinConfigured = pinCryptoManager.hasPinConfigured(),
            isNotificationsEnabled = preferences.isNotificationsEnabled,
            isDailyCheckInEnabled = preferences.isDailyCheckInEnabled,
            dailyCheckInTime = preferences.dailyCheckInTime,
            isPeriodWindowEnabled = preferences.isPeriodWindowEnabled,
            periodWindowLeadDays = preferences.periodWindowLeadDays,
            isLateCycleEnabled = preferences.isLateCycleEnabled,
            lateCycleGraceDays = preferences.lateCycleGraceDays,
            notificationVisibilityMode = NotificationVisibility.fromName(preferences.notificationVisibilityMode),
            notificationCustomTitle = preferences.notificationCustomTitle,
            notificationCustomBody = preferences.notificationCustomBody,
            temperatureUnit = preferences.temperatureUnit
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsSyncUiState()
    )

    fun setOnlineSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userRepository.updateSyncMode(
                if (enabled) SyncMode.ONLINE_CLOUD else SyncMode.OFFLINE_LOCAL
            )
        }
    }

    fun setTrackingContext(context: TrackingContext, enabled: Boolean) {
        viewModelScope.launch {
            userRepository.setTrackingContext(context.id, enabled)
        }
    }

    fun setAgeBand(band: AgeBand?) {
        viewModelScope.launch {
            userRepository.setAgeBand(band?.id)
        }
    }

    fun setTemperatureUnit(unit: String) {
        viewModelScope.launch {
            userPreferencesDataStore.setTemperatureUnit(unit)
        }
    }

    fun onBaseUrlChange(value: String) {
        baseUrlDraft.update { value }
    }

    /** Persists the custom sync server base URL. */
    fun saveSyncSettings() {
        val base = baseUrlDraft.value.trim()
        viewModelScope.launch {
            syncDataStore.setBaseUrl(base.ifBlank { null })
        }
    }

    fun syncNow() {
        syncScheduler.syncNow()
    }

    fun seedMockData() {
        viewModelScope.launch {
            testDataActionState.value = TestDataActionState.Loading
            runCatching {
                testDataSeeder.seedMockData()
            }.onSuccess {
                testDataActionState.value = TestDataActionState.SuccessSeeded
            }.onFailure {
                testDataActionState.value = TestDataActionState.Error(
                    message = it.message,
                    messageResId = if (it.message.isNullOrBlank()) R.string.settings_test_data_error_seed else null
                )
            }
        }
    }

    fun clearTestData() {
        viewModelScope.launch {
            testDataActionState.value = TestDataActionState.Loading
            runCatching {
                testDataSeeder.clearAllData()
            }.onSuccess {
                testDataActionState.value = TestDataActionState.SuccessCleared
            }.onFailure {
                testDataActionState.value = TestDataActionState.Error(
                    message = it.message,
                    messageResId = if (it.message.isNullOrBlank()) R.string.settings_test_data_error_clear else null
                )
            }
        }
    }

    fun clearTestDataMessage() {
        testDataActionState.value = TestDataActionState.Idle
    }

    fun getAccountCode(): String? = credentialStore.load()?.accountCode

    // --- Account recovery ---------------------------------------------------

    fun onRecoveryCodeChange(value: String) {
        recoveryCodeDraft.value = value
    }

    /**
     * Attaches this device to an existing account using its account code.
     *
     * This is the only way to read data written by another device: the account
     * code is the root of the key hierarchy, and the server holds no key that
     * could substitute for it. On success the credentials are stored and the
     * next sync pulls and decrypts the account's history.
     */
    fun recoverAccount() {
        val code = recoveryCodeDraft.value.trim()
        if (code.isBlank()) return
        if (credentialStore.load() != null) {
            recoveryState.value = RecoveryState.Error(messageResId = R.string.settings_recovery_error_already_linked)
            return
        }
        viewModelScope.launch {
            recoveryState.value = RecoveryState.Loading
            runCatching {
                val client = apiClientFactory.create(cursorStore.getBaseUrl())
                val result = client.addDevice(code, cursorStore.getDeviceLabel())
                credentialStore.save(
                    SyncCredentials(
                        accountId = result.accountId,
                        accountCode = code,
                        deviceToken = result.deviceToken
                    )
                )
                // A restored device starts from cursor zero so it pulls the
                // whole history rather than resuming someone else's position.
                syncDataStore.setCursor(0L)
            }.onSuccess {
                recoveryState.value = RecoveryState.Success
                recoveryCodeDraft.value = ""
                syncScheduler.syncNow()
            }.onFailure { err ->
                recoveryState.value = RecoveryState.Error(
                    message = err.message,
                    messageResId = if (err.message.isNullOrBlank()) R.string.settings_recovery_error_failed else null
                )
            }
        }
    }

    fun clearRecoveryState() {
        recoveryState.value = RecoveryState.Idle
    }

    fun exportData(context: Context, uri: Uri) {
        viewModelScope.launch {
            exportState.value = DataExportState.Loading
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        dataExportManager.exportToStream(stream)
                    } ?: throw IllegalStateException("Cannot open output stream for URI")
                }
            }.onSuccess {
                exportState.value = DataExportState.Success
            }.onFailure { err ->
                exportState.value = DataExportState.Error(err.message ?: "Export failed")
            }
        }
    }

    fun purgeAllLocalData(onPurged: () -> Unit = {}) {
        viewModelScope.launch {
            wipeState.value = DataWipeState.Loading
            runCatching {
                localDataPurgeManager.purgeAllLocalData()
            }.onSuccess {
                wipeState.value = DataWipeState.Success
                notificationScheduler.reconcileAllSchedules()
                onPurged()
            }.onFailure { err ->
                wipeState.value = DataWipeState.Error(err.message ?: "Purge failed")
            }
        }
    }

    fun clearExportState() {
        exportState.value = DataExportState.Idle
    }

    fun clearWipeState() {
        wipeState.value = DataWipeState.Idle
    }

    fun inspectBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            importState.value = DataImportState.Inspecting
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        dataImportManager.inspectBackup(stream)
                    } ?: throw IllegalStateException("Cannot open input stream for URI")
                }
            }.onSuccess { result ->
                result.fold(
                    onSuccess = { (preview, payload) ->
                        importState.value = DataImportState.PreviewReady(preview, payload)
                    },
                    onFailure = { err ->
                        val resId = when (err) {
                            is DataImportError.InvalidJsonSyntax -> R.string.settings_import_error_syntax
                            is DataImportError.UnsupportedSchemaVersion -> R.string.settings_import_error_schema
                            else -> R.string.settings_import_error_generic
                        }
                        importState.value = DataImportState.Error(message = err.message, messageResId = resId)
                    }
                )
            }.onFailure { err ->
                importState.value = DataImportState.Error(
                    message = err.message,
                    messageResId = R.string.settings_import_error_generic
                )
            }
        }
    }

    fun confirmRestore(payload: LutealBackupPayload, strategy: ImportStrategy, onRestored: () -> Unit = {}) {
        viewModelScope.launch {
            importState.value = DataImportState.Restoring
            val result = dataImportManager.restoreBackup(payload, strategy)
            result.fold(
                onSuccess = { summary ->
                    importState.value = DataImportState.Success(summary)
                    notificationScheduler.reconcileAllSchedules()
                    onRestored()
                },
                onFailure = { err ->
                    importState.value = DataImportState.Error(
                        message = err.message,
                        messageResId = R.string.settings_import_error_generic
                    )
                }
            )
        }
    }

    fun dismissImportPreview() {
        importState.value = DataImportState.Idle
    }

    fun clearImportState() {
        importState.value = DataImportState.Idle
    }

    fun setAppLockEnabledWithPin(pin: String) {
        viewModelScope.launch {
            pinCryptoManager.setPin(pin)
            userPreferencesDataStore.setAppLockEnabled(true)
        }
    }

    fun disableAppLock(currentPin: String): Boolean {
        if (!pinCryptoManager.verifyPin(currentPin)) return false
        viewModelScope.launch {
            userPreferencesDataStore.setAppLockEnabled(false)
            userPreferencesDataStore.setBiometricEnabled(false)
            pinCryptoManager.clearPin()
        }
        return true
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesDataStore.setBiometricEnabled(enabled)
        }
    }

    fun setAutoLockTimeout(timeout: AutoLockTimeout) {
        viewModelScope.launch {
            userPreferencesDataStore.setAutoLockTimeout(timeout.name)
        }
    }

    fun setScreenMaskingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesDataStore.setScreenMaskingEnabled(enabled)
        }
    }

    fun changePin(oldPin: String, newPin: String): Boolean {
        if (!pinCryptoManager.verifyPin(oldPin)) return false
        pinCryptoManager.setPin(newPin)
        return true
    }


    fun exportClinicalReport(context: Context, uri: Uri, config: ClinicalReportConfig) {
        viewModelScope.launch {
            reportExportState.value = DataExportState.Loading
            withContext(Dispatchers.IO) {
                runCatching {
                    val data = clinicalReportAggregator.aggregate(config)
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        if (config.format == ReportFormat.PDF) {
                            PdfReportBuilder.writePdfToStream(data, stream)
                        } else {
                            HtmlReportBuilder.writeHtmlToStream(data, stream)
                        }
                    } ?: throw IllegalStateException("Cannot open output stream for URI")
                }
            }.onSuccess {
                reportExportState.value = DataExportState.Success
            }.onFailure { err ->
                reportExportState.value = DataExportState.Error(err.message ?: "Export failed")
            }
        }
    }

    fun clearReportExportState() {
        reportExportState.value = DataExportState.Idle
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesDataStore.setNotificationsEnabled(enabled)
            notificationScheduler.reconcileAllSchedules()
        }
    }

    fun setDailyCheckInEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesDataStore.setDailyCheckInEnabled(enabled)
            notificationScheduler.reconcileAllSchedules()
        }
    }

    fun setDailyCheckInTime(time: String) {
        viewModelScope.launch {
            userPreferencesDataStore.setDailyCheckInTime(time)
            notificationScheduler.reconcileAllSchedules()
        }
    }

    fun setPeriodWindowNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesDataStore.setPeriodWindowNotificationEnabled(enabled)
            notificationScheduler.reconcileAllSchedules()
        }
    }

    fun setPeriodWindowLeadDays(days: Int) {
        viewModelScope.launch {
            userPreferencesDataStore.setPeriodWindowLeadDays(days)
            notificationScheduler.reconcileAllSchedules()
        }
    }

    fun setLateCycleNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesDataStore.setLateCycleNotificationEnabled(enabled)
            notificationScheduler.reconcileAllSchedules()
        }
    }

    fun setLateCycleGraceDays(days: Int) {
        viewModelScope.launch {
            userPreferencesDataStore.setLateCycleGraceDays(days)
            notificationScheduler.reconcileAllSchedules()
        }
    }

    fun setNotificationVisibilityMode(mode: NotificationVisibility) {
        viewModelScope.launch {
            userPreferencesDataStore.setNotificationVisibilityMode(mode.name)
            notificationScheduler.reconcileAllSchedules()
        }
    }

    fun setNotificationCustomTitle(title: String) {
        viewModelScope.launch {
            userPreferencesDataStore.setNotificationCustomTitle(title)
            notificationScheduler.reconcileAllSchedules()
        }
    }

    fun setNotificationCustomBody(body: String) {
        viewModelScope.launch {
            userPreferencesDataStore.setNotificationCustomBody(body)
            notificationScheduler.reconcileAllSchedules()
        }
    }
    fun verifyPin(pin: String): Boolean = pinCryptoManager.verifyPin(pin)
}

sealed interface RecoveryState {
    data object Idle : RecoveryState
    data object Loading : RecoveryState
    data object Success : RecoveryState
    data class Error(val message: String? = null, @param:StringRes val messageResId: Int? = null) : RecoveryState
}

sealed interface DataExportState {
    data object Idle : DataExportState
    data object Loading : DataExportState
    data object Success : DataExportState
    data class Error(val message: String) : DataExportState
}

sealed interface DataWipeState {
    data object Idle : DataWipeState
    data object Loading : DataWipeState
    data object Success : DataWipeState
    data class Error(val message: String) : DataWipeState
}

sealed interface DataImportState {
    data object Idle : DataImportState
    data object Inspecting : DataImportState
    data class PreviewReady(val preview: LutealBackupPreview, val payload: LutealBackupPayload) : DataImportState
    data object Restoring : DataImportState
    data class Success(val summary: ImportSummary) : DataImportState
    data class Error(val message: String? = null, @param:StringRes val messageResId: Int? = null) : DataImportState
}

data class SettingsSyncUiState(
    val onlineSyncEnabled: Boolean = false,
    val baseUrlDraft: String = "",
    val storedBaseUrl: String = "",
    val inProgress: Boolean = false,
    val lastSyncedEpochMillis: Long? = null,
    val lastError: String? = null,
    val testDataState: TestDataActionState = TestDataActionState.Idle,
    val recoveryCodeDraft: String = "",
    val recoveryState: RecoveryState = RecoveryState.Idle,
    val exportState: DataExportState = DataExportState.Idle,
    val wipeState: DataWipeState = DataWipeState.Idle,
    val importState: DataImportState = DataImportState.Idle,
    val reportExportState: DataExportState = DataExportState.Idle,
    val hasAccount: Boolean = false,
    /** Contexts the user has declared, editable after onboarding. */
    val declaredContexts: Set<TrackingContext> = emptySet(),
    /** Null means no band declared, which is a valid state. */
    val ageBand: AgeBand? = null,
    val isAppLockEnabled: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    val isBiometricHardwareAvailable: Boolean = false,
    val autoLockTimeout: AutoLockTimeout = AutoLockTimeout.IMMEDIATE,
    val isScreenMaskingEnabled: Boolean = false,
    val hasPinConfigured: Boolean = false,
    val isNotificationsEnabled: Boolean = false,
    val isDailyCheckInEnabled: Boolean = false,
    val dailyCheckInTime: String = "21:00",
    val isPeriodWindowEnabled: Boolean = false,
    val periodWindowLeadDays: Int = 2,
    val isLateCycleEnabled: Boolean = false,
    val lateCycleGraceDays: Int = 1,
    val notificationVisibilityMode: NotificationVisibility = NotificationVisibility.CONCEALED,
    val notificationCustomTitle: String = "",
    val notificationCustomBody: String = "",
    val temperatureUnit: String = "CELSIUS"
)

/** Text drafts held together so [combine] stays within its five-flow limit. */
private data class FileOps(
    val export: DataExportState,
    val wipe: DataWipeState,
    val importOp: DataImportState,
    val report: DataExportState
)

private data class Drafts(
    val baseUrl: String,
    val recoveryCode: String,
    val exportState: DataExportState,
    val wipeState: DataWipeState,
    val importState: DataImportState,
    val reportExportState: DataExportState
)

sealed interface TestDataActionState {
    data object Idle : TestDataActionState
    data object Loading : TestDataActionState
    data object SuccessSeeded : TestDataActionState
    data object SuccessCleared : TestDataActionState
    data class Error(val message: String? = null, @param:StringRes val messageResId: Int? = null) : TestDataActionState
}
