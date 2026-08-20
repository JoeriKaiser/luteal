package fr.luteal.app.notification

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
import javax.inject.Inject

@AndroidEntryPoint
class NotificationAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var contentResolver: NotificationContentResolver

    @Inject
    lateinit var userPreferencesDataStore: UserPreferencesDataStore

    @Inject
    lateinit var notificationScheduler: NotificationScheduler

    override fun onReceive(context: Context, intent: Intent) {
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

                val notification = NotificationCompat.Builder(context, content.channelId)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(content.title)
                    .setContentText(content.body)
                    .setContentIntent(tapPendingIntent)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setPublicVersion(publicNotification)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()

                val notificationId = when (type) {
                    NotificationType.DAILY_CHECK_IN -> 2001
                    NotificationType.PERIOD_WINDOW -> 2002
                    NotificationType.LATE_CYCLE -> 2003
                }

                NotificationManagerCompat.from(context).notify(notificationId, notification)

                // Reschedule next occurrence
                notificationScheduler.reconcileAllSchedules()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
