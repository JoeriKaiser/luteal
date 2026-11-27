package fr.luteal.app.update

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.luteal.app.MainActivity
import fr.luteal.app.R
import fr.luteal.app.notification.NotificationChannelManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun notifyUpdateAvailable(version: String) {
        val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_WIDGET_DESTINATION
            putExtra(MainActivity.EXTRA_WIDGET_DESTINATION, MainActivity.WIDGET_DESTINATION_SETTINGS)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_UPDATE_AVAILABLE,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationChannelManager.CHANNEL_APP_UPDATES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.notif_update_available_title))
            .setContentText(context.getString(R.string.notif_update_available_body, version))
            .setContentIntent(tapPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_UPDATE_AVAILABLE, notification)
        } catch (_: SecurityException) {
            // Suppressed if runtime notification permission was concurrently revoked
        }
    }

    fun cancel() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_UPDATE_AVAILABLE)
    }

    companion object {
        const val NOTIFICATION_ID_UPDATE_AVAILABLE = 2101
    }
}
