package fr.luteal.app.widget.personal

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import fr.luteal.app.widget.widgetEntryPoint

class PersonalCycleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PersonalCycleWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        context.widgetEntryPoint().workScheduler().reconcileSchedules()
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        context.widgetEntryPoint().workScheduler().reconcileSchedules()
    }
}
