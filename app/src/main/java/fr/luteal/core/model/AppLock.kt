package fr.luteal.core.model

enum class AutoLockTimeout(val durationSeconds: Long) {
    IMMEDIATE(0L),
    ONE_MINUTE(60L),
    FIVE_MINUTES(300L);

    companion object {
        fun fromName(name: String?): AutoLockTimeout {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: IMMEDIATE
        }
    }
}

sealed interface AppLockState {
    data object Resolving : AppLockState
    data object NotConfigured : AppLockState
    data object Unlocked : AppLockState
    data class Locked(
        val isBiometricAvailable: Boolean = false,
        val remainingLockoutSeconds: Int = 0
    ) : AppLockState
}

object PinEntryPolicy {
    fun shouldAutoSubmit(enteredLength: Int, expectedLength: Int?): Boolean =
        expectedLength != null && enteredLength == expectedLength.coerceIn(4, 8)

    fun canConfirm(enteredLength: Int): Boolean = enteredLength in 4..8
}

sealed interface PinVerificationResult {
    data object Success : PinVerificationResult
    data class Incorrect(val remainingAttemptsBeforeLockout: Int) : PinVerificationResult
    data class LockedOut(val remainingSeconds: Int) : PinVerificationResult
}
