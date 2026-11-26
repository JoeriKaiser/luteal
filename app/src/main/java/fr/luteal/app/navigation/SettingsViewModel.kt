package fr.luteal.app.navigation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.luteal.app.sync.AutoSyncCoordinator
import fr.luteal.app.sync.SyncScheduler
import fr.luteal.core.data.datastore.SyncDataStore
import fr.luteal.core.data.repository.DuoProjectionSynchronizer
import fr.luteal.core.data.repository.UserRepository
import fr.luteal.core.data.seed.TestDataSeeder
import fr.luteal.core.model.AgeBand
import fr.luteal.core.model.SyncMode
import fr.luteal.core.model.TrackingContext
import fr.luteal.core.network.LegacyServerUnsupportedException
import fr.luteal.core.network.auth.AuthVersion
import fr.luteal.core.network.auth.SyncCredentialStore
import fr.luteal.core.network.auth.SyncCredentials
import fr.luteal.core.network.crypto.AccountCodeV2
import fr.luteal.core.network.crypto.RecordCrypto
import fr.luteal.core.network.crypto.RecordSealer
import fr.luteal.core.network.sync.AccountUpgradeManager
import fr.luteal.core.network.sync.AccountUpgradeOutcome
import fr.luteal.core.network.sync.FolicularApiClientFactory
import fr.luteal.core.network.sync.SyncCursorStore
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

import android.content.Context
import android.net.Uri
import fr.luteal.core.data.DataExportManager
import fr.luteal.core.data.LocalDataPurgeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import androidx.annotation.StringRes
import fr.luteal.app.BuildConfig
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
import java.io.ByteArrayInputStream
import java.time.LocalDate

/**
 * Settings tab view model.
 *
 * Connects the UI to [UserRepository] for local configuration, [SyncDataStore]
 * and [SyncScheduler] for online sync, [DataExportManager] for JSON backups,
 * and [LocalDataPurgeManager] for local data wiping.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val userRepository: UserRepository,
    private val syncDataStore: SyncDataStore,
    private val syncScheduler: SyncScheduler,
    private val autoSyncCoordinator: AutoSyncCoordinator,
    private val testDataSeeder: TestDataSeeder,
    private val credentialStore: SyncCredentialStore,
    private val apiClientFactory: FolicularApiClientFactory,
    private val cursorStore: SyncCursorStore,
    private val recordSealer: RecordSealer,
    private val accountUpgradeManager: AccountUpgradeManager,
    private val dataExportManager: DataExportManager,
    private val dataImportManager: DataImportManager,
    private val localDataPurgeManager: LocalDataPurgeManager,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val pinCryptoManager: PinCryptoManager,
    private val appLockManager: AppLockManager,
    private val clinicalReportAggregator: ClinicalReportAggregator,
    private val notificationScheduler: NotificationScheduler,
    private val duoProjectionSynchronizer: DuoProjectionSynchronizer
) : ViewModel() {
    private companion object {
        const val KEY_REG_DRAFT_CODE = "v2_reg_draft_code"
        const val KEY_UPGRADE_DRAFT_CODE = "v2_upgrade_draft_code"
        const val KEY_UPGRADE_OTHER_DEVICES = "v2_upgrade_other_devices"
    }

    private val syncCursorStore: SyncCursorStore get() = cursorStore

    private val _syncUiState = MutableStateFlow(
        credentialStore.load().let { creds ->
            SettingsSyncUiState(
                hasAccount = creds != null,
                isLinked = creds != null,
                accountCode = creds?.accountCode,
                deviceToken = creds?.deviceToken,
                authVersion = creds?.authVersion
            )
        }
    )
    val syncUiState: StateFlow<SettingsSyncUiState> = _syncUiState.asStateFlow()

    /** Local edit buffer for the base URL field, kept out of the DataStore. */
    private val baseUrlDraft = MutableStateFlow("")

    private val testDataActionState = MutableStateFlow<TestDataActionState>(TestDataActionState.Idle)
    /** Account-code recovery buffers. Declared before [uiState] so the
     *  initializer below can observe them. */
    private val recoveryCodeDraft = MutableStateFlow("")
    private val recoveryState = MutableStateFlow<RecoveryState>(RecoveryState.Idle)

    private val registrationState = MutableStateFlow<V2RegistrationState>(
        savedStateHandle.get<String>(KEY_REG_DRAFT_CODE)
            ?.let { V2RegistrationState.ReviewCode(it) }
            ?: V2RegistrationState.Idle
    )
    private val upgradeState = MutableStateFlow<AccountUpgradeState>(
        savedStateHandle.get<String>(KEY_UPGRADE_DRAFT_CODE)
            ?.let {
                AccountUpgradeState.ConfirmCode(
                    newRecoveryCode = it,
                    otherActiveDevices = savedStateHandle.get<Int>(KEY_UPGRADE_OTHER_DEVICES) ?: 0
                )
            }
            ?: AccountUpgradeState.Idle
    )

    private val exportState = MutableStateFlow<DataExportState>(DataExportState.Idle)
    private val wipeState = MutableStateFlow<DataWipeState>(DataWipeState.Idle)
    private val importState = MutableStateFlow<DataImportState>(DataImportState.Idle)

    private val reportExportState = MutableStateFlow<DataExportState>(DataExportState.Idle)

    private val fileOps: Flow<FileOps> =
        combine(exportState, wipeState, importState, reportExportState) { export, wipe, importOp, report ->
            FileOps(export, wipe, importOp, report)
        }

    private val syncFlows: Flow<SyncActionStates> =
        combine(_syncUiState, registrationState, upgradeState) { syncUi, reg, up ->
            SyncActionStates(syncUi, reg, up)
        }

    private val drafts: Flow<Drafts> =
        combine(fileOps, syncFlows) { files, sync ->
            Drafts(files.export, files.wipe, files.importOp, files.report, sync.syncUi, sync.registration, sync.upgrade)
        }

    init {
        viewModelScope.launch {
            if (credentialStore.loadPendingUpgradeCode() != null) {
                runCatching { accountUpgradeManager.reconcilePendingUpgradeIfNeeded() }
                    .onSuccess { promoted ->
                        if (promoted) refreshCredentialsSnapshot()
                    }
            }
        }
    }

    private fun refreshCredentialsSnapshot() {
        val creds = credentialStore.load()
        _syncUiState.update {
            it.copy(
                hasAccount = creds != null,
                isLinked = creds != null,
                accountCode = creds?.accountCode,
                deviceToken = creds?.deviceToken,
                authVersion = creds?.authVersion
            )
        }
        autoSyncCoordinator.reconcile()
    }
    val uiState: StateFlow<SettingsSyncUiState> = combine(
        userRepository.getUserPreferences(),
        syncDataStore.syncPreferencesFlow,
        drafts,
        testDataActionState,
        recoveryState
    ) { preferences, syncPreferences, draft, actionState, recovery ->
        val creds = credentialStore.load()
        val isLinked = creds != null
        SettingsSyncUiState(
            onlineSyncEnabled = preferences.syncMode == SyncMode.ONLINE_CLOUD.name,
            baseUrlDraft = syncPreferences.baseUrl.orEmpty(),
            storedBaseUrl = syncPreferences.baseUrl.orEmpty(),
            inProgress = syncPreferences.inProgress,
            lastSyncedEpochMillis = syncPreferences.lastSyncedEpochMillis,
            lastError = syncPreferences.lastError,
            testDataState = actionState,
            recoveryCodeDraft = "",
            recoveryState = recovery,
            exportState = draft.exportState,
            wipeState = draft.wipeState,
            importState = draft.importState,
            reportExportState = draft.reportExportState,
            hasAccount = isLinked,
            isLinked = isLinked,
            accountCode = creds?.accountCode,
            deviceToken = creds?.deviceToken,
            authVersion = creds?.authVersion,
            registrationState = draft.registrationState,
            upgradeState = draft.upgradeState,
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
            isPillRegimenEnabled = preferences.isPillRegimenEnabled,
            pillActiveDays = preferences.pillActiveDays,
            pillPauseDays = preferences.pillPauseDays,
            pillStartDate = preferences.pillStartDate,
            pillReminderTime = preferences.pillReminderTime,
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
    fun saveSyncSettings(url: String? = null) {
        val base = (url ?: baseUrlDraft.value).trim()
        viewModelScope.launch {
            val previous = cursorStore.getBaseUrl()
            syncDataStore.setBaseUrl(base.ifBlank { null })
            val next = cursorStore.getBaseUrl()
            if (cursorStore.getDatasetAccountId() != null && previous != next) {
                syncDataStore.setCursor(0)
                syncScheduler.cancelAndJoin()
            }
        }
    }

    fun syncNow() {
        if (credentialStore.load() == null) {
            beginV2Registration()
        } else {
            syncScheduler.syncNow()
        }
    }

    // --- First-run operator-blind v2 registration ---------------------------

    fun beginV2Registration() {
        val code = savedStateHandle.get<String>(KEY_REG_DRAFT_CODE) ?: AccountCodeV2.generate().also {
            savedStateHandle[KEY_REG_DRAFT_CODE] = it
        }
        registrationState.value = V2RegistrationState.ReviewCode(code)
    }

    fun confirmV2Registration() {
        val code = (registrationState.value as? V2RegistrationState.ReviewCode)?.recoveryCode
            ?: savedStateHandle.get<String>(KEY_REG_DRAFT_CODE)
            ?: return
        viewModelScope.launch {
            registrationState.value = V2RegistrationState.Submitting(code)
            runCatching {
                val boundAccount = cursorStore.getDatasetAccountId()
                if (boundAccount != null) {
                    throw ForeignAccountRestore()
                }
                val normalized = AccountCodeV2.normalizeDisplay(code)
                val authKeyHex = RecordCrypto.deriveV2AuthKeyHex(normalized)
                val client = apiClientFactory.create(cursorStore.getBaseUrl())
                val result = client.registerV2(
                    authKeyHex = authKeyHex,
                    deviceName = cursorStore.getDeviceLabel()
                )
                credentialStore.save(
                    SyncCredentials(
                        accountId = result.accountId,
                        accountCode = normalized,
                        deviceToken = result.deviceToken,
                        authVersion = AuthVersion.V2
                    )
                )
                recordSealer.invalidate()
                cursorStore.setDatasetAccountId(result.accountId)
                cursorStore.setBoundBaseUrl(cursorStore.getBaseUrl())
                userRepository.updateSyncMode(SyncMode.ONLINE_CLOUD)
            }.onSuccess {
                savedStateHandle.remove<String>(KEY_REG_DRAFT_CODE)
                registrationState.value = V2RegistrationState.Idle
                refreshCredentialsSnapshot()
                syncScheduler.syncNow()
            }.onFailure { err ->
                registrationState.value = when (err) {
                    is ForeignAccountRestore -> V2RegistrationState.Error(
                        recoveryCode = code,
                        messageResId = R.string.settings_recovery_error_foreign_account
                    )
                    is LegacyServerUnsupportedException -> V2RegistrationState.Error(
                        recoveryCode = code,
                        messageResId = R.string.sync_error_legacy_server
                    )
                    else -> V2RegistrationState.Error(
                        recoveryCode = code,
                        message = err.message,
                        messageResId = if (err.message.isNullOrBlank()) R.string.settings_recovery_error_failed else null
                    )
                }
            }
        }
    }

    fun dismissV2Registration() {
        savedStateHandle.remove<String>(KEY_REG_DRAFT_CODE)
        registrationState.value = V2RegistrationState.Idle
    }

    // --- In-place v1 -> v2 account upgrade ----------------------------------

    fun startAccountUpgrade() {
        viewModelScope.launch {
            upgradeState.value = AccountUpgradeState.Preparing
            runCatching {
                syncScheduler.cancelAndJoin()
                accountUpgradeManager.prepareUpgrade()
            }.onSuccess { prepared ->
                savedStateHandle[KEY_UPGRADE_DRAFT_CODE] = prepared.newRecoveryCode
                savedStateHandle[KEY_UPGRADE_OTHER_DEVICES] = prepared.otherActiveDeviceCount
                upgradeState.value = AccountUpgradeState.ConfirmCode(
                    newRecoveryCode = prepared.newRecoveryCode,
                    otherActiveDevices = prepared.otherActiveDeviceCount
                )
            }.onFailure { err ->
                upgradeState.value = when (err) {
                    is LegacyServerUnsupportedException -> AccountUpgradeState.Error(
                        messageResId = R.string.sync_error_legacy_server
                    )
                    else -> AccountUpgradeState.Error(
                        message = err.message,
                        messageResId = if (err.message.isNullOrBlank()) R.string.settings_recovery_error_failed else null
                    )
                }
            }
        }
    }

    fun confirmAccountUpgrade() {
        val code = when (val cur = upgradeState.value) {
            is AccountUpgradeState.ConfirmCode -> cur.newRecoveryCode
            is AccountUpgradeState.Conflict -> cur.newRecoveryCode
            else -> savedStateHandle.get<String>(KEY_UPGRADE_DRAFT_CODE)
        } ?: return
        viewModelScope.launch {
            upgradeState.value = AccountUpgradeState.Upgrading(code)
            runCatching {
                syncScheduler.cancelAndJoin()
                accountUpgradeManager.executeUpgrade(code)
            }.onSuccess { outcome ->
                when (outcome) {
                    is AccountUpgradeOutcome.Success -> {
                        savedStateHandle.remove<String>(KEY_UPGRADE_DRAFT_CODE)
                        savedStateHandle.remove<Int>(KEY_UPGRADE_OTHER_DEVICES)
                        refreshCredentialsSnapshot()
                        upgradeState.value = AccountUpgradeState.Success(outcome.recordsMigrated)
                    }
                    AccountUpgradeOutcome.AlreadyUpgraded -> {
                        savedStateHandle.remove<String>(KEY_UPGRADE_DRAFT_CODE)
                        savedStateHandle.remove<Int>(KEY_UPGRADE_OTHER_DEVICES)
                        refreshCredentialsSnapshot()
                        upgradeState.value = AccountUpgradeState.AlreadyUpgraded
                    }
                    AccountUpgradeOutcome.ConcurrentChangeConflict -> {
                        upgradeState.value = AccountUpgradeState.Conflict(code)
                    }
                }
            }.onFailure { err ->
                upgradeState.value = when (err) {
                    is LegacyServerUnsupportedException -> AccountUpgradeState.Error(
                        messageResId = R.string.sync_error_legacy_server
                    )
                    else -> AccountUpgradeState.Error(
                        message = err.message,
                        messageResId = if (err.message.isNullOrBlank()) R.string.settings_recovery_error_failed else null
                    )
                }
            }
        }
    }

    fun dismissAccountUpgrade() {
        savedStateHandle.remove<String>(KEY_UPGRADE_DRAFT_CODE)
        savedStateHandle.remove<Int>(KEY_UPGRADE_OTHER_DEVICES)
        upgradeState.value = AccountUpgradeState.Idle
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

    fun unlinkSync() {
        viewModelScope.launch {
            syncScheduler.cancelAndJoin()
            credentialStore.clearPendingUpgradeCode()
            credentialStore.clear()
            recordSealer.invalidate()
            syncDataStore.setCursor(0)
            refreshCredentialsSnapshot()
        }
    }

    // --- Account recovery ---------------------------------------------------

    fun onRecoveryCodeChange(value: String) {
        recoveryCodeDraft.value = value
    }

    fun recoverAccount() {
        recoverAccount(recoveryCodeDraft.value)
    }

    /**
     * Attaches this device to an existing v2 (`LTL2-...`) or legacy v1
     * (`LTL-...`) account. For v2 codes, the 12-bit checksum is checked
     * offline before deriving `K_auth` and calling `POST /v1/auth/devices`;
     * the recovery code itself never leaves the device.
     */
    fun recoverAccount(accountCode: String) {
        val rawCode = accountCode.trim()
        if (rawCode.isBlank()) return
        viewModelScope.launch {
            recoveryState.value = RecoveryState.Loading
            runCatching {
                val detectedVersion = AccountCodeV2.detectVersion(rawCode)
                val client = apiClientFactory.create(cursorStore.getBaseUrl())
                val (canonicalCode, result) = when (detectedVersion) {
                    AuthVersion.V2 -> {
                        val normalized = AccountCodeV2.normalizeDisplay(rawCode)
                        val authKeyHex = RecordCrypto.deriveV2AuthKeyHex(normalized)
                        normalized to client.addDeviceV2(authKeyHex, cursorStore.getDeviceLabel())
                    }
                    AuthVersion.V1 -> {
                        rawCode to client.addDevice(rawCode, cursorStore.getDeviceLabel())
                    }
                }
                val bound = cursorStore.getDatasetAccountId()
                if (bound != null && bound != result.accountId) {
                    throw ForeignAccountRestore()
                }
                credentialStore.clearPendingUpgradeCode()
                credentialStore.clear()
                credentialStore.save(
                    SyncCredentials(
                        accountId = result.accountId,
                        accountCode = canonicalCode,
                        deviceToken = result.deviceToken,
                        authVersion = result.authVersion
                    )
                )
                recordSealer.invalidate()
                syncDataStore.setCursor(0L)
                cursorStore.setDatasetAccountId(result.accountId)
                cursorStore.setBoundBaseUrl(cursorStore.getBaseUrl())
                canonicalCode to result
            }.onSuccess {
                recoveryState.value = RecoveryState.Success
                recoveryCodeDraft.value = ""
                refreshCredentialsSnapshot()
                syncScheduler.syncNow()
            }.onFailure { err ->
                recoveryState.value = when (err) {
                    is AccountCodeV2.InvalidAccountCodeException -> RecoveryState.Error(
                        messageResId = when (err.reason) {
                            AccountCodeV2.ErrorReason.CHECKSUM_MISMATCH -> R.string.settings_recovery_error_checksum
                            AccountCodeV2.ErrorReason.INVALID_FORMAT -> R.string.settings_recovery_error_format
                        }
                    )
                    is ForeignAccountRestore -> RecoveryState.Error(
                        messageResId = R.string.settings_recovery_error_foreign_account
                    )
                    is LegacyServerUnsupportedException -> RecoveryState.Error(
                        messageResId = R.string.sync_error_legacy_server
                    )
                    else -> RecoveryState.Error(
                        message = err.message,
                        messageResId = if (err.message.isNullOrBlank()) R.string.settings_recovery_error_failed else null
                    )
                }
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
                        dataExportManager.exportToStream(stream, appVersion = BuildConfig.VERSION_NAME)
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
                syncScheduler.cancelAndJoin()
                if (!localDataPurgeManager.purgeAllLocalData()) {
                    error("local erase did not clear every secret")
                }
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

    fun inspectBackupJson(json: String) {
        viewModelScope.launch {
            importState.value = DataImportState.Inspecting
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    dataImportManager.inspectBackup(
                        ByteArrayInputStream(json.toByteArray(Charsets.UTF_8))
                    )
                }
            }
            result.onSuccess { inspection ->
                inspection.fold(
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

    suspend fun disableAppLock(currentPin: String): Boolean {
        if (!pinCryptoManager.verifyPin(currentPin)) return false
        userPreferencesDataStore.setAppLockEnabled(false)
        userPreferencesDataStore.setBiometricEnabled(false)
        pinCryptoManager.clearPin()
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

    suspend fun changePin(oldPin: String, newPin: String): Boolean {
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
        }
    }

    private var customTitleJob: Job? = null
    private var customBodyJob: Job? = null

    fun setNotificationCustomTitle(title: String) {
        customTitleJob?.cancel()
        customTitleJob = viewModelScope.launch {
            delay(300)
            userPreferencesDataStore.setNotificationCustomTitle(title)
        }
    }

    fun setNotificationCustomBody(body: String) {
        customBodyJob?.cancel()
        customBodyJob = viewModelScope.launch {
            delay(300)
            userPreferencesDataStore.setNotificationCustomBody(body)
        }
    }

    fun flushCustomNotificationStrings(title: String, body: String) {
        customTitleJob?.cancel()
        customBodyJob?.cancel()
        viewModelScope.launch {
            userPreferencesDataStore.setNotificationCustomTitle(title)
            userPreferencesDataStore.setNotificationCustomBody(body)
        }
    }
    fun setPillRegimenEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && userPreferencesDataStore.userPreferencesFlow.first().pillStartDate == null) {
                userPreferencesDataStore.setPillStartDate(LocalDate.now().toString())
            }
            userPreferencesDataStore.setPillRegimenEnabled(enabled)
            notificationScheduler.reconcileAllSchedules()
            duoProjectionSynchronizer.publishIfTrackerPaired()
        }
    }

    fun setPillRegimenSchedule(activeDays: Int, pauseDays: Int) {
        viewModelScope.launch {
            userPreferencesDataStore.setPillActiveDays(activeDays)
            userPreferencesDataStore.setPillPauseDays(pauseDays)
            notificationScheduler.reconcileAllSchedules()
            duoProjectionSynchronizer.publishIfTrackerPaired()
        }
    }

    fun setPillStartDate(startDate: LocalDate) {
        viewModelScope.launch {
            userPreferencesDataStore.setPillStartDate(startDate.toString())
            notificationScheduler.reconcileAllSchedules()
            duoProjectionSynchronizer.publishIfTrackerPaired()
        }
    }

    fun setPillReminderTime(time: String) {
        viewModelScope.launch {
            userPreferencesDataStore.setPillReminderTime(time)
            notificationScheduler.reconcileAllSchedules()
            duoProjectionSynchronizer.publishIfTrackerPaired()
        }
    }

    suspend fun verifyPin(pin: String): Boolean = pinCryptoManager.verifyPin(pin)
}

sealed interface V2RegistrationState {
    data object Idle : V2RegistrationState
    data class ReviewCode(val recoveryCode: String) : V2RegistrationState
    data class Submitting(val recoveryCode: String) : V2RegistrationState
    data class Error(
        val recoveryCode: String,
        val message: String? = null,
        @param:StringRes val messageResId: Int? = null
    ) : V2RegistrationState
}

sealed interface AccountUpgradeState {
    data object Idle : AccountUpgradeState
    data object Preparing : AccountUpgradeState
    data class ConfirmCode(val newRecoveryCode: String, val otherActiveDevices: Int) : AccountUpgradeState
    data class Upgrading(val newRecoveryCode: String) : AccountUpgradeState
    data class Conflict(val newRecoveryCode: String) : AccountUpgradeState
    data class Success(val recordsMigrated: Int) : AccountUpgradeState
    data object AlreadyUpgraded : AccountUpgradeState
    data class Error(val message: String? = null, @param:StringRes val messageResId: Int? = null) : AccountUpgradeState
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
    val isLinked: Boolean = false,
    val accountCode: String? = null,
    val deviceToken: String? = null,
    val authVersion: AuthVersion? = null,
    val registrationState: V2RegistrationState = V2RegistrationState.Idle,
    val upgradeState: AccountUpgradeState = AccountUpgradeState.Idle,
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
    val isPillRegimenEnabled: Boolean = false,
    val pillActiveDays: Int = 21,
    val pillPauseDays: Int = 7,
    val pillStartDate: String? = null,
    val pillReminderTime: String = "20:00",
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

private data class SyncActionStates(
    val syncUi: SettingsSyncUiState,
    val registration: V2RegistrationState,
    val upgrade: AccountUpgradeState
)

private data class Drafts(
    val exportState: DataExportState,
    val wipeState: DataWipeState,
    val importState: DataImportState,
    val reportExportState: DataExportState,
    val syncUi: SettingsSyncUiState = SettingsSyncUiState(),
    val registrationState: V2RegistrationState = V2RegistrationState.Idle,
    val upgradeState: AccountUpgradeState = AccountUpgradeState.Idle
)

sealed interface TestDataActionState {
    data object Idle : TestDataActionState
    data object Loading : TestDataActionState
    data object SuccessSeeded : TestDataActionState
    data object SuccessCleared : TestDataActionState
    data class Error(val message: String? = null, @param:StringRes val messageResId: Int? = null) : TestDataActionState
}

private class ForeignAccountRestore : Exception()
