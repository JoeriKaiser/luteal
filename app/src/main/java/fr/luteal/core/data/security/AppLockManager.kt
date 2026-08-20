package fr.luteal.core.data.security

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.luteal.core.data.datastore.UserPreferencesDataStore
import fr.luteal.core.model.AppLockState
import fr.luteal.core.model.AutoLockTimeout
import fr.luteal.core.model.PinVerificationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

@Singleton
class AppLockManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val pinCryptoManager: PinCryptoManager
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _lockState = MutableStateFlow<AppLockState>(AppLockState.Resolving)
    val lockState: StateFlow<AppLockState> = _lockState.asStateFlow()

    private var lastBackgroundedTimestampElapsedRealtime: Long? = null

    init {
        scope.launch {
            val prefs = userPreferencesDataStore.userPreferencesFlow.first()
            if (prefs.isAppLockEnabled && pinCryptoManager.hasPinConfigured()) {
                val now = System.currentTimeMillis()
                val remainingSeconds = if (now < prefs.lockoutUntilEpochMillis) {
                    ceil((prefs.lockoutUntilEpochMillis - now) / 1000.0).toInt()
                } else 0
                _lockState.value = AppLockState.Locked(
                    isBiometricAvailable = isBiometricAvailable() && prefs.isBiometricEnabled,
                    remainingLockoutSeconds = remainingSeconds
                )
            } else {
                _lockState.value = AppLockState.NotConfigured
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        scope.launch {
            val prefs = userPreferencesDataStore.userPreferencesFlow.first()
            if (!prefs.isAppLockEnabled || !pinCryptoManager.hasPinConfigured()) {
                _lockState.value = AppLockState.NotConfigured
                return@launch
            }

            val lastBg = lastBackgroundedTimestampElapsedRealtime
            if (lastBg != null) {
                val elapsedMillis = SystemClock.elapsedRealtime() - lastBg
                val timeout = AutoLockTimeout.fromName(prefs.autoLockTimeout)
                if (elapsedMillis >= timeout.durationSeconds * 1000L) {
                    val now = System.currentTimeMillis()
                    val remainingSeconds = if (now < prefs.lockoutUntilEpochMillis) {
                        ceil((prefs.lockoutUntilEpochMillis - now) / 1000.0).toInt()
                    } else 0
                    _lockState.value = AppLockState.Locked(
                        isBiometricAvailable = isBiometricAvailable() && prefs.isBiometricEnabled,
                        remainingLockoutSeconds = remainingSeconds
                    )
                }
            } else if (_lockState.value !is AppLockState.Unlocked) {
                val now = System.currentTimeMillis()
                val remainingSeconds = if (now < prefs.lockoutUntilEpochMillis) {
                    ceil((prefs.lockoutUntilEpochMillis - now) / 1000.0).toInt()
                } else 0
                _lockState.value = AppLockState.Locked(
                    isBiometricAvailable = isBiometricAvailable() && prefs.isBiometricEnabled,
                    remainingLockoutSeconds = remainingSeconds
                )
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        val isChangingConfigs = (owner as? Activity)?.isChangingConfigurations == true
        if (isChangingConfigs) return

        lastBackgroundedTimestampElapsedRealtime = SystemClock.elapsedRealtime()
        scope.launch {
            val prefs = userPreferencesDataStore.userPreferencesFlow.first()
            if (prefs.isAppLockEnabled && pinCryptoManager.hasPinConfigured()) {
                val timeout = AutoLockTimeout.fromName(prefs.autoLockTimeout)
                if (timeout == AutoLockTimeout.IMMEDIATE) {
                    val now = System.currentTimeMillis()
                    val remainingSeconds = if (now < prefs.lockoutUntilEpochMillis) {
                        ceil((prefs.lockoutUntilEpochMillis - now) / 1000.0).toInt()
                    } else 0
                    _lockState.value = AppLockState.Locked(
                        isBiometricAvailable = isBiometricAvailable() && prefs.isBiometricEnabled,
                        remainingLockoutSeconds = remainingSeconds
                    )
                }
            }
        }
    }

    suspend fun verifyAndUnlockPin(pin: String): PinVerificationResult {
        val prefs = userPreferencesDataStore.userPreferencesFlow.first()
        val now = System.currentTimeMillis()

        if (now < prefs.lockoutUntilEpochMillis) {
            val remainingSeconds = ceil((prefs.lockoutUntilEpochMillis - now) / 1000.0).toInt()
            _lockState.value = AppLockState.Locked(
                isBiometricAvailable = isBiometricAvailable() && prefs.isBiometricEnabled,
                remainingLockoutSeconds = remainingSeconds
            )
            return PinVerificationResult.LockedOut(remainingSeconds)
        }

        val isValid = pinCryptoManager.verifyPin(pin)
        return if (isValid) {
            userPreferencesDataStore.resetPinFailures()
            _lockState.value = AppLockState.Unlocked
            PinVerificationResult.Success
        } else {
            val newFailures = prefs.consecutivePinFailures + 1
            userPreferencesDataStore.setConsecutivePinFailures(newFailures)
            if (newFailures >= 5) {
                val lockoutSeconds = calculateLockoutSeconds(newFailures)
                val lockoutUntil = now + (lockoutSeconds * 1000L)
                userPreferencesDataStore.setLockoutUntilEpochMillis(lockoutUntil)
                _lockState.value = AppLockState.Locked(
                    isBiometricAvailable = isBiometricAvailable() && prefs.isBiometricEnabled,
                    remainingLockoutSeconds = lockoutSeconds
                )
                PinVerificationResult.LockedOut(lockoutSeconds)
            } else {
                PinVerificationResult.Incorrect(remainingAttemptsBeforeLockout = 5 - newFailures)
            }
        }
    }

    suspend fun unlockWithBiometric(): Boolean {
        userPreferencesDataStore.resetPinFailures()
        _lockState.value = AppLockState.Unlocked
        return true
    }

    fun lock() {
        scope.launch {
            val prefs = userPreferencesDataStore.userPreferencesFlow.first()
            if (prefs.isAppLockEnabled && pinCryptoManager.hasPinConfigured()) {
                val now = System.currentTimeMillis()
                val remainingSeconds = if (now < prefs.lockoutUntilEpochMillis) {
                    ceil((prefs.lockoutUntilEpochMillis - now) / 1000.0).toInt()
                } else 0
                _lockState.value = AppLockState.Locked(
                    isBiometricAvailable = isBiometricAvailable() && prefs.isBiometricEnabled,
                    remainingLockoutSeconds = remainingSeconds
                )
            }
        }
    }

    fun pinLength(): Int? = pinCryptoManager.pinLength()

    fun isBiometricHardwareAvailable(): Boolean {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun isBiometricAvailable(): Boolean = isBiometricHardwareAvailable()

    private fun calculateLockoutSeconds(consecutiveFailures: Int): Int {
        return when {
            consecutiveFailures == 5 -> 30
            consecutiveFailures == 6 -> 60
            consecutiveFailures == 7 -> 120
            else -> 300
        }
    }
}
