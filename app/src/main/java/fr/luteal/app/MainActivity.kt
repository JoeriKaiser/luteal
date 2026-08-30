package fr.luteal.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import fr.luteal.app.navigation.AppLockResolvingBarrier
import fr.luteal.app.navigation.AppLockScreen
import fr.luteal.app.navigation.LutealMainScaffold
import fr.luteal.core.data.datastore.UserPreferencesDataStore
import fr.luteal.core.data.security.AppLockManager
import fr.luteal.core.designsystem.theme.LutealTheme
import fr.luteal.core.model.AppLockState
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var appLockManager: AppLockManager

    @Inject
    lateinit var userPreferencesDataStore: UserPreferencesDataStore

    private var widgetDestination by mutableStateOf<String?>(null)
    private var pendingImportJson by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetDestination = intent.widgetDestination()
        pendingImportJson = intent.importBackupJson()
        intent.removeExtra(EXTRA_IMPORT_JSON)
        enableEdgeToEdge()

        lifecycle.addObserver(appLockManager)

        // Observe screen masking preference and apply/clear FLAG_SECURE
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userPreferencesDataStore.userPreferencesFlow.collect { prefs ->
                    if (prefs.isScreenMaskingEnabled) {
                        window.setFlags(
                            WindowManager.LayoutParams.FLAG_SECURE,
                            WindowManager.LayoutParams.FLAG_SECURE
                        )
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
        }

        setContent {
            LutealTheme {
                val lockState by appLockManager.lockState.collectAsStateWithLifecycle()
                val barrierUp = lockState is AppLockState.Resolving || lockState is AppLockState.Locked
                // PIN length comes from a Keystore read: produceState keeps
                // that suspend call out of composition.
                @SuppressLint("ProduceStateDoesNotAssignValue")
                val expectedPinLength by produceState<Int?>(initialValue = null, lockState) {
                    value = appLockManager.pinLength()
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = if (barrierUp) {
                            Modifier.clearAndSetSemantics { }
                        } else {
                            Modifier
                        }
                    ) {
                        LutealMainScaffold(
                            widgetDestination = widgetDestination,
                            onWidgetDestinationConsumed = { widgetDestination = null },
                            pendingImportJson = pendingImportJson,
                            onPendingImportConsumed = { pendingImportJson = null }
                        )
                    }
                    if (lockState is AppLockState.Resolving) {
                        AppLockResolvingBarrier()
                    }
                    val locked = lockState as? AppLockState.Locked
                    if (locked != null) {
                        AppLockScreen(
                            isBiometricAvailable = locked.isBiometricAvailable,
                            remainingLockoutSeconds = locked.remainingLockoutSeconds,
                            expectedPinLength = expectedPinLength,
                            onVerifyPin = { pin -> appLockManager.verifyAndUnlockPin(pin) },
                            onRequestBiometricPrompt = ::showBiometricPrompt
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        widgetDestination = intent.widgetDestination()
        pendingImportJson = intent.importBackupJson()
        intent.removeExtra(EXTRA_IMPORT_JSON)
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                lifecycleScope.launch {
                    appLockManager.unlockWithBiometric()
                }
            }
        }
        val prompt = BiometricPrompt(this, executor, callback)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.lock_biometric_title))
            .setSubtitle(getString(R.string.lock_biometric_subtitle))
            .setNegativeButtonText(getString(R.string.lock_biometric_negative))
            .build()
        prompt.authenticate(promptInfo)
    }

    private fun Intent.widgetDestination(): String? =
        takeIf { action == ACTION_OPEN_WIDGET_DESTINATION }
            ?.getStringExtra(EXTRA_WIDGET_DESTINATION)

    private fun Intent.importBackupJson(): String? =
        takeIf { action == ACTION_IMPORT_BACKUP }
            ?.getStringExtra(EXTRA_IMPORT_JSON)

    companion object {
        const val ACTION_OPEN_WIDGET_DESTINATION = "fr.luteal.app.action.OPEN_WIDGET_DESTINATION"
        const val ACTION_IMPORT_BACKUP = "fr.luteal.app.action.IMPORT_BACKUP"
        const val EXTRA_IMPORT_JSON = "fr.luteal.app.extra.IMPORT_JSON"
        const val EXTRA_IMPORT_JSON_BASE64 = "fr.luteal.app.extra.IMPORT_JSON_BASE64"
        const val EXTRA_WIDGET_DESTINATION = "fr.luteal.app.extra.WIDGET_DESTINATION"
        const val WIDGET_DESTINATION_TODAY = "today"
        const val WIDGET_DESTINATION_TODAY_EDITOR = "today_editor"
        const val WIDGET_DESTINATION_DUO = "duo"
    }
}
