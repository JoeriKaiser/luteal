package fr.luteal.app.widget

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Re-renders date-sensitive content shortly after local midnight. */
@HiltWorker
class WidgetMaintenanceWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val updates: WidgetUpdateCoordinator,
    private val scheduler: WidgetWorkScheduler
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = runCatching {
        updates.updateAll()
        scheduler.reconcileSchedules()
        Result.success()
    }.getOrElse { Result.retry() }
}
