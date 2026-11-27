package fr.luteal.app.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.workDataOf
import java.time.Duration
import java.util.concurrent.TimeUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enqueues [SyncWorker] runs. Uses unique work so a trigger replaces
 * any pending/in-flight sync rather than stacking duplicates. Sync never
 * blocks the UI: callers just enqueue and observe [fr.luteal.core.data.datastore.SyncDataStore].
 *
 * Automatic runs (app open, local edits, periodic) use their own unique
 * names so they never cancel a manual "Sync now"; [fr.luteal.core.network.sync.SyncLock]
 * serializes the engine when two of them overlap.
 */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        /** Background cadence while online sync is on. */
        val PERIODIC_INTERVAL: Duration = Duration.ofHours(12)

        /** Quiet time after the last local edit before pushing it. */
        val LOCAL_CHANGE_DEBOUNCE: Duration = Duration.ofSeconds(30)

        /** Opening the app again within this window does not sync again. */
        val APP_OPEN_MIN_INTERVAL: Duration = Duration.ofMinutes(5)

        internal const val WORK_NAME = "luteal_cycle_sync"
        internal const val LOCAL_CHANGE_WORK_NAME = "luteal_cycle_sync_local_change"
        internal const val PERIODIC_WORK_NAME = "luteal_cycle_sync_periodic"
    }

    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    fun syncNow() {
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, oneTimeRequest())
    }

    /** Leaves a pending or running sync alone instead of restarting it. */
    fun syncOnAppOpen() {
        workManager.enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            oneTimeRequest(automatic = true)
        )
    }

    /** REPLACE pushes the delay back, so a burst of edits ends in one sync. */
    fun syncAfterLocalChange() {
        workManager.enqueueUniqueWork(
            LOCAL_CHANGE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            oneTimeRequest(automatic = true, delay = LOCAL_CHANGE_DEBOUNCE)
        )
    }

    fun schedulePeriodic() {
        // The first run waits a full interval: app open already covers "now".
        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            PERIODIC_INTERVAL.toMillis(),
            TimeUnit.MILLISECONDS
        )
            .setConstraints(networkConstraint)
            .setInitialDelay(PERIODIC_INTERVAL.toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(SyncWorker.KEY_AUTOMATIC to true))
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancelAutomatic() {
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        workManager.cancelUniqueWork(LOCAL_CHANGE_WORK_NAME)
    }

    suspend fun cancelAndJoin() {
        workManager.cancelUniqueWork(LOCAL_CHANGE_WORK_NAME)
        val operation = workManager.cancelUniqueWork(WORK_NAME)
        suspendCancellableCoroutine { continuation ->
            operation.result.addListener(
                { if (continuation.isActive) continuation.resume(Unit) },
                Runnable::run
            )
        }
    }

    private fun oneTimeRequest(
        automatic: Boolean = false,
        delay: Duration = Duration.ZERO
    ): OneTimeWorkRequest {
        // CONNECTED constraint keeps offline edits from failing outright;
        // transient transport failures retry inside the worker with this
        // backoff until MAX_TRANSIENT_ATTEMPTS is exhausted.
        return OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraint)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(SyncWorker.KEY_AUTOMATIC to automatic))
            .build()
    }

    private val networkConstraint = Constraints(requiredNetworkType = NetworkType.CONNECTED)
}
