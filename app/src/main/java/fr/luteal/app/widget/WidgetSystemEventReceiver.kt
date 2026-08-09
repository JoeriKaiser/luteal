package fr.luteal.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Keeps calendar-day content and scheduling correct after system changes. */
class WidgetSystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in supportedActions) return
        context.widgetEntryPoint().workScheduler().apply {
            reconcileSchedules()
            refreshAllNow()
        }
    }

    private companion object {
        val supportedActions = setOf(
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_LOCALE_CHANGED
        )
    }
}
