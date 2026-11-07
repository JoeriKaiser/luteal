package fr.luteal.app.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.luteal.core.data.datastore.UserPreferencesDataStore
import fr.luteal.core.data.repository.CycleRepository
import fr.luteal.core.data.repository.DailyEntryRepository
import fr.luteal.core.model.AgeBand
import fr.luteal.core.model.CycleEstimateCalculator
import fr.luteal.core.model.CycleEstimateResult
import fr.luteal.core.model.NotificationType
import fr.luteal.core.model.PillRegimenCalculator
import fr.luteal.core.model.PillRegimenConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val cycleRepository: CycleRepository,
    private val dailyEntryRepository: DailyEntryRepository
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        const val EXTRA_NOTIFICATION_TYPE = "extra_notification_type"
        const val REQUEST_CODE_DAILY = 1001
        const val REQUEST_CODE_WINDOW = 1002
        const val REQUEST_CODE_LATE = 1003
        const val REQUEST_CODE_PILL = 1004
        const val REQUEST_CODE_PILL_SNOOZE = 1005

        fun calculateDailyCheckInTarget(targetTime: LocalTime, now: LocalDateTime, hasEntryToday: Boolean): LocalDateTime {
            val today = now.toLocalDate()
            return if (hasEntryToday || now.toLocalTime().isAfter(targetTime)) {
                today.plusDays(1).atTime(targetTime)
            } else {
                today.atTime(targetTime)
            }
        }

        fun calculatePeriodWindowTarget(earliestDate: LocalDate, leadDays: Int): LocalDateTime {
            return earliestDate.minusDays(leadDays.toLong()).atTime(9, 0)
        }

        fun calculateLateCycleTarget(latestDate: LocalDate, graceDays: Int): LocalDateTime {
            return latestDate.plusDays(graceDays.toLong()).atTime(10, 0)
        }

        fun calculatePillReminderTarget(config: PillRegimenConfig, now: LocalDateTime): LocalDateTime? =
            PillRegimenCalculator.nextReminderDateTime(config, now)
    }

    suspend fun reconcileAllSchedules(now: LocalDateTime = LocalDateTime.now()) {
        withContext(Dispatchers.IO) {
            val prefs = userPreferencesDataStore.userPreferencesFlow.first()

            if (!prefs.isNotificationsEnabled) {
                cancelAlarm(NotificationType.DAILY_CHECK_IN)
                cancelAlarm(NotificationType.PERIOD_WINDOW)
                cancelAlarm(NotificationType.LATE_CYCLE)
                cancelAlarm(NotificationType.PILL_REMINDER)
                cancelPillSnooze()
                return@withContext
            }

            // Daily Check-in
            if (prefs.isDailyCheckInEnabled) {
                scheduleDailyCheckIn(prefs.dailyCheckInTime, now)
            } else {
                cancelAlarm(NotificationType.DAILY_CHECK_IN)
            }

            // Period Window Reminder
            val cycles = cycleRepository.getCyclesOnce()
            val estimateResult = CycleEstimateCalculator.evaluate(
                cycles = cycles,
                ageBand = AgeBand.fromId(prefs.ageBand),
                hasTimingContext = prefs.hasTimingContext
            )

            if (prefs.isPeriodWindowEnabled && estimateResult is CycleEstimateResult.Available) {
                schedulePeriodWindow(estimateResult.estimate.earliestDate, prefs.periodWindowLeadDays, now)
            } else {
                cancelAlarm(NotificationType.PERIOD_WINDOW)
            }

            // Late Cycle Check-in
            if (prefs.isLateCycleEnabled && estimateResult is CycleEstimateResult.Available) {
                scheduleLateCycle(estimateResult.estimate.latestDate, prefs.lateCycleGraceDays, now)
            } else {
                cancelAlarm(NotificationType.LATE_CYCLE)
            }

            // Pill Regimen Reminder
            if (prefs.isPillRegimenEnabled) {
                schedulePillReminder(prefs.pillRegimenConfig, now)
            } else {
                cancelAlarm(NotificationType.PILL_REMINDER)
                cancelPillSnooze()
            }
        }
    }

    private suspend fun scheduleDailyCheckIn(timeStr: String, now: LocalDateTime) {
        val targetTime = runCatching { LocalTime.parse(timeStr) }.getOrDefault(LocalTime.of(21, 0))
        val today = now.toLocalDate()
        val hasEntryToday = dailyEntryRepository.getEntryOnce(today)?.hasObservations == true

        val targetDateTime = calculateDailyCheckInTarget(targetTime, now, hasEntryToday)

        setAlarm(NotificationType.DAILY_CHECK_IN, targetDateTime)
    }

    private fun schedulePeriodWindow(earliestDate: LocalDate, leadDays: Int, now: LocalDateTime) {
        val targetDateTime = calculatePeriodWindowTarget(earliestDate, leadDays)

        if (targetDateTime.isAfter(now)) {
            setAlarm(NotificationType.PERIOD_WINDOW, targetDateTime)
        } else {
            cancelAlarm(NotificationType.PERIOD_WINDOW)
        }
    }

    private fun scheduleLateCycle(latestDate: LocalDate, graceDays: Int, now: LocalDateTime) {
        val targetDateTime = calculateLateCycleTarget(latestDate, graceDays)

        if (targetDateTime.isAfter(now)) {
            setAlarm(NotificationType.LATE_CYCLE, targetDateTime)
        } else {
            cancelAlarm(NotificationType.LATE_CYCLE)
        }
    }

    private fun schedulePillReminder(config: PillRegimenConfig, now: LocalDateTime) {
        if (config.lastTakenDate == now.toLocalDate()) {
            cancelPillSnooze()
        }
        val targetDateTime = calculatePillReminderTarget(config, now)
        if (targetDateTime != null) {
            setAlarm(NotificationType.PILL_REMINDER, targetDateTime)
        } else {
            cancelAlarm(NotificationType.PILL_REMINDER)
        }
    }

    fun schedulePillSnooze(now: LocalDateTime = LocalDateTime.now(), snoozeMinutes: Long = 30L) {
        val triggerDateTime = now.plusMinutes(snoozeMinutes)
        val epochMillis = triggerDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pendingIntent = createPillSnoozePendingIntent()

        setPendingIntentAlarm(epochMillis, pendingIntent, preferExact = true)
    }

    fun cancelPillSnooze() {
        val pendingIntent = createPillSnoozePendingIntent()
        alarmManager.cancel(pendingIntent)
    }

    private fun createPillSnoozePendingIntent(): PendingIntent {
        val intent = Intent(context, NotificationAlarmReceiver::class.java).apply {
            putExtra(EXTRA_NOTIFICATION_TYPE, NotificationType.PILL_REMINDER.name)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_PILL_SNOOZE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun setAlarm(type: NotificationType, triggerDateTime: LocalDateTime) {
        val epochMillis = triggerDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pendingIntent = createPendingIntent(type)

        setPendingIntentAlarm(
            epochMillis = epochMillis,
            pendingIntent = pendingIntent,
            preferExact = type == NotificationType.PILL_REMINDER
        )
    }

    private fun setPendingIntentAlarm(
        epochMillis: Long,
        pendingIntent: PendingIntent,
        preferExact: Boolean
    ) {
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (preferExact && canExact) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, epochMillis, pendingIntent)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMillis, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, epochMillis, pendingIntent)
        }
    }

    private fun cancelAlarm(type: NotificationType) {
        val pendingIntent = createPendingIntent(type)
        alarmManager.cancel(pendingIntent)
    }

    private fun createPendingIntent(type: NotificationType): PendingIntent {
        val intent = Intent(context, NotificationAlarmReceiver::class.java).apply {
            putExtra(EXTRA_NOTIFICATION_TYPE, type.name)
        }
        val requestCode = when (type) {
            NotificationType.DAILY_CHECK_IN -> REQUEST_CODE_DAILY
            NotificationType.PERIOD_WINDOW -> REQUEST_CODE_WINDOW
            NotificationType.LATE_CYCLE -> REQUEST_CODE_LATE
            NotificationType.PILL_REMINDER -> REQUEST_CODE_PILL
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
