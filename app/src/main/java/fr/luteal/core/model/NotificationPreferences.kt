package fr.luteal.core.model

enum class NotificationVisibility {
    CONCEALED,
    DESCRIPTIVE,
    CUSTOM;

    companion object {
        fun fromName(name: String?): NotificationVisibility {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: CONCEALED
        }
    }
}

enum class NotificationType {
    DAILY_CHECK_IN,
    PERIOD_WINDOW,
    LATE_CYCLE
}

data class NotificationPreferences(
    val isNotificationsEnabled: Boolean = false,
    val isDailyCheckInEnabled: Boolean = false,
    val dailyCheckInTime: String = "21:00",
    val isPeriodWindowEnabled: Boolean = false,
    val periodWindowLeadDays: Int = 2,
    val isLateCycleEnabled: Boolean = false,
    val lateCycleGraceDays: Int = 1,
    val visibilityMode: NotificationVisibility = NotificationVisibility.CONCEALED,
    val customTitle: String = "",
    val customBody: String = ""
)
