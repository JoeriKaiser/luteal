package fr.luteal.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.luteal.app.sync.SyncScheduler
import fr.luteal.core.data.datastore.SyncDataStore
import fr.luteal.core.data.repository.UserRepository
import fr.luteal.core.data.seed.TestDataSeeder
import fr.luteal.core.model.SyncMode
import fr.luteal.core.network.auth.SyncCredentialStore
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the debug "local sync trial" controls in Settings. Sync itself runs
 * in a background WorkManager worker; this only flips [SyncMode], edits the
 * dev base URL, enqueues a sync, and observes the outcome. The rest of the app
 * keeps observing Room and never waits on this.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val syncDataStore: SyncDataStore,
    private val syncScheduler: SyncScheduler,
    private val testDataSeeder: TestDataSeeder,
    private val credentialStore: SyncCredentialStore
) : ViewModel() {

    /** Local edit buffer for the base URL field, kept out of the DataStore. */
    private val baseUrlDraft = MutableStateFlow("")

    private val testDataActionState = MutableStateFlow<TestDataActionState>(TestDataActionState.Idle)

    val uiState: StateFlow<SettingsSyncUiState> = combine(
        userRepository.getUserPreferences(),
        syncDataStore.syncPreferencesFlow,
        baseUrlDraft,
        testDataActionState
    ) { preferences, syncPreferences, draft, actionState ->
        SettingsSyncUiState(
            onlineSyncEnabled = preferences.syncMode == SyncMode.ONLINE_CLOUD.name,
            baseUrlDraft = draft,
            storedBaseUrl = syncPreferences.baseUrl.orEmpty(),
            inProgress = syncPreferences.inProgress,
            lastSyncedEpochMillis = syncPreferences.lastSyncedEpochMillis,
            lastError = syncPreferences.lastError,
            testDataState = actionState
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

    fun onBaseUrlChange(value: String) {
        baseUrlDraft.update { value }
    }

    fun saveBaseUrl() {
        val value = baseUrlDraft.value.trim()
        viewModelScope.launch { syncDataStore.setBaseUrl(value.ifBlank { null }) }
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
}

data class SettingsSyncUiState(
    val onlineSyncEnabled: Boolean = false,
    val baseUrlDraft: String = "",
    val storedBaseUrl: String = "",
    val inProgress: Boolean = false,
    val lastSyncedEpochMillis: Long? = null,
    val lastError: String? = null,
    val testDataState: TestDataActionState = TestDataActionState.Idle
)

sealed interface TestDataActionState {
    data object Idle : TestDataActionState
    data object Loading : TestDataActionState
    data object SuccessSeeded : TestDataActionState
    data object SuccessCleared : TestDataActionState
    data class Error(val message: String) : TestDataActionState
}
