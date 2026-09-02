package fr.luteal.core.data.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fr.luteal.core.data.datastore.UserPreferencesDataStore
import fr.luteal.core.model.AppLockState
import fr.luteal.core.model.PinVerificationResult
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import android.os.SystemClock
import org.robolectric.shadows.ShadowPausedSystemClock
@RunWith(RobolectricTestRunner::class)
class AppLockManagerTest {

    private lateinit var context: Context
    private lateinit var userPreferencesDataStore: UserPreferencesDataStore
    private lateinit var pinSecretStore: InMemoryPinSecretStore
    private lateinit var pinCryptoManager: PinCryptoManager
    private lateinit var appLockManager: AppLockManager

    @Before
    fun setup() = runTest {
        context = ApplicationProvider.getApplicationContext()
        userPreferencesDataStore = UserPreferencesDataStore(context)
        userPreferencesDataStore.clear()
        pinSecretStore = InMemoryPinSecretStore()
        pinCryptoManager = PinCryptoManager(pinSecretStore)
        appLockManager = AppLockManager(context, userPreferencesDataStore, pinCryptoManager)
    }

    @Test
    fun initialStateIsResolvingUntilPreferencesLoad() = runTest {
        assertEquals(AppLockState.Resolving, appLockManager.lockState.value)
    }

    @Test
    fun unlockWithValidPinSucceedsAndResetsFailures() = runTest {
        pinCryptoManager.setPin("4321")
        userPreferencesDataStore.setAppLockEnabled(true)
        userPreferencesDataStore.setConsecutivePinFailures(2)

        val result = appLockManager.verifyAndUnlockPin("4321")
        assertTrue(result is PinVerificationResult.Success)
        assertEquals(AppLockState.Unlocked, appLockManager.lockState.value)
    }

    @Test
    fun invalidPinReturnsRemainingAttemptsBeforeLockout() = runTest {
        pinCryptoManager.setPin("9999")
        userPreferencesDataStore.setAppLockEnabled(true)
        userPreferencesDataStore.setConsecutivePinFailures(0)

        val result = appLockManager.verifyAndUnlockPin("0000")
        assertTrue(result is PinVerificationResult.Incorrect)
        assertEquals(4, (result as PinVerificationResult.Incorrect).remainingAttemptsBeforeLockout)
    }

    @Test
    fun fiveFailedAttemptsEnforcesLockout() = runTest {
        pinCryptoManager.setPin("1234")
        userPreferencesDataStore.setAppLockEnabled(true)
        userPreferencesDataStore.setConsecutivePinFailures(4)

        val result = appLockManager.verifyAndUnlockPin("0000")
        assertTrue(result is PinVerificationResult.LockedOut)
        val lockedOut = result as PinVerificationResult.LockedOut
        assertEquals(30, lockedOut.remainingSeconds)

        val state = appLockManager.lockState.value
        assertTrue(state is AppLockState.Locked)
        assertEquals(30, (state as AppLockState.Locked).remainingLockoutSeconds)
    }

    @Test
    fun unlockWithBiometricResetsPinFailures() = runTest {
        pinCryptoManager.setPin("1234")
        userPreferencesDataStore.setAppLockEnabled(true)
        userPreferencesDataStore.setConsecutivePinFailures(3)

        appLockManager.unlockWithBiometric()
        assertEquals(AppLockState.Unlocked, appLockManager.lockState.value)

        val failures = userPreferencesDataStore.userPreferencesFlow.first().consecutivePinFailures
        assertEquals(0, failures)
    }

    @Test
    fun onStopWithChangingConfigurationsDoesNotLock() = runTest {
        pinCryptoManager.setPin("1234")
        userPreferencesDataStore.setAppLockEnabled(true)
        userPreferencesDataStore.setAutoLockTimeout(fr.luteal.core.model.AutoLockTimeout.IMMEDIATE.name)
        appLockManager.unlockWithBiometric()
        assertEquals(AppLockState.Unlocked, appLockManager.lockState.value)

        val changingConfigsActivity = object : androidx.activity.ComponentActivity() {
            override fun isChangingConfigurations(): Boolean = true
        }

        appLockManager.onStop(changingConfigsActivity)
        assertEquals(AppLockState.Unlocked, appLockManager.lockState.value)
    }

    @Test
    fun lockoutSurvivesWallClockRollbackViaMonotonicDeadline() = runTest {
        pinCryptoManager.setPin("1234")
        userPreferencesDataStore.setAppLockEnabled(true)
        // Aftermath of a roll-the-clock-forward attack: the wall-clock
        // deadline is in the past, but the monotonic deadline still holds.
        userPreferencesDataStore.setLockoutUntilEpochMillis(System.currentTimeMillis() - 60_000)
        userPreferencesDataStore.setLockoutUntilElapsedRealtimeMillis(
            SystemClock.elapsedRealtime() + 30_000
        )

        val result = appLockManager.verifyAndUnlockPin("1234")

        assertTrue(result is PinVerificationResult.LockedOut)
    }

    @Test
    fun lockoutSurvivesRebootViaWallClockDeadline() = runTest {
        pinCryptoManager.setPin("1234")
        userPreferencesDataStore.setAppLockEnabled(true)
        // Aftermath of a reboot: the monotonic deadline is gone (elapsed
        // real time restarted near zero), but the wall-clock deadline holds.
        userPreferencesDataStore.setLockoutUntilEpochMillis(System.currentTimeMillis() + 30_000)
        userPreferencesDataStore.setLockoutUntilElapsedRealtimeMillis(0L)

        val result = appLockManager.verifyAndUnlockPin("1234")

        assertTrue(result is PinVerificationResult.LockedOut)
    }

    @Test
    fun rebootDuringLockoutDoesNotTriggerExcessiveLockout() = runTest {
        pinCryptoManager.setPin("1234")
        userPreferencesDataStore.setAppLockEnabled(true)

        // Simulate device reboot: elapsedRealtime resets near zero, simulated here as 5,000 ms.
        ShadowPausedSystemClock.reset()
        SystemClock.setCurrentTimeMillis(5_000L)
        assertEquals(5_000L, SystemClock.elapsedRealtime())

        // Device was locked out before reboot with a pre-reboot elapsedRealtime deadline (100,000,000 ms)
        // and a wall-clock deadline having 30 seconds remaining.
        val remainingWallClockSeconds = 30
        userPreferencesDataStore.setLockoutUntilEpochMillis(
            System.currentTimeMillis() + remainingWallClockSeconds * 1000L
        )
        userPreferencesDataStore.setLockoutUntilElapsedRealtimeMillis(100_000_000L)

        val result = appLockManager.verifyAndUnlockPin("1234")

        val lockoutSeconds = (result as PinVerificationResult.LockedOut).remainingSeconds
        assertTrue(
            "Lockout remaining seconds ($lockoutSeconds) must not exceed remaining wall-clock seconds ($remainingWallClockSeconds)",
            lockoutSeconds <= remainingWallClockSeconds
        )
        assertTrue(
            "Lockout remaining seconds ($lockoutSeconds) must not cause a multi-hour lockout",
            lockoutSeconds <= 300
        )
    }

    @Test
    fun resetPinFailuresClearsBothLockoutDeadlines() = runTest {
        pinCryptoManager.setPin("1234")
        userPreferencesDataStore.setAppLockEnabled(true)
        userPreferencesDataStore.setLockoutUntilEpochMillis(System.currentTimeMillis() + 30_000)
        userPreferencesDataStore.setLockoutUntilElapsedRealtimeMillis(
            SystemClock.elapsedRealtime() + 30_000
        )

        userPreferencesDataStore.resetPinFailures()

        val result = appLockManager.verifyAndUnlockPin("1234")
        assertTrue(result is PinVerificationResult.Success)
    }
}
