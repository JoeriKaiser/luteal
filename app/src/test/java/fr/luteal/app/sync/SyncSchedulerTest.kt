package fr.luteal.app.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Checks what [SyncScheduler] enqueues: automatic work stays on its own
 * unique names, waits for a network, and cancels cleanly.
 */
@RunWith(RobolectricTestRunner::class)
class SyncSchedulerTest {

    private lateinit var context: Context
    private lateinit var scheduler: SyncScheduler

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        )
        scheduler = SyncScheduler(context)
    }

    private fun workInfos(name: String): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(name).get()

    private fun live(name: String): List<WorkInfo> =
        workInfos(name).filter { !it.state.isFinished }

    @Test
    fun periodicSyncRequiresNetworkAndUsesTheConfiguredInterval() {
        scheduler.schedulePeriodic()

        val info = live(SyncScheduler.PERIODIC_WORK_NAME).single()
        assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
        assertEquals(
            SyncScheduler.PERIODIC_INTERVAL.toMillis(),
            info.periodicityInfo?.repeatIntervalMillis
        )
    }

    @Test
    fun reschedulingPeriodicSyncKeepsOneJob() {
        scheduler.schedulePeriodic()
        scheduler.schedulePeriodic()

        assertEquals(1, live(SyncScheduler.PERIODIC_WORK_NAME).size)
    }

    @Test
    fun burstOfLocalChangesLeavesOneDelayedSync() {
        repeat(3) { scheduler.syncAfterLocalChange() }

        val pending = live(SyncScheduler.LOCAL_CHANGE_WORK_NAME)
        assertEquals(1, pending.size)
        assertEquals(WorkInfo.State.ENQUEUED, pending.single().state)
        assertTrue(pending.single().initialDelayMillis >= SyncScheduler.LOCAL_CHANGE_DEBOUNCE.toMillis())
    }

    @Test
    fun localChangeSyncDoesNotReplaceManualSync() {
        scheduler.syncNow()
        val manual = live(SyncScheduler.WORK_NAME).single().id

        scheduler.syncAfterLocalChange()
        scheduler.syncOnAppOpen()

        assertEquals(manual, live(SyncScheduler.WORK_NAME).single().id)
    }

    @Test
    fun cancelAutomaticStopsPeriodicAndPendingSyncs() {
        scheduler.schedulePeriodic()
        scheduler.syncAfterLocalChange()

        scheduler.cancelAutomatic()

        assertTrue(live(SyncScheduler.PERIODIC_WORK_NAME).isEmpty())
        assertTrue(live(SyncScheduler.LOCAL_CHANGE_WORK_NAME).isEmpty())
    }
}
