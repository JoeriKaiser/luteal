# Spec 08: Biometric and PIN App Lock with Screen Masking

## Problem Statement

Menstrual and reproductive health records represent some of the most intimate, sensitive personal data stored on a mobile device. Luteal logs contain detailed records of cycle lengths, estimated fertile windows, period flows, intimacy events, contraception usage, basal body temperature, cervical fluid observations, dysmenorrhea pain levels, and personal health notes.

In daily life, mobile devices are frequently unlocked and handed to partners, children, friends, or colleagues to share photos, show a map, play media, or make emergency calls. In such situations, an unlocked device leaves all historical reproductive data exposed to accidental disclosure or snooping. Furthermore, in privacy-hostile environments—including relationships with intimate partner surveillance or jurisdictions where reproductive health logs carry legal vulnerability—physical access to an unlocked phone is a primary attack vector.

Additionally, the Android operating system defaults to capturing an unblurred snapshot bitmap of active applications when users switch apps or enter the Task Switcher (Recents Carousel). Without active protection, switching apps briefly flashes and caches the user's cycle wheel, fertile window estimates, or private symptoms in the system recents preview, exposing them to anyone looking over the user's shoulder.

## Solution

Implement an on-device, zero-network security layer providing:
1. **Biometric and Custom PIN App Lock:**
   - Primary authentication via AndroidX `BiometricPrompt` (Class 3 Strong Biometrics like Fingerprint, Class 2 Biometrics like secure Face Unlock).
   - Independent custom PIN fallback (4 to 8 digits) managed exclusively by Luteal and isolated from device lock credentials.
   - Reactive lock state managed by an `AppLockManager` that displays a full-screen `AppLockScreen` overlay blocking all underlying UI and database reads until successful authentication.
2. **Cryptographic PIN Protection via `KeystoreSecretStore`:**
   - Salted key derivation (PBKDF2-HMAC-SHA256 with 100,000 iterations) and hardware-backed AES-256-GCM encryption in `AndroidKeyStore`.
   - Constant-time verification to eliminate timing side-channel attacks.
   - Rate limiting and exponential backoff after consecutive failed PIN attempts to thwart automated brute-force attacks.
3. **Configurable Auto-Lock Timeouts:**
   - Configurable timeout thresholds: `IMMEDIATE` (locks upon app backgrounding or screen off), `ONE_MINUTE` (60-second grace period), and `FIVE_MINUTES` (300-second grace period).
   - Monotonic system clock (`SystemClock.elapsedRealtime()`) tracking to prevent bypass via system clock manipulation.
4. **OS Screen Masking (`FLAG_SECURE`):**
   - User-configurable toggle in Settings applying `WindowManager.LayoutParams.FLAG_SECURE` to `MainActivity.window`.
   - Prevents Android from capturing task switcher previews and blocks screenshots and screen recorders.
5. **Privacy & Offline Guarantee:**
   - 100% local operation with zero network dependencies, zero telemetry, and zero transmission of security state over Duo or cloud sync.

---

## User Stories

1. As a privacy-conscious user, I want to lock Luteal behind a biometric or PIN prompt, so that my intimate menstrual and reproductive records are protected even when my phone is unlocked.
2. As a user lending my phone to a family member or coworker, I want Luteal to lock immediately when backgrounded, so that others cannot view my cycle data if they navigate to the app.
3. As a user whose partner or household members know my general Android device unlock passcode, I want a distinct, dedicated PIN for Luteal, so that device access does not grant access to my cycle tracking.
4. As a user with biometric hardware (fingerprint or face recognition), I want Luteal to prompt me with the standard biometric prompt upon launch, so that I can unlock the app quickly and securely with a single touch.
5. As a user whose biometric authentication fails (e.g., wet fingers or poor lighting), I want to seamlessly fall back to entering my custom Luteal PIN without app crashes or state loss.
6. As a user whose device lacks biometric hardware or who has disabled biometrics, I want to authenticate solely with a 4 to 8 digit numeric PIN.
7. As a user frequently switching between Luteal and a messaging app or browser for a few moments, I want to choose a grace period (e.g., 1 minute or 5 minutes), so that I don't have to re-authenticate continuously during active tracking sessions.
8. As a user in a privacy-sensitive environment, I want the Android task switcher / recents carousel to show a blank or masked screen instead of my cycle wheel, so that passersby cannot glimpse my health status.
9. As a user entering an incorrect PIN, I want immediate visual and haptic error feedback (subtle shake animation and error message), so that I know the entry was invalid without the app leaking valid digit prefixes.
10. As a user facing multiple failed PIN attempts, I want the app to introduce progressive backoff delays (e.g., 30-second lockout after 5 failed attempts), so that unauthorized persons cannot brute-force my PIN.
11. As a user who wants to change my PIN, I want Luteal to require my current PIN or biometric authentication before permitting a new PIN to be configured.
12. As a user who wants to disable the app lock, I want Luteal to require full re-authentication, so that an unauthorized person holding my unlocked phone cannot disable the lock without credentials.
13. As a TalkBack / screen reader user, I want the custom numpad to have distinct, accessible 48dp touch targets and clear semantic announcements for each digit and the backspace key.
14. As a TalkBack user, I want PIN entry progress to announce the number of digits entered (e.g., "3 chiffres saisis sur 4") without reading out the actual digits aloud, preserving my privacy in shared rooms.
15. As a user restarting my phone, I want Luteal to initialize in a strictly locked state upon cold boot, ensuring that data is never briefly visible before the lock triggers.
16. As a Duo partner-sharing user, I want my app lock configuration and PIN to remain strictly local to my physical device, so that my partner's security settings do not overwrite or interfere with mine.
17. As an English-language user, I want all lock screen labels, biometric prompts, timeout options, and security settings translated accurately in `values-en` with 100% parity with French default copy.
18. As a low-vision user utilizing 200% font scaling, I want the lock screen layout, PIN dots, numpad buttons, and error messages to adapt without clipping or overlapping.
19. As an offline user, I want all biometric checks, PIN hashing, and Keystore crypto operations to function instantly with zero internet connectivity.

---

## Implementation Decisions

### 1. Security Architecture & State Flow

The app lock lifecycle is governed by an `AppLockManager` singleton coordinating with `UserPreferencesDataStore`, `KeystoreSecretStore`, and Android's `ProcessLifecycleOwner`.

```
                +----------------------------+
                |   ProcessLifecycleOwner    |
                |  (onStart / onStop Events) |
                +-------------+--------------+
                              |
                              v
                  +-----------------------+
                  |    AppLockManager     |
                  +-----------+-----------+
                              |
        +---------------------+---------------------+
        |                     |                     |
        v                     v                     v
+---------------+     +---------------+     +---------------+
| LockStateFlow |     | KeystoreStore |     | DataStorePref |
| (Locked/Open) |     |  (PIN Crypto) |     |   (Timeouts)  |
+-------+-------+     +---------------+     +---------------+
        |
        v
+-----------------------------------------------------------+
| MainActivity Compose Root (Conditional Barrier)           |
|  - If Locked: renders AppLockScreen (Numpad + Biometrics) |
|  - If Unlocked: renders LutealMainScaffold & NavHost       |
+-----------------------------------------------------------+
```

#### Lock State Machine
```kotlin
sealed interface AppLockState {
    data object Unlocked : AppLockState
    data object NotConfigured : AppLockState
    data class Locked(
        val isBiometricAvailable: Boolean,
        val remainingLockoutSeconds: Int = 0
    ) : AppLockState
}
```

- When `AppLockState` is `Locked`, the Compose root does not instantiate or render `LutealMainScaffold`, preventing any cycle calculations, Room queries, or UI elements from loading in the background.
- Upon successful biometric callback or PIN verification, `AppLockManager` emits `AppLockState.Unlocked`.

---

### 2. Android Keystore & Cryptographic PIN Storage

PINs are never stored in plaintext, reversible format, or simple unsalted SHA-256 hashes. Storage is handled via `KeystoreSecretStore`:

1. **Salt Generation:** 16-byte cryptographically secure random salt generated via `SecureRandom`.
2. **Key Derivation (PBKDF2):** `PBKDF2WithHmacSHA256` with 100,000 iterations producing a 256-bit derived key.
3. **Hardware-Backed AES-256-GCM:** The derived token and salt are sealed using `KeystoreSecretStore` with a dedicated alias (`luteal_app_lock_key`) backed by AndroidKeyStore TEE/StrongBox.
4. **Constant-Time Verification:** When the user enters a PIN, the candidate is derived with the stored salt and compared using `MessageDigest.isEqual()` to prevent timing side-channel analysis.
5. **Rate Limiting & Anti-Brute-Force:**
   - 1–4 failed attempts: Short haptic feedback + visual shake animation.
   - 5 failed attempts: 30-second lockout.
   - 10+ failed attempts: Exponential backoff (60s, 120s, 300s).
   - Lockout timestamps are persisted in `KeystoreSecretStore` so killing and restarting the app cannot reset the active lockout window.

---

### 3. Activity Lifecycle & Auto-Lock Timeout

Auto-lock timing is determined by observing Android's application lifecycle via `DefaultLifecycleObserver`:

- **`onStop()`:** When the app moves to the background or the screen turns off, `AppLockManager` records `lastBackgroundTimestamp = SystemClock.elapsedRealtime()`.
- **`onStart()`:** When the app returns to the foreground:
  - If `AutoLockTimeout.IMMEDIATE`: Transition immediately to `AppLockState.Locked`.
  - If `AutoLockTimeout.ONE_MINUTE`: Check `(elapsedRealtime() - lastBackgroundTimestamp) >= 60_000`. If exceeded, lock; otherwise remain unlocked.
  - If `AutoLockTimeout.FIVE_MINUTES`: Check `(elapsedRealtime() - lastBackgroundTimestamp) >= 300_000`. If exceeded, lock; otherwise remain unlocked.
- Monotonic `SystemClock.elapsedRealtime()` is unaffected by user changes to the system date/time, preventing time-tampering bypasses.

---

### 4. Screen Masking with `FLAG_SECURE`

To protect data in the Android Task Switcher (Recents Carousel) and prevent unprivileged screen recording:

1. In `MainActivity`, collect `isScreenMaskingEnabled` from `UserPreferencesDataStore`:
```kotlin
lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        userPreferencesDataStore.isScreenMaskingEnabled.collect { enabled ->
            if (enabled) {
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
```
2. When enabled, Android blanks the app preview in the recent apps carousel and rejects screenshots, protecting the user from shoulder-surfing and OS thumbnail caching.

---

### 5. Compose Lock Screen Overlay (`AppLockScreen.kt`)

The lock screen composable provides a distraction-free, accessible authentication interface:

- **PIN Dots Indicator:** A row of 4 to 8 animated circular dots displaying filled/unfilled state.
- **Accessible Numpad:** 3x4 grid of buttons (`1` through `9`, `Biometric Action`, `0`, `Backspace`). Each button has a minimum 48dp x 48dp touch target, high-contrast typography, and subtle tactile haptic feedback.
- **Biometric Integration:**
  - Automatically invokes AndroidX `BiometricPrompt` on initial screen load if biometric authentication is enabled and available.
  - The bottom-left numpad slot contains a biometric icon button to re-trigger the prompt if dismissed.
- **Accessibility & TalkBack:**
  - Numpad buttons provide explicit `contentDescription` (e.g., `"Chiffre 1"`, `"Effacer"`).
  - PIN indicator row is marked with `clearAndSetSemantics` to announce aggregate progress (`"2 chiffres saisis"`) rather than reading entered digit values aloud.

---

### 6. Settings Screen Integration (`SettingsScreen.kt`)

Add a dedicated **"Sécurité & Verrouillage"** section to the settings screen:

1. **Verrouillage de l'application (Switch):** Enables or disables the master app lock. Toggling off requires entering the current PIN or biometric auth.
2. **Déverrouillage biométrique (Switch):** Toggles fingerprint/face authentication (enabled only if device hardware supports biometrics).
3. **Modifier le code PIN (Button):** Opens a dialog to enter the old PIN, then set and confirm a new PIN.
4. **Délai de verrouillage automatique (Dropdown / Radio):**
   - *Immédiat* (défaut)
   - *1 minute*
   - *5 minutes*
5. **Masquer l'écran dans les applications récentes (Switch):** Toggles `FLAG_SECURE` screen masking.

---

### 7. Localization & Copy

All user-facing strings are defined in `res/values/strings.xml` (French default), `res/values-fr/strings.xml`, and `res/values-en/strings.xml`.

| Key | French (Default / `values-fr`) | English (`values-en`) |
|---|---|---|
| `settings_security_title` | Sécurité & Confidentialité | Security & Privacy |
| `settings_app_lock_title` | Verrouillage de l'application | App Lock |
| `settings_app_lock_summary` | Protéger l'accès à Luteal par empreinte ou code PIN | Protect access to Luteal with biometrics or PIN |
| `settings_biometric_title` | Déverrouillage biométrique | Biometric Unlock |
| `settings_biometric_summary` | Utiliser l'empreinte digitale ou la reconnaissance faciale | Use fingerprint or face recognition |
| `settings_change_pin` | Modifier le code PIN | Change PIN |
| `settings_timeout_title` | Verrouillage automatique | Auto-lock Timeout |
| `settings_timeout_immediate` | Immédiat | Immediately |
| `settings_timeout_1min` | Après 1 minute | After 1 minute |
| `settings_timeout_5min` | Après 5 minutes | After 5 minutes |
| `settings_screen_masking_title` | Masquage de l'écran | Screen Masking |
| `settings_screen_masking_summary` | Masque le contenu dans le sélecteur d'applications et bloque les captures d'écran | Hides content in the recent apps switcher and blocks screenshots |
| `lock_prompt_title` | Déverrouiller Luteal | Unlock Luteal |
| `lock_enter_pin` | Entrez votre code PIN | Enter your PIN |
| `lock_incorrect_pin` | Code PIN incorrect | Incorrect PIN |
| `lock_rate_limited` | Trop de tentatives. Réessayez dans %1$d s | Too many attempts. Try again in %1$ds |
| `lock_accessibility_progress` | %1$d chiffres saisis | %1$d digits entered |

---

## Testing Decisions

### 1. Unit Tests (`AppLockManagerTest.kt`)
- Verify immediate lock transitions when `AutoLockTimeout.IMMEDIATE` is set and app enters background.
- Verify 1-minute and 5-minute grace periods allow resume within window without locking, and transition to `Locked` once time threshold elapses.
- Verify cold boot starts in `Locked` state when app lock is enabled.
- Verify constant-time PIN comparison logic and rejection of invalid PINs.
- Verify rate-limiting lockout increments after consecutive invalid attempts and persists across simulated restarts.

### 2. Cryptographic Tests (`PinCryptoTest.kt`)
- Verify PBKDF2 derivation with unique random salts produces distinct outputs for identical PINs.
- Verify salt and ciphertext sealing and unsealing with `KeystoreSecretStore`.
- Verify clearing credentials on local wipe properly purges keys and resets state.

### 3. Compose UI & Accessibility Tests (`AppLockScreenTest.kt`)
- Test typing digits on the custom numpad updates the indicator dots.
- Test backspace button clears the last entered digit.
- Test entering the correct PIN triggers `onUnlockSuccess`.
- Test entering an invalid PIN triggers error animation and clears input.
- Test TalkBack semantic node hierarchy: verify numpad buttons have click actions and digit labels, and PIN indicators do not leak digit text.

### 4. Integration Tests (`ScreenMaskingTest.kt`)
- Test `MainActivity` applies `FLAG_SECURE` when `isScreenMaskingEnabled` is `true` and removes it when `false`.

---

## Out of Scope

- **Cloud / Remote PIN Reset:** By design, Luteal is zero-knowledge and offline-first. If a user forgets their PIN and biometrics are unavailable, data can only be recovered via local wipe and re-importing an offline backup.
- **Duress / Decoy PIN:** Decoy vaults with fake cycle data are out of scope for this initial specification and reserved for future security extensions.
- **Alphanumeric Passwords:** Numeric PINs (4 to 8 digits) combined with biometrics provide optimal mobile ergonomics while maintaining robust security against brute-force attacks.
- **Third-Party Authenticator Plugins:** No dependencies on proprietary third-party lock SDKs.

---

## Further Notes

### Threat Model Analysis
- **Shoulder Surfing:** Mitigated by masking PIN dots, omitting numeric character echoes, and applying `FLAG_SECURE` to the task switcher.
- **Device Lending / Snatching:** Mitigated by `IMMEDIATE` auto-lock on app backgrounding and distinct Luteal PIN.
- **Automated Brute-Force:** Mitigated by PBKDF2 computational cost, Keystore hardware backing, and exponential rate-limiting delays.
- **Forensic Memory Dumps:** PIN characters held in transient `CharArray` and zeroed immediately after derivation; never stored in persistent plain strings.

### Authoritative References
- **NIST SP 800-63B:** *Digital Identity Guidelines: Authentication and Lifecycle Management* (Rate limiting, salt generation, PBKDF2 iteration recommendations).
- **OWASP Mobile Application Security Verification Standard (MASVS):** MASVS-AUTH (Local Authentication) & MASVS-STORAGE (Hardware-backed Keystore storage).
- **Android Open Source Project (AOSP):** *AndroidX Biometric API Guidelines* and *WindowManager FLAG_SECURE Specification*.
- **ACOG & EFF Privacy Guides:** *Protecting Reproductive Health Data in the Digital Age*.
