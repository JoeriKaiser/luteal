# Spec 11: Privacy-Safe Local Notifications and Health Reminders

## Problem Statement

Regularity and timeliness of logging are essential for cycle tracking, yet users face distinct friction points:
1. **Log Forgetting & Gaps in Data:** Users frequently forget to log daily somatic observations (temperature, symptoms, pain, cervical mucus, mood) in the evening, leading to fragmented history and reduced insight into recurring phase patterns.
2. **Unanticipated Menstrual Bleeding:** Users are often caught off guard by the onset of menstruation when distracted or busy, creating stress that could be mitigated by a discreet advance notice.
3. **Late Cycle Uncertainty:** When a menstrual cycle extends significantly beyond its typical statistical window (due to stress, travel, illness, SOPK/PCOS, perimenopause, or pregnancy), users may wonder whether the app has ceased tracking or fail to record ongoing observations during extended phases.
4. **Severe Privacy Risks of Cloud Push (FCM/APNs):** Commercial menstrual trackers rely on third-party cloud push services such as Firebase Cloud Messaging (FCM) or Apple Push Notification Service (APNs). These architectures transmit unique hardware push tokens, app install identifiers, and notification triggers through Google/Apple infrastructure, exposing intimate reproductive timing metadata to corporate servers and ad brokers.
5. **F-Droid Build Restrictions:** Luteal is distributed via F-Droid as a 100% free and open-source software (FOSS) application. F-Droid inclusion strictly forbids proprietary Google Play Services and closed-source Firebase client SDKs (`play-services-*`, `firebase-messaging`).
6. **Lock Screen Snooping & Stigmatization:** Mobile devices are routinely left on tables or viewed by roommates, colleagues, family members, or abusive partners. Notifications that display overt clinical or reproductive phrasing (e.g. *"Your period starts tomorrow"* or *"Log your cramps and bleeding"*) present critical privacy and safety hazards.

Luteal requires an entirely on-device, zero-network notification subsystem that delivers timely reminders while guaranteeing absolute user privacy, zero third-party push dependencies, and robust lock screen concealment.

---

## Solution

Implement a 100% local notification and alarm scheduling architecture leveraging Android's `AlarmManager`, `BroadcastReceiver`, and `WorkManager` APIs.

### 1. Three Privacy-Conscious Notification Channels
Luteal establishes three distinct Android `NotificationChannel` groups with configurable preferences:

1. **Daily Observation Prompt (`channel_daily_checkin`):**
   - A daily reminder delivered at a user-selected time (e.g., 21:00 default).
   - Intelligently evaluates whether a `DailyEntry` has already been recorded for the current calendar day; if already logged, the notification is silently suppressed for that day.
2. **Period Window Reminder (`channel_period_window`):**
   - Scheduled 1 to 3 days (user configurable, default 2 days) prior to the `earliestDate` of the statistical window computed by `CycleEstimateCalculator`.
   - Never claims deterministic certainty ("Your period will arrive on Thursday"), framing the alert around the statistical arrival window ("Luteal — Fenêtre estimée à l'approche").
   - Automatically disabled or skipped if the user has fewer than two cycles recorded (`CycleEstimateResult.NeedsMoreHistory`).
3. **Late Cycle Check-in (`channel_late_cycle`):**
   - A discreet check-in triggered when the current cycle length exceeds the `latestDate` of the estimated window by a grace threshold (default: 1 day) without a recorded cycle start.
   - Provides a neutral, non-judgmental prompt to log observations or check current tracking status.

### 2. Privacy Safeguards & Concealed Mode
- **Concealed Text by Default:** All notification copy defaults to generic, non-medical language (e.g., *"Luteal — Rappel"* or *"Luteal — Suivi quotidien"*), revealing zero reproductive or clinical details.
- **Custom Copy Configuration:** Users can define their own notification title and body strings (e.g., *"Prendre un moment pour soi"* or *"Check-in"*).
- **Lock Screen Redaction:** Notifications set `NotificationCompat.VISIBILITY_PRIVATE` and attach a minimal public version via `setPublicVersion()`, ensuring lock screens show only generic text even if descriptive mode is selected for unlocked displays.
- **Zero Cloud Metadata:** Zero push tokens, zero network requests, zero background analytics. All scheduling occurs strictly inside the local SQLite database and Android system alarm registry.

### 3. Android System Resilience & Lifecycle Management
- **Android 13+ (`TIRAMISU` / API 33+) Runtime Permission:** In-context runtime request for `POST_NOTIFICATIONS` with an educational pre-permission dialog highlighting the 100% offline, zero-tracking guarantee.
- **Exact & Inexact Alarm Scheduling:** Standard daily reminders leverage battery-efficient inexact window alarms (`setWindow()` / `setInexactRepeating()`), with an optional exact alarm toggle (`SCHEDULE_EXACT_ALARM`) for users requiring minute-precise scheduling.
- **System Event Recovery:** `NotificationSystemReceiver` listens to `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`, and `ACTION_DATE_CHANGED` to recalculate and restore all alarm triggers across reboots and travel.

---

## User Stories

1. As a cycle tracking user, I want to set a daily evening reminder at a specific time (e.g., 21:30), so that I consistently log my symptoms and observations before going to sleep.
2. As a user who logs throughout the day, I want the daily reminder to be automatically suppressed if I have already recorded an entry for today, so that I am not bothered by redundant alerts.
3. As a cycle tracking user, I want to receive an advance notification 1, 2, or 3 days before my estimated period window begins, so that I can prepare necessary supplies and plan my week.
4. As a user with irregular cycles, I want period window reminders to reflect the earliest statistical arrival date rather than a fixed calendar day, so that the reminder honestly accounts for my cycle variability.
5. As a user whose cycle is running longer than usual, I want a discreet check-in notification a few days after the latest estimated date, so that I am prompted to record observations or confirm whether a new cycle has started.
6. As a privacy-conscious user, I want all notification text to be discreet and non-medical by default, so that anyone glancing at my screen cannot deduce that I am using a menstrual tracking application.
7. As a user with specific privacy preferences, I want to write custom notification titles and message bodies, so that reminders appear as arbitrary personal notes on my device.
8. As a user who leaves their phone on public desks, I want lock screen notifications to completely conceal message contents, so that bystanders cannot read notification details even if unlocked notifications are descriptive.
9. As an F-Droid user, I want all notifications to function with 100% reliability on devices without Google Play Services or microG, so that my tracking remains completely independent of proprietary software.
10. As an Android 13+ user, I want the app to explain why it needs notification permissions before showing the system permission dialog, so that I understand that notifications are generated purely on-device.
11. As a user who revokes notification permissions in Android settings, I want the app to update its settings UI gracefully without crashing or attempting invalid alarm dispatches.
12. As a user who travels across timezones or changes system clocks, I want all scheduled alarms to automatically adjust to the new local time without skipping days or firing at the wrong hour.
13. As a user who restarts their phone frequently, I want all pending reminders to be automatically restored on system boot, so that alarms continue working uninterrupted.
14. As a user who deletes or edits past cycle starts, I want period window and late cycle alarms to immediately recalculate based on updated estimates, so that alerts always reflect my current data.
15. As a Duo primary tracker, I want partner reminders to be strictly confined to the partner's device based on cached decrypted projections, so that no notifications or triggers are ever broadcast over the network.
16. As a TalkBack screen reader user, I want the reminder settings and time pickers to have accessible labels, announcements, and 48dp touch targets, so that I can configure my reminders independently.
17. As a French-speaking user, I want all notification channels, settings labels, and default notification templates to be natively phrased in French with complete English parity.

---

## Implementation Decisions

### 1. Architecture & Component Decomposition

The notification subsystem resides within `fr.luteal.core.notification` and `fr.luteal.app.notification`:

```
fr.luteal.
├── core.
│   ├── model.
│   │   ├── NotificationPreferences.kt       # Immutable domain preferences
│   │   └── NotificationType.kt              # Sealed interface for reminder types
│   ├── notification.
│   │   ├── NotificationScheduler.kt         # Interface for scheduling/canceling alarms
│   │   ├── NotificationSchedulerImpl.kt     # AlarmManager calculation & dispatch
│   │   ├── NotificationChannelManager.kt    # Android NotificationChannel registration
│   │   └── NotificationContentResolver.kt   # Localized & concealed copy generation
│   └── data.datastore.
│       └── UserPreferencesDataStore.kt      # DataStore keys for notification config
└── app.
    ├── notification.
    │   ├── NotificationAlarmReceiver.kt     # BroadcastReceiver triggered by AlarmManager
    │   ├── NotificationSystemReceiver.kt    # BroadcastReceiver for boot/time changes
    │   └── NotificationMaintenanceWorker.kt # WorkManager backup reconciler
    └── navigation.
        ├── SettingsViewModel.kt             # UI state flow & preference mutations
        └── component.
            └── NotificationSettingsCard.kt  # Compose settings UI & time picker
```

### 2. Domain & Preferences Models

```kotlin
package fr.luteal.core.model

import java.time.LocalTime

enum class NotificationVisibility {
    CONCEALED,       // "Luteal — Rappel"
    DESCRIPTIVE,     // "Luteal — Suivi quotidien" / "Fenêtre estimée à l'approche"
    CUSTOM           // User-defined title and text
}

data class NotificationPreferences(
    val dailyPromptEnabled: Boolean = false,
    val dailyPromptTime: LocalTime = LocalTime.of(21, 0),
    val periodWindowReminderEnabled: Boolean = false,
    val periodWindowLeadDays: Int = 2, // 1, 2, or 3 days before earliestDate
    val lateCyclePromptEnabled: Boolean = false,
    val lateCycleGraceDays: Int = 1, // days after latestDate
    val visibilityMode: NotificationVisibility = NotificationVisibility.CONCEALED,
    val customDailyPromptTitle: String = "",
    val customDailyPromptBody: String = "",
    val exactAlarmsRequested: Boolean = false
) {
    init {
        require(periodWindowLeadDays in 1..3) { "Period window lead days must be between 1 and 3" }
        require(lateCycleGraceDays in 1..7) { "Late cycle grace days must be between 1 and 7" }
    }
}

sealed interface NotificationType {
    val id: Int
    val channelId: String

    data class DailyCheckIn(val targetDate: java.time.LocalDate) : NotificationType {
        override val id: Int = 1001
        override val channelId: String = CHANNEL_DAILY_CHECKIN
    }

    data class PeriodWindowApproaching(val earliestEstimatedDate: java.time.LocalDate) : NotificationType {
        override val id: Int = 1002
        override val channelId: String = CHANNEL_PERIOD_WINDOW
    }

    data class LateCycleCheckIn(val daysPastLatest: Int) : NotificationType {
        override val id: Int = 1003
        override val channelId: String = CHANNEL_LATE_CYCLE
    }

    companion object {
        const val CHANNEL_DAILY_CHECKIN = "channel_daily_checkin"
        const val CHANNEL_PERIOD_WINDOW = "channel_period_window"
        const val CHANNEL_LATE_CYCLE = "channel_late_cycle"
    }
}
```

### 3. Alarm Calculation & Scheduling Logic (`NotificationSchedulerImpl`)

The `NotificationScheduler` coordinates alarm timing:
1. **Daily Check-in Trigger Calculation:**
   - Computes target `ZonedDateTime` using `prefs.dailyPromptTime` and `ZoneId.systemDefault()`.
   - If the calculated time for today is in the past, schedules for tomorrow at the configured time.
   - Before scheduling, queries `DailyEntryRepository.getEntry(today)`: if an entry already exists, skips today's trigger and targets tomorrow.
2. **Period Window Trigger Calculation:**
   - Queries `CycleRepository.getLatestCycle()` and calculates `CycleEstimate` via `CycleEstimateCalculator.calculate()`.
   - If `CycleEstimateResult.Available(estimate)`: computes `triggerDate = estimate.earliestDate.minusDays(prefs.periodWindowLeadDays.toLong())`.
   - Schedules alarm at 09:00 local time on `triggerDate`. If `triggerDate` is today or in the past, no past alarm is scheduled.
3. **Late Cycle Trigger Calculation:**
   - When a cycle is active and current date exceeds `estimate.latestDate + prefs.lateCycleGraceDays` without a new cycle start, schedules a one-shot reminder at 10:00 local time.
4. **AlarmManager Intent Dispatch:**
   - Uses `PendingIntent.getBroadcast()` with `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`.
   - Dispatches via `AlarmManagerCompat.setAndAllowWhileIdle()` (or `setWindow()` for inexact power-efficient windows).

```kotlin
class NotificationSchedulerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmManager: AlarmManager,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val cycleRepository: CycleRepository,
    private val dailyEntryRepository: DailyEntryRepository
) : NotificationScheduler {

    override suspend fun reconcileAllSchedules() {
        val prefs = preferencesDataStore.userPreferences.first().notificationPreferences
        cancelAll()

        if (prefs.dailyPromptEnabled) {
            scheduleDailyPrompt(prefs)
        }
        if (prefs.periodWindowReminderEnabled) {
            schedulePeriodWindowReminder(prefs)
        }
        if (prefs.lateCyclePromptEnabled) {
            scheduleLateCyclePrompt(prefs)
        }
    }

    private suspend fun scheduleDailyPrompt(prefs: NotificationPreferences) {
        val now = ZonedDateTime.now()
        val today = now.toLocalDate()
        val hasEntryToday = dailyEntryRepository.getEntryForDate(today) != null

        var targetDateTime = today.atTime(prefs.dailyPromptTime).atZone(ZoneId.systemDefault())
        if (hasEntryToday || targetDateTime.isBefore(now)) {
            targetDateTime = targetDateTime.plusDays(1)
        }

        val intent = Intent(context, NotificationAlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_DAILY_PROMPT
            putExtra(EXTRA_NOTIFICATION_TYPE, TYPE_DAILY_CHECKIN)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_DAILY_PROMPT,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            targetDateTime.toInstant().toEpochMilli(),
            pendingIntent
        )
    }
}
```

### 4. BroadcastReceivers & System Lifecycle

1. **`NotificationAlarmReceiver` (`exported="false"`):**
   - Receives the alarm trigger from `AlarmManager`.
   - Resolves localized copy via `NotificationContentResolver`.
   - Constructs `NotificationCompat.Builder` with appropriate channel, icons, priority, and `setPublicVersion()`.
   - Posts notification via `NotificationManagerCompat.notify()`.
   - Immediately invokes `NotificationScheduler.reconcileAllSchedules()` to program the subsequent day's alarm.
2. **`NotificationSystemReceiver` (`exported="true"` in Manifest for system broadcasts):**
   - Registered for:
     - `android.intent.action.BOOT_COMPLETED`
     - `android.intent.action.MY_PACKAGE_REPLACED`
     - `android.intent.action.TIME_SET`
     - `android.intent.action.TIMEZONE_CHANGED`
     - `android.intent.action.DATE_CHANGED`
   - Spawns a coroutine via `goAsync()` to call `NotificationScheduler.reconcileAllSchedules()`.

### 5. Notification Channel Management (`NotificationChannelManager`)

On Android 8.0+ (API 26+), registers three channels on app startup:

```kotlin
object NotificationChannelManager {
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val dailyChannel = NotificationChannel(
            NotificationType.CHANNEL_DAILY_CHECKIN,
            context.getString(R.string.notification_channel_daily_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_daily_desc)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }

        val windowChannel = NotificationChannel(
            NotificationType.CHANNEL_PERIOD_WINDOW,
            context.getString(R.string.notification_channel_window_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_window_desc)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }

        val lateChannel = NotificationChannel(
            NotificationType.CHANNEL_LATE_CYCLE,
            context.getString(R.string.notification_channel_late_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_late_desc)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }

        notificationManager.createNotificationChannels(listOf(dailyChannel, windowChannel, lateChannel))
    }
}
```

### 6. DataStore Persistence

Extend `UserPreferencesDataStore` with notification preference keys:
- `NOTIFICATIONS_DAILY_ENABLED = booleanPreferencesKey("notif_daily_enabled")`
- `NOTIFICATIONS_DAILY_TIME = stringPreferencesKey("notif_daily_time")` (ISO-8601 `HH:mm`)
- `NOTIFICATIONS_WINDOW_ENABLED = booleanPreferencesKey("notif_window_enabled")`
- `NOTIFICATIONS_WINDOW_LEAD_DAYS = intPreferencesKey("notif_window_lead_days")`
- `NOTIFICATIONS_LATE_ENABLED = booleanPreferencesKey("notif_late_enabled")`
- `NOTIFICATIONS_LATE_GRACE_DAYS = intPreferencesKey("notif_late_grace_days")`
- `NOTIFICATIONS_VISIBILITY_MODE = stringPreferencesKey("notif_visibility_mode")`
- `NOTIFICATIONS_CUSTOM_TITLE = stringPreferencesKey("notif_custom_title")`
- `NOTIFICATIONS_CUSTOM_BODY = stringPreferencesKey("notif_custom_body")`

### 7. UI & Compose Integration (`SettingsScreen`)

Add a dedicated `LutealCard` section in `SettingsScreen.kt`:
- **Permission Rationale Banner:** Displayed if `Build.VERSION.SDK_INT >= 33` and notification permission is not granted. Shows a non-intrusive card explaining offline privacy with an "Activer les rappels" button.
- **Daily Prompt Toggle & Time Picker:**
  - `LutealToggleRow` to enable/disable daily reminders.
  - Sub-row displaying formatted time (e.g., "21:00") with a button opening a Material 3 `TimePickerDialog`.
- **Period Window Reminder Controls:**
  - `LutealToggleRow` for period window reminders.
  - Radio/segmented selection for lead days (1 jour, 2 jours, 3 jours avant la fenêtre).
- **Late Cycle Check-in Controls:**
  - `LutealToggleRow` for late cycle check-ins.
- **Privacy & Concealment Mode Picker:**
  - Selection between *Discret (Recommandé)*, *Détaillé*, and *Personnalisé*.
  - Text fields for custom title and body when *Personnalisé* is active.
  - Visual preview box demonstrating how the notification appears in the system tray and on lock screen.

### 8. Localization & Copy Strings

#### French Default (`res/values-fr/strings.xml` / `res/values/strings.xml`)
```xml
<!-- Notification Channels -->
<string name="notification_channel_daily_name">Suivi quotidien</string>
<string name="notification_channel_daily_desc">Rappels pour enregistrer vos observations quotidiennes.</string>
<string name="notification_channel_window_name">Fenêtre de cycle estimée</string>
<string name="notification_channel_window_desc">Rappels à l\'approche de la fenêtre de règles estimée.</string>
<string name="notification_channel_late_name">Suivi de cycle prolongé</string>
<string name="notification_channel_late_desc">Rappels discrets lorsque le cycle dépasse la fenêtre estimée.</string>

<!-- Notification Content (Concealed - Default) -->
<string name="notification_concealed_title">Luteal</string>
<string name="notification_concealed_body">Rappel de suivi quotidien</string>
<string name="notification_lockscreen_public_body">1 nouveau rappel</string>

<!-- Notification Content (Descriptive) -->
<string name="notification_daily_descriptive_title">Luteal — Suivi du jour</string>
<string name="notification_daily_descriptive_body">Prenez un instant pour noter vos observations du jour.</string>
<string name="notification_window_descriptive_title">Luteal — Fenêtre estimée</string>
<string name="notification_window_descriptive_body">La fenêtre de règles estimée approche dans %1$d jours.</string>
<string name="notification_late_descriptive_title">Luteal — Suivi de cycle</string>
<string name="notification_late_descriptive_body">Votre cycle se prolonge au-delà de l\'estimation habituelle. Pensez à noter vos observations.</string>

<!-- Settings UI -->
<string name="settings_notifications_title">Rappels et notifications locales</string>
<string name="settings_notifications_desc">Toutes les notifications sont programmées exclusivement sur votre appareil. Aucune donnée ne transite par des serveurs externes.</string>
<string name="settings_notifications_permission_required">Pour recevoir des rappels, veuillez autoriser les notifications dans les paramètres système.</string>
<string name="settings_notifications_permission_cta">Autoriser les rappels</string>
<string name="settings_notifications_daily_prompt">Rappel quotidien d\'observation</string>
<string name="settings_notifications_daily_time">Heure du rappel</string>
<string name="settings_notifications_window_prompt">Rappel avant la fenêtre de règles</string>
<string name="settings_notifications_window_lead_days">Délai d\'anticipation</string>
<string name="settings_notifications_window_lead_1_day">1 jour avant</string>
<string name="settings_notifications_window_lead_2_days">2 jours avant</string>
<string name="settings_notifications_window_lead_3_days">3 jours avant</string>
<string name="settings_notifications_late_prompt">Vérification si le cycle se prolonge</string>
<string name="settings_notifications_visibility_header">Confidentialité de l\'affichage</string>
<string name="settings_notifications_visibility_concealed">Discret (recommandé)</string>
<string name="settings_notifications_visibility_concealed_desc">Masque tout terme lié au cycle sur l\'écran et les notifications.</string>
<string name="settings_notifications_visibility_descriptive">Détaillé</string>
<string name="settings_notifications_visibility_custom">Personnalisé</string>
<string name="settings_notifications_custom_title_label">Titre personnalisé</string>
<string name="settings_notifications_custom_body_label">Message personnalisé</string>
```

#### English Parity (`res/values-en/strings.xml`)
```xml
<!-- Notification Channels -->
<string name="notification_channel_daily_name">Daily Check-in</string>
<string name="notification_channel_daily_desc">Reminders to record your daily observations.</string>
<string name="notification_channel_window_name">Estimated Cycle Window</string>
<string name="notification_channel_window_desc">Reminders ahead of your estimated period window.</string>
<string name="notification_channel_late_name">Extended Cycle Check-in</string>
<string name="notification_channel_late_desc">Discreet reminders when a cycle extends past the estimated window.</string>

<!-- Notification Content (Concealed - Default) -->
<string name="notification_concealed_title">Luteal</string>
<string name="notification_concealed_body">Daily reminder</string>
<string name="notification_lockscreen_public_body">1 new reminder</string>

<!-- Notification Content (Descriptive) -->
<string name="notification_daily_descriptive_title">Luteal — Daily Check-in</string>
<string name="notification_daily_descriptive_body">Take a moment to record today\'s observations.</string>
<string name="notification_window_descriptive_title">Luteal — Estimated Window</string>
<string name="notification_window_descriptive_body">Your estimated period window begins in %1$d days.</string>
<string name="notification_late_descriptive_title">Luteal — Cycle Check-in</string>
<string name="notification_late_descriptive_body">Your cycle is extending past the typical estimate. Consider recording current observations.</string>

<!-- Settings UI -->
<string name="settings_notifications_title">Local Reminders &amp; Notifications</string>
<string name="settings_notifications_desc">All reminders are scheduled exclusively on your device. Zero data is sent to external servers.</string>
<string name="settings_notifications_permission_required">To receive reminders, please grant notification permissions in system settings.</string>
<string name="settings_notifications_permission_cta">Allow Reminders</string>
<string name="settings_notifications_daily_prompt">Daily observation reminder</string>
<string name="settings_notifications_daily_time">Reminder time</string>
<string name="settings_notifications_window_prompt">Period window reminder</string>
<string name="settings_notifications_window_lead_days">Advance notice</string>
<string name="settings_notifications_window_lead_1_day">1 day before</string>
<string name="settings_notifications_window_lead_2_days">2 days before</string>
<string name="settings_notifications_window_lead_3_days">3 days before</string>
<string name="settings_notifications_late_prompt">Extended cycle check-in</string>
<string name="settings_notifications_visibility_header">Notification Privacy</string>
<string name="settings_notifications_visibility_concealed">Discreet (Recommended)</string>
<string name="settings_notifications_visibility_concealed_desc">Hides any cycle-related terms in notifications and on lock screen.</string>
<string name="settings_notifications_visibility_descriptive">Descriptive</string>
<string name="settings_notifications_visibility_custom">Custom</string>
<string name="settings_notifications_custom_title_label">Custom Title</string>
<string name="settings_notifications_custom_body_label">Custom Message</string>
```

### 9. Accessibility & WCAG 2.2 AA

- **Touch Targets:** All toggles, time selector buttons, radio buttons, and text fields strictly satisfy 48×48dp touch target guidelines.
- **Screen Reader Semantics:**
  - `LutealToggleRow` includes `Modifier.semantics { role = Role.Switch }` with dynamic state descriptions ("Activé", "Désactivé").
  - Time picker trigger button provides explicit `contentDescription` indicating the currently selected time (e.g. *"Heure de rappel quotidien configurée à 21 heures 00, toucher pour modifier"*).
- **Notification Accessibility:**
  - Notifications provide clear titles, bodies, and `setTicker()` strings for accessibility services.
  - High-contrast monochromatic small icon (`ic_notification_luteal`) adhering to Android status bar contrast ratios.

---

## Testing Decisions

### 1. Unit Tests (`NotificationSchedulerTest`, `NotificationContentResolverTest`)

- **Daily Check-in Trigger Calculation:**
  - *Case 1:* Target time today is in the future and no entry exists $\rightarrow$ schedules for today at target time.
  - *Case 2:* Target time today is in the past $\rightarrow$ schedules for tomorrow at target time.
  - *Case 3:* Daily entry already recorded for today $\rightarrow$ schedules for tomorrow at target time.
- **Period Window Reminder Calculation:**
  - *Case 1:* `CycleEstimateResult.Available` with `earliestDate = 2026-08-20` and `leadDays = 2` $\rightarrow$ alarm scheduled for `2026-08-18` at 09:00.
  - *Case 2:* `CycleEstimateResult.NeedsMoreHistory` $\rightarrow$ no window alarm scheduled.
  - *Case 3:* Calculated trigger date is in the past $\rightarrow$ alarm skipped.
- **Late Cycle Check-in Calculation:**
  - *Case 1:* Active cycle length exceeds `latestDate + graceDays` $\rightarrow$ one-shot check-in scheduled.
  - *Case 2:* New cycle start logged $\rightarrow$ late cycle alarm immediately canceled.
- **Privacy & Copy Resolution:**
  - *Concealed Mode:* Asserts that resolved titles and bodies contain zero prohibited medical tokens (`menstruation`, `period`, `règles`, `cramps`, `ovulation`, `bleeding`, `sang`).
  - *Custom Mode:* Asserts that user-provided text is rendered verbatim with trimming and sanitization of excessive whitespace or control characters.
  - *Lockscreen Public Version:* Asserts that `setPublicVersion` always contains the generic string resource regardless of active visibility mode.

### 2. Timezone & System Event Tests (`NotificationSystemReceiverTest`)

- **Daylight Saving Time (DST) Transition:**
  - Validates that a 21:00 reminder scheduled across a DST shift fires at 21:00 wall-clock time in the new offset, avoiding 1-hour drift.
- **Timezone Change (`ACTION_TIMEZONE_CHANGED`):**
  - Simulates timezone switch (e.g., `Europe/Paris` to `America/New_York`) and verifies `reconcileAllSchedules()` converts all `LocalTime` triggers to the new system default zone.
- **Device Boot (`ACTION_BOOT_COMPLETED`):**
  - Validates that `NotificationSystemReceiver` properly invokes `NotificationScheduler` within `goAsync()`.

### 3. Integration & Compose UI Tests (`SettingsScreenTest`)

- **Permission State Changes:**
  - Tests UI transition when `POST_NOTIFICATIONS` permission changes from `DENIED` to `GRANTED`.
- **Time Picker Interaction:**
  - Validates opening `TimePickerDialog`, selecting a new hour/minute, confirming, and observing the updated state in DataStore.
- **Custom Copy Validation:**
  - Tests inputting custom strings and verifies real-time updates to preview components and DataStore persistence.

---

## Out of Scope

1. **Cloud Push Infrastructure (FCM, APNs):** No integration with cloud push services; all notifications are strictly 100% on-device.
2. **Third-Party Push Relays (UnifiedPush, WebPush, Matrix):** Excluded to preserve the zero-network offline guarantee and prevent unnecessary battery drain.
3. **Audible Alarm Clock Rings:** The app uses gentle, standard system notification tones and vibration patterns; it does not set high-priority alarm clock audio streams (`STREAM_ALARM`).
4. **Fertility, Conception, or Ovulation Prediction Alarms:** Strict adherence to Luteal's "Quiet Instrument" philosophy; no speculative ovulation alerts or fertility predictions.
5. **Wear OS Companion Push:** Dedicated smartwatch companion apps are out of scope; standard Android system-level notification mirroring handles connected wearables automatically.

---

## Further Notes

### 1. Research Register Citations

- **CNIL (Commission Nationale de l'Informatique et des Libertés) — Health Data Protection:**
  - Health data is subject to strict protection under GDPR Article 9. Transmitting notification payloads or device push tokens over external networks creates severe compliance and privacy risks. Local-only scheduling complies directly with CNIL data minimization principles (`docs/research/SOURCE_REGISTER.md`, row 34–35).
- **Bull et al. (2019) & Li et al. (2023) — Cycle Length Variability:**
  - Real-world cycle length within individuals varies by a standard deviation of 2.6 to 5.4 days. Fixed-day prediction alerts ("Your period is tomorrow") are scientifically unfounded and induce alert fatigue. Notifying ahead of the *earliest statistical window date* (`earliestDate`) aligns reminder timing with physiological evidence (`docs/research/SOURCE_REGISTER.md`, row 44–45).
- **Habit Formation in Mobile Health (Stawarz et al., 2015; Lally et al., 2010):**
  - Consistent temporal anchoring (e.g., prompting at the same self-chosen evening hour) significantly enhances logging adherence compared to unpredictable sporadic prompts.
- **Intimate Partner Privacy & Device Snooping (Matthews et al., 2017; Freed et al., 2018):**
  - Intimate tracking applications represent common vectors for unwanted surveillance by partners or family. Concealed notification copy and strict lock screen redaction (`VISIBILITY_PRIVATE` / `setPublicVersion`) prevent accidental exposure.

### 2. Security & Privacy Safeguards

- **Zero IPC Leakage:**
  - `NotificationAlarmReceiver` is declared with `android:exported="false"`.
  - All `PendingIntent` instances specify `PendingIntent.FLAG_IMMUTABLE`.
- **Ephemeral Notification Lifecycle:**
  - Notifications are dismissed automatically upon user tap (`setAutoCancel(true)`), routing directly to the appropriate app destination (`DailyEntrySheet` or `HomeScreen`).
- **No Notification Payload Logging:**
  - No notification history, delivery timestamps, or text payloads are logged to disk or diagnostics.
