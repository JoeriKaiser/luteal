package fr.luteal.app.notification

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import fr.luteal.app.MainActivity
import fr.luteal.app.R
import fr.luteal.core.data.datastore.UserPreferencesDataStore
import fr.luteal.core.model.NotificationType
import fr.luteal.core.model.NotificationVisibility
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@AndroidEntryPoint
class NotificationAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_MARK_PILL_TAKEN = "fr.luteal.app.action.MARK_PILL_TAKEN"
        const val ACTION_SNOOZE_PILL = "fr.luteal.app.action.SNOOZE_PILL"
        const val NOTIFICATION_ID_PILL = 2004
        private const val REQUEST_CODE_ACTION_TAKEN = 3001
        private const val REQUEST_CODE_ACTION_SNOOZE = 3002
    }

    @Inject
    lateinit var contentResolver: NotificationContentResolver

    @Inject
    lateinit var userPreferencesDataStore: UserPreferencesDataStore

    @Inject
    lateinit var notificationScheduler: NotificationScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_MARK_PILL_TAKEN) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    userPreferencesDataStore.setPillLastTakenDate(LocalDate.now().toString())
                    notificationScheduler.cancelPillSnooze()
                    NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_PILL)
                    notificationScheduler.reconcileAllSchedules()
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        if (intent.action == ACTION_SNOOZE_PILL) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_PILL)
                    notificationScheduler.schedulePillSnooze()
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        val typeName = intent.getStringExtra(NotificationScheduler.EXTRA_NOTIFICATION_TYPE) ?: return
        val type = runCatching { NotificationType.valueOf(typeName) }.getOrNull() ?: return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = userPreferencesDataStore.userPreferencesFlow.first()
                if (!prefs.isNotificationsEnabled) return@launch

                val isTypeEnabled = when (type) {
                    NotificationType.DAILY_CHECK_IN -> prefs.isDailyCheckInEnabled
                    NotificationType.PERIOD_WINDOW -> prefs.isPeriodWindowEnabled
                    NotificationType.LATE_CYCLE -> prefs.isLateCycleEnabled
                    NotificationType.PILL_REMINDER -> prefs.isPillRegimenEnabled
                }
                if (!isTypeEnabled) return@launch

                val visibility = NotificationVisibility.fromName(prefs.notificationVisibilityMode)
                val content = contentResolver.resolve(
                    type = type,
                    visibility = visibility,
                    customTitle = prefs.notificationCustomTitle,
                    customBody = prefs.notificationCustomBody
                )

                val tapIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val tapPendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    tapIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val publicNotification = NotificationCompat.Builder(context, content.channelId)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(content.publicTitle)
                    .setContentText(content.publicBody)
                    .setContentIntent(tapPendingIntent)
                    .setAutoCancel(true)
                    .build()

                val notificationBuilder = NotificationCompat.Builder(context, content.channelId)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(content.title)
                    .setContentText(content.body)
                    .setContentIntent(tapPendingIntent)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setPublicVersion(publicNotification)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)

                if (content.actionTakenLabel != null) {
                    val takenIntent = Intent(context, NotificationAlarmReceiver::class.java).apply {
                        action = ACTION_MARK_PILL_TAKEN
                    }
                    val takenPendingIntent = PendingIntent.getBroadcast(
                        context,
                        REQUEST_CODE_ACTION_TAKEN,
                        takenIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    notificationBuilder.addAction(0, content.actionTakenLabel, takenPendingIntent)
                }

                if (content.actionSnoozeLabel != null) {
                    val snoozeIntent = Intent(context, NotificationAlarmReceiver::class.java).apply {
                        action = ACTION_SNOOZE_PILL
                    }
                    val snoozePendingIntent = PendingIntent.getBroadcast(
                        context,
                        REQUEST_CODE_ACTION_SNOOZE,
                        snoozeIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    notificationBuilder.addAction(0, content.actionSnoozeLabel, snoozePendingIntent)
                }

                val notification = notificationBuilder.build()
                val notificationId = when (type) {
                    NotificationType.DAILY_CHECK_IN -> 2001
                    NotificationType.PERIOD_WINDOW -> 2002
                    NotificationType.LATE_CYCLE -> 2003
                    NotificationType.PILL_REMINDER -> NOTIFICATION_ID_PILL
                }

                val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    try {
                        NotificationManagerCompat.from(context).notify(notificationId, notification)
                    } catch (_: SecurityException) {
                        // Suppressed if runtime notification permission was concurrently revoked
                    }
                }

                // Reschedule next occurrence
                notificationScheduler.reconcileAllSchedules()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
