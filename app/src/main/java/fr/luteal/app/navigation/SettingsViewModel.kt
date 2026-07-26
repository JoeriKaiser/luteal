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

/**
 * Drives the Settings sync controls. Sync itself runs in a background
 * WorkManager worker; this only flips [SyncMode], edits the base URL
 * (debug-only local trial), enqueues a sync, and observes the outcome. The
 * rest of the app keeps observing Room and never waits on this.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val syncDataStore: SyncDataStore,
    private val syncScheduler: SyncScheduler,
    private val testDataSeeder: TestDataSeeder,
    private val credentialStore: SyncCredentialStore,
    private val apiClientFactory: FolicularApiClientFactory,
    private val cursorStore: SyncCursorStore
) : ViewModel() {

    /** Local edit buffer for the base URL field, kept out of the DataStore. */
    private val baseUrlDraft = MutableStateFlow("")

    /** Local edit buffer for the invite code field, kept out of the DataStore. */
    private val inviteCodeDraft = MutableStateFlow("")

    private val testDataActionState = MutableStateFlow<TestDataActionState>(TestDataActionState.Idle)

    /** Account-code recovery buffers. Declared before [uiState] so the
     *  initializer below can observe them. */
    private val recoveryCodeDraft = MutableStateFlow("")
    private val recoveryState = MutableStateFlow<RecoveryState>(RecoveryState.Idle)

    /** Text drafts folded into one flow: `combine` takes at most five. */
    private val drafts: Flow<Drafts> =
        combine(baseUrlDraft, inviteCodeDraft, recoveryCodeDraft) { base, invite, recovery ->
            Drafts(base, invite, recovery)
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
            inviteCodeDraft = draft.inviteCode,
            storedInviteCode = syncPreferences.inviteCode.orEmpty(),
            inProgress = syncPreferences.inProgress,
            lastSyncedEpochMillis = syncPreferences.lastSyncedEpochMillis,
            lastError = syncPreferences.lastError,
            testDataState = actionState,
            recoveryCodeDraft = draft.recoveryCode,
            recoveryState = recovery,
            hasAccount = credentialStore.load() != null,
            declaredContexts = preferences.declaredContexts,
            ageBand = AgeBand.fromId(preferences.ageBand)
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

    fun onBaseUrlChange(value: String) {
        baseUrlDraft.update { value }
    }

    fun onInviteCodeChange(value: String) {
        inviteCodeDraft.update { value }
    }

    /** Persists the invite code (all builds) and the dev base URL. */
    fun saveSyncSettings() {
        val invite = inviteCodeDraft.value.trim()
        val base = baseUrlDraft.value.trim()
        viewModelScope.launch {
            syncDataStore.setInviteCode(invite.ifBlank { null })
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
                testDataActionState.value = TestDataActionState.Error(it.message ?: "Error seeding data")
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
                testDataActionState.value = TestDataActionState.Error(it.message ?: "Error clearing data")
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
            recoveryState.value = RecoveryState.Error(RECOVERY_ALREADY_LINKED)
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
                recoveryState.value = RecoveryState.Error(err.message ?: RECOVERY_FAILED)
            }
        }
    }

    fun clearRecoveryState() {
        recoveryState.value = RecoveryState.Idle
    }
}

/** Copy shown when the device already belongs to an account. */
const val RECOVERY_ALREADY_LINKED =
    "Cet appareil est déjà relié à un compte. Effacez les données locales avant de restaurer un autre compte."

const val RECOVERY_FAILED = "La restauration a échoué."

sealed interface RecoveryState {
    data object Idle : RecoveryState
    data object Loading : RecoveryState
    data object Success : RecoveryState
    data class Error(val message: String) : RecoveryState
}

data class SettingsSyncUiState(
    val onlineSyncEnabled: Boolean = false,
    val baseUrlDraft: String = "",
    val storedBaseUrl: String = "",
    val inviteCodeDraft: String = "",
    val storedInviteCode: String = "",
    val inProgress: Boolean = false,
    val lastSyncedEpochMillis: Long? = null,
    val lastError: String? = null,
    val testDataState: TestDataActionState = TestDataActionState.Idle,
    val recoveryCodeDraft: String = "",
    val recoveryState: RecoveryState = RecoveryState.Idle,
    /** True once this device holds credentials; recovery is offered only before. */
    val hasAccount: Boolean = false,
    /** Contexts the user has declared, editable after onboarding. */
    val declaredContexts: Set<TrackingContext> = emptySet(),
    /** Null means no band declared, which is a valid state. */
    val ageBand: AgeBand? = null
)

/** Text drafts held together so [combine] stays within its five-flow limit. */
private data class Drafts(
    val baseUrl: String,
    val inviteCode: String,
    val recoveryCode: String
)

sealed interface TestDataActionState {
    data object Idle : TestDataActionState
    data object Loading : TestDataActionState
    data object SuccessSeeded : TestDataActionState
    data object SuccessCleared : TestDataActionState
    data class Error(val message: String) : TestDataActionState
}
