package fr.luteal.app.notification

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.luteal.app.R
import fr.luteal.core.model.NotificationType
import fr.luteal.core.model.NotificationVisibility
import javax.inject.Inject
import javax.inject.Singleton

data class ResolvedNotificationContent(
    val title: String,
    val body: String,
    val publicTitle: String,
    val publicBody: String,
    val channelId: String,
    val actionTakenLabel: String? = null,
    val actionSnoozeLabel: String? = null
)

@Singleton
class NotificationContentResolver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun resolve(
        type: NotificationType,
        visibility: NotificationVisibility,
        customTitle: String = "",
        customBody: String = ""
    ): ResolvedNotificationContent {
        val channelId = when (type) {
            NotificationType.DAILY_CHECK_IN -> NotificationChannelManager.CHANNEL_DAILY_CHECKIN
            NotificationType.PERIOD_WINDOW -> NotificationChannelManager.CHANNEL_PERIOD_WINDOW
            NotificationType.LATE_CYCLE -> NotificationChannelManager.CHANNEL_LATE_CYCLE
            NotificationType.PILL_REMINDER -> NotificationChannelManager.CHANNEL_PILL_REMINDER
        }

        val defaultPublicTitle = context.getString(R.string.notif_default_public_title)
        val defaultPublicBody = context.getString(R.string.notif_default_public_body)

        return when (visibility) {
            NotificationVisibility.CUSTOM -> {
                val title = customTitle.ifBlank { context.getString(R.string.notif_default_concealed_title) }
                val body = customBody.ifBlank { context.getString(R.string.notif_default_concealed_body) }
                ResolvedNotificationContent(
                    title = title,
                    body = body,
                    publicTitle = defaultPublicTitle,
                    publicBody = defaultPublicBody,
                    channelId = channelId,
                    actionTakenLabel = if (type == NotificationType.PILL_REMINDER) {
                        context.getString(R.string.notif_pill_action_taken_concealed)
                    } else null,
                    actionSnoozeLabel = if (type == NotificationType.PILL_REMINDER) {
                        context.getString(R.string.notif_pill_action_snooze)
                    } else null
                )
            }
            NotificationVisibility.DESCRIPTIVE -> {
                val (title, body) = when (type) {
                    NotificationType.DAILY_CHECK_IN -> Pair(
                        context.getString(R.string.notif_daily_descriptive_title),
                        context.getString(R.string.notif_daily_descriptive_body)
                    )
                    NotificationType.PERIOD_WINDOW -> Pair(
                        context.getString(R.string.notif_window_descriptive_title),
                        context.getString(R.string.notif_window_descriptive_body)
                    )
                    NotificationType.LATE_CYCLE -> Pair(
                        context.getString(R.string.notif_late_descriptive_title),
                        context.getString(R.string.notif_late_descriptive_body)
                    )
                    NotificationType.PILL_REMINDER -> Pair(
                        context.getString(R.string.notif_pill_descriptive_title),
                        context.getString(R.string.notif_pill_descriptive_body)
                    )
                }
                ResolvedNotificationContent(
                    title = title,
                    body = body,
                    publicTitle = defaultPublicTitle,
                    publicBody = defaultPublicBody,
                    channelId = channelId,
                    actionTakenLabel = if (type == NotificationType.PILL_REMINDER) {
                        context.getString(R.string.notif_pill_action_taken_descriptive)
                    } else null,
                    actionSnoozeLabel = if (type == NotificationType.PILL_REMINDER) {
                        context.getString(R.string.notif_pill_action_snooze)
                    } else null
                )
            }
            NotificationVisibility.CONCEALED -> {
                ResolvedNotificationContent(
                    title = context.getString(R.string.notif_default_concealed_title),
                    body = context.getString(R.string.notif_default_concealed_body),
                    publicTitle = defaultPublicTitle,
                    publicBody = defaultPublicBody,
                    channelId = channelId,
                    actionTakenLabel = if (type == NotificationType.PILL_REMINDER) {
                        context.getString(R.string.notif_pill_action_taken_concealed)
                    } else null,
                    actionSnoozeLabel = if (type == NotificationType.PILL_REMINDER) {
                        context.getString(R.string.notif_pill_action_snooze)
                    } else null
                )
            }
        }
    }
}
