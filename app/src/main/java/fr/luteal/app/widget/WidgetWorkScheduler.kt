package fr.luteal.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.luteal.app.widget.duo.DuoCycleWidgetReceiver
import fr.luteal.app.widget.duo.DuoWidgetRefreshWorker
import fr.luteal.app.widget.personal.PersonalCycleWidgetReceiver
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetWorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock
) {
    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    fun reconcileSchedules() {
        if (hasAnyWidgets()) scheduleMaintenance() else {
            workManager.cancelUniqueWork(MAINTENANCE_WORK)
        }

        if (hasDuoWidgets()) schedulePeriodicDuoRefresh() else {
            workManager.cancelUniqueWork(DUO_PERIODIC_WORK)
        }
    }

    fun refreshDuoNow() {
        if (!hasDuoWidgets()) return
        val request = OneTimeWorkRequestBuilder<DuoWidgetRefreshWorker>()
            .setConstraints(networkConstraint)
            .build()
        workManager.enqueueUniqueWork(
            DUO_IMMEDIATE_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun refreshAllNow() {
        if (!hasAnyWidgets()) return
        workManager.enqueueUniqueWork(
            MAINTENANCE_IMMEDIATE_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WidgetMaintenanceWorker>().build()
        )
    }

    private fun scheduleMaintenance() {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(clock.withZone(zone))
        val next = now.toLocalDate().plusDays(1).atStartOfDay(zone).plusMinutes(5)
        val delay = Duration.between(now, next).coerceAtLeast(Duration.ZERO)
        val request = OneTimeWorkRequestBuilder<WidgetMaintenanceWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork(
            MAINTENANCE_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun schedulePeriodicDuoRefresh() {
        val request = PeriodicWorkRequestBuilder<DuoWidgetRefreshWorker>(12, TimeUnit.HOURS)
            .setConstraints(networkConstraint)
            .build()
        workManager.enqueueUniquePeriodicWork(
            DUO_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun hasAnyWidgets(): Boolean = hasPersonalWidgets() || hasDuoWidgets()

    private fun hasPersonalWidgets(): Boolean = widgetIds(PersonalCycleWidgetReceiver::class.java).isNotEmpty()

    private fun hasDuoWidgets(): Boolean = widgetIds(DuoCycleWidgetReceiver::class.java).isNotEmpty()

    private fun widgetIds(receiver: Class<*>): IntArray =
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, receiver))

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    private companion object {
        const val MAINTENANCE_WORK = "luteal_widget_daily_maintenance"
        const val MAINTENANCE_IMMEDIATE_WORK = "luteal_widget_immediate_maintenance"
        const val DUO_IMMEDIATE_WORK = "luteal_duo_widget_refresh"
        const val DUO_PERIODIC_WORK = "luteal_duo_widget_periodic_refresh"
    }
}
