package fr.luteal.app.notification

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
import fr.luteal.core.data.datastore.UserPreferences
import fr.luteal.core.model.NotificationVisibility
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DuoPartnerPillAlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val NOTIFICATION_ID_DUO_PARTNER_PILL_REMINDER = 2005
        const val NOTIFICATION_ID_DUO_PARTNER_PILL_TAKEN = 2006
    }

    fun cancelPendingReminderNotification() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_DUO_PARTNER_PILL_REMINDER)
    }

    fun notifyPartnerPillTaken(prefs: UserPreferences) {
        val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return

        val visibility = NotificationVisibility.fromName(prefs.notificationVisibilityMode)
        val defaultPublicTitle = context.getString(R.string.notif_default_public_title)
        val defaultPublicBody = context.getString(R.string.notif_default_public_body)

        val (title, body) = when (visibility) {
            NotificationVisibility.DESCRIPTIVE -> Pair(
                context.getString(R.string.notif_duo_partner_pill_taken_title),
                context.getString(R.string.notif_duo_partner_pill_taken_body)
            )
            NotificationVisibility.CUSTOM -> Pair(
                prefs.notificationCustomTitle.ifBlank { context.getString(R.string.notif_default_concealed_title) },
                prefs.notificationCustomBody.ifBlank { context.getString(R.string.notif_default_concealed_body) }
            )
            NotificationVisibility.CONCEALED -> Pair(
                context.getString(R.string.notif_default_concealed_title),
                context.getString(R.string.notif_default_concealed_body)
            )
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_WIDGET_DESTINATION
            putExtra(MainActivity.EXTRA_WIDGET_DESTINATION, MainActivity.WIDGET_DESTINATION_DUO)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_DUO_PARTNER_PILL_TAKEN,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val publicNotification = NotificationCompat.Builder(context, NotificationChannelManager.CHANNEL_PILL_REMINDER)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(defaultPublicTitle)
            .setContentText(defaultPublicBody)
            .setContentIntent(tapPendingIntent)
            .setAutoCancel(true)
            .build()

        val notification = NotificationCompat.Builder(context, NotificationChannelManager.CHANNEL_PILL_REMINDER)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(tapPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_DUO_PARTNER_PILL_TAKEN, notification)
        } catch (_: SecurityException) {
            // Suppressed if runtime notification permission was concurrently revoked
        }
    }
}
