package fr.luteal.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.luteal.app.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationChannelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_DAILY_CHECKIN = "channel_daily_checkin"
        const val CHANNEL_PERIOD_WINDOW = "channel_period_window"
        const val CHANNEL_LATE_CYCLE = "channel_late_cycle"
    }

    fun registerChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return

            val dailyChannel = NotificationChannel(
                CHANNEL_DAILY_CHECKIN,
                context.getString(R.string.notif_channel_daily_title),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notif_channel_daily_desc)
                setShowBadge(true)
            }

            val windowChannel = NotificationChannel(
                CHANNEL_PERIOD_WINDOW,
                context.getString(R.string.notif_channel_window_title),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notif_channel_window_desc)
                setShowBadge(true)
            }

            val lateChannel = NotificationChannel(
                CHANNEL_LATE_CYCLE,
                context.getString(R.string.notif_channel_late_title),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notif_channel_late_desc)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannels(listOf(dailyChannel, windowChannel, lateChannel))
        }
    }
}
