package fr.luteal.app.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import fr.luteal.app.R
import fr.luteal.core.designsystem.component.LutealPrimaryButton
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.model.PinEntryPolicy
import fr.luteal.core.model.PinVerificationResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun AppLockResolvingBarrier() {
    BackHandler(enabled = true) { }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = stringResource(R.string.lock_screen_title),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
fun AppLockScreen(
    isBiometricAvailable: Boolean,
    remainingLockoutSeconds: Int,
    expectedPinLength: Int? = null,
    onVerifyPin: suspend (String) -> PinVerificationResult,
    onRequestBiometricPrompt: () -> Unit
) {
    BackHandler(enabled = true) { }
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentLockoutSeconds by remember(remainingLockoutSeconds) { mutableIntStateOf(remainingLockoutSeconds) }

    val shakeOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current

    // Handle lockout countdown timer
    LaunchedEffect(currentLockoutSeconds) {
        if (currentLockoutSeconds > 0) {
            while (currentLockoutSeconds > 0) {
                delay(1000L)
                currentLockoutSeconds -= 1
            }
            errorMessage = null
        }
    }

    // Auto-trigger biometric prompt on screen launch if available and not locked out
    LaunchedEffect(Unit) {
        if (isBiometricAvailable && currentLockoutSeconds <= 0) {
            onRequestBiometricPrompt()
        }
    }

    val isLockedOut = currentLockoutSeconds > 0

    fun triggerShake() {
        scope.launch {
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 400
                    0f at 0
                    -20f at 50
                    20f at 100
                    -15f at 150
                    15f at 200
                    -10f at 250
                    10f at 300
                    -5f at 350
                    0f at 400
                }
            )
        }
    }

    fun submitPin(pin: String) {
        if (isLockedOut) return
        scope.launch {
            val result = onVerifyPin(pin)
            when (result) {
                is PinVerificationResult.Success -> {
                    errorMessage = null
                    enteredPin = ""
                }
                is PinVerificationResult.Incorrect -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    triggerShake()
                    enteredPin = ""
                    errorMessage = context.getString(
                        R.string.lock_incorrect_pin,
                        result.remainingAttemptsBeforeLockout
                    )
                }
                is PinVerificationResult.LockedOut -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    triggerShake()
                    enteredPin = ""
                    currentLockoutSeconds = result.remainingSeconds
                }
            }
        }
    }

    fun onDigitPress(digit: String) {
        if (isLockedOut || enteredPin.length >= 8) return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val newPin = enteredPin + digit
        enteredPin = newPin

        if (PinEntryPolicy.shouldAutoSubmit(newPin.length, expectedPinLength)) {
            submitPin(newPin)
        }
    }

    fun onBackspacePress() {
        if (isLockedOut || enteredPin.isEmpty()) return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        enteredPin = enteredPin.dropLast(1)
        errorMessage = null
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = LutealSpacing.lg, vertical = LutealSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm),
                modifier = Modifier.padding(top = LutealSpacing.xl)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Text(
                    text = stringResource(R.string.lock_screen_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = stringResource(R.string.lock_enter_pin),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(LutealSpacing.sm))

                // Animated PIN Indicators
                val dotCount = maxOf(4, enteredPin.length)
                val accessibilityDescription = stringResource(
                    R.string.lock_accessibility_progress,
                    enteredPin.length
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(LutealSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
                        .padding(vertical = LutealSpacing.md)
                        .semantics { contentDescription = accessibilityDescription }
                ) {
                    for (i in 0 until dotCount) {
                        val isFilled = i < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isFilled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isFilled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .clearAndSetSemantics { }
                        )
                    }
                }

                // Error / Lockout Messages
                if (isLockedOut) {
                    Text(
                        text = stringResource(R.string.lock_rate_limited, currentLockoutSeconds),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold
                    )
                } else if (errorMessage != null) {
                    Text(
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Numpad Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(LutealSpacing.md),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = LutealSpacing.lg)
            ) {
                val digits = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9")
                )

                digits.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xl),
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row.forEach { digit ->
                            NumpadKey(
                                text = digit,
                                enabled = !isLockedOut,
                                onClick = { onDigitPress(digit) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Bottom Row: Biometric Button, 0, Backspace
                Row(
                    horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xl),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Biometric Action
                    if (isBiometricAvailable) {
                        IconButton(
                            onClick = {
                                if (!isLockedOut) {
                                    onRequestBiometricPrompt()
                                }
                            },
                            enabled = !isLockedOut,
                            modifier = Modifier
                                .weight(1f)
                                .size(64.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Fingerprint,
                                contentDescription = stringResource(R.string.lock_biometric_prompt),
                                modifier = Modifier.size(36.dp),
                                tint = if (!isLockedOut) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    // Digit 0
                    NumpadKey(
                        text = "0",
                        enabled = !isLockedOut,
                        onClick = { onDigitPress("0") },
                        modifier = Modifier.weight(1f)
                    )

                    // Backspace Action
                    IconButton(
                        onClick = ::onBackspacePress,
                        enabled = !isLockedOut && enteredPin.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .size(64.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Backspace,
                            contentDescription = stringResource(R.string.lock_backspace),
                            modifier = Modifier.size(28.dp),
                            tint = if (!isLockedOut && enteredPin.isNotEmpty()) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                }

                if (PinEntryPolicy.canConfirm(enteredPin.length) &&
                    !PinEntryPolicy.shouldAutoSubmit(enteredPin.length, expectedPinLength)
                ) {
                    LutealPrimaryButton(
                        text = stringResource(R.string.lock_submit_pin),
                        onClick = { submitPin(enteredPin) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLockedOut
                    )
                }
            }
        }
    }
}

@Composable
private fun NumpadKey(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
            .size(64.dp)
            .semantics { contentDescription = text }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }
    }
}
