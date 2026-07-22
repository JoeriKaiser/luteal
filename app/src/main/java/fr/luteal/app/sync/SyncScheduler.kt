package fr.luteal.app.sync

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enqueues the on-demand [SyncWorker]. Uses unique work so a trigger replaces
 * any pending/in-flight sync rather than stacking duplicates. Sync never
 * blocks the UI: callers just enqueue and observe [fr.luteal.core.data.datastore.SyncDataStore].
 */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        const val WORK_NAME = "luteal_cycle_sync"
    }

    fun syncNow() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>().build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}
