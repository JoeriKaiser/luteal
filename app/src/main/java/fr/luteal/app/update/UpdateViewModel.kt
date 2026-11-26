package fr.luteal.app.update

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.luteal.app.BuildConfig
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UpdateUiState(
    val checksEnabled: Boolean = false,
    val check: UpdateCheckState = UpdateCheckState.Idle,
    val install: UpdateInstallState = UpdateInstallState.Idle
)

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateManager: UpdateManager,
    private val preferencesStore: UpdatePreferencesStore,
    private val scheduler: UpdateScheduler,
    private val notifier: UpdateNotifier
) : ViewModel() {

    val isAvailable: Boolean = updateManager.isAvailableForThisInstall

    val uiState: StateFlow<UpdateUiState> = combine(
        preferencesStore.preferences,
        updateManager.checkState,
        updateManager.installState
    ) { prefs, check, install ->
        UpdateUiState(checksEnabled = prefs.checksEnabled, check = check, install = install)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UpdateUiState())

    private var checkJob: Job? = null
    private var installJob: Job? = null

    init {
        // After an "update available" notification, show the release without a manual check.
        viewModelScope.launch {
            val prefs = preferencesStore.preferences.first()
            val notified = prefs.lastNotifiedVersion?.let(AppVersion::parse)
            val installed = AppVersion.parse(BuildConfig.VERSION_NAME)
            if (isAvailable && prefs.checksEnabled && notified != null && installed != null && notified > installed &&
                updateManager.checkState.value == UpdateCheckState.Idle
            ) {
                checkNow()
            }
        }
    }

    fun enableChecks() {
        if (!isAvailable) return
        viewModelScope.launch {
            preferencesStore.setChecksEnabled(true)
            scheduler.schedule()
        }
    }

    fun disableChecks() {
        checkJob?.cancel()
        installJob?.cancel()
        viewModelScope.launch {
            preferencesStore.setChecksEnabled(false)
            scheduler.cancel()
            notifier.cancel()
            updateManager.resetCheck()
        }
    }

    fun checkNow() {
        if (!isAvailable || checkJob?.isActive == true) return
        checkJob = viewModelScope.launch { updateManager.checkNow() }
    }

    fun downloadAndInstall() {
        val info = (updateManager.checkState.value as? UpdateCheckState.Available)?.info ?: return
        if (installJob?.isActive == true) return
        installJob = viewModelScope.launch { updateManager.downloadAndInstall(info) }
    }

    fun installPermissionIntent(): Intent = updateManager.unknownSourcesSettingsIntent()

    fun onConfirmationLaunched(launched: Boolean) = updateManager.onConfirmationLaunched(launched)

    fun onReturnedFromInstallPermission() {
        if (updateManager.installState.value == UpdateInstallState.NeedsInstallPermission &&
            updateManager.canRequestPackageInstalls()
        ) {
            downloadAndInstall()
        }
    }
}
