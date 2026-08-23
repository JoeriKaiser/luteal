package fr.luteal.app.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import fr.luteal.app.R
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.WorkerParameters
import fr.luteal.core.data.entity.SyncStateEntity
import fr.luteal.core.model.Cycle
import fr.luteal.core.data.datastore.SyncDataStore
import fr.luteal.core.data.datastore.UserPreferencesDataStore
import fr.luteal.core.model.SyncMode
import fr.luteal.core.network.FolicularApiException
import fr.luteal.core.network.auth.SyncCredentials
import java.time.LocalDate
import fr.luteal.core.network.sync.FakeApiClient
import fr.luteal.core.network.sync.FakeBiomarkerDao
import fr.luteal.core.network.sync.FakeCredentialStore
import fr.luteal.core.network.sync.FakeCursorStore
import fr.luteal.core.network.sync.FakeCycleRepository
import fr.luteal.core.network.sync.FakeDailyEntryDao
import fr.luteal.core.network.sync.FakeSymptomDao
import fr.luteal.core.network.sync.FakeSyncStateDao
import fr.luteal.core.network.sync.CycleSyncEngine
import fr.luteal.core.network.crypto.RecordSealer
import fr.luteal.core.network.contract.models.NewDevice
import fr.luteal.core.network.contract.models.Register201Response
import fr.luteal.core.network.contract.models.Register201ResponseAccount
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression tests for [SyncWorker] error routing: cancellation must
 * propagate, auth failures are terminal without touching stored credentials,
 * transient transport failures retry, and the IN_PROGRESS flag never gets
 * stranded.
 */
@RunWith(RobolectricTestRunner::class)
class SyncWorkerTest {

    private lateinit var context: Context
    private lateinit var userPreferencesDataStore: UserPreferencesDataStore
    private lateinit var syncDataStore: SyncDataStore

    private val fixedClock: Clock = Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC)

    private val registerResponse = Register201Response(
        account = Register201ResponseAccount(
            id = UUID.fromString("019832e0-6c14-7000-8000-0000000000aa"),
            code = "LTL-8K3FQ-Z2WNT-7HJMC-4XRDB"
        ),
        device = NewDevice(
            id = UUID.fromString("019832e0-6c14-7000-8000-0000000000bb"),
            name = "test-device",
            token = "ltok_test_token"
        ),
        warning = "Conservez votre code."
    )

    @Before
    fun setup() = runTest {
        context = ApplicationProvider.getApplicationContext()
        userPreferencesDataStore = UserPreferencesDataStore(context)
        userPreferencesDataStore.clear()
        userPreferencesDataStore.setSyncMode(SyncMode.ONLINE_CLOUD.name)
        syncDataStore = SyncDataStore(context)
        syncDataStore.clear()
    }

    /**
     * Real [CycleSyncEngine] over in-memory fakes; registers on first pass
     * and carries one dirty cycle so [FolicularApiClient.syncPush] is always
     * reached (error injection happens there).
     */
    private suspend fun buildEngine(api: FakeApiClient): Pair<CycleSyncEngine, FakeCredentialStore> {
        val creds = FakeCredentialStore(null)
        val cycle = Cycle(
            id = "019832e0-6c14-7000-8000-000000000001",
            startDate = LocalDate.parse("2026-06-30"),
            endDate = null
        )
        val stateDao = FakeSyncStateDao().apply {
            upsert(
                SyncStateEntity(
                    entityId = cycle.id,
                    entityType = SyncStateEntity.TYPE_CYCLE,
                    clientRev = UUID.randomUUID().toString(),
                    createdAtEpochMillis = 0L,
                    updatedAtEpochMillis = 0L,
                    dirty = true
                )
            )
        }
        val engine = CycleSyncEngine(
            cycleRepository = FakeCycleRepository(listOf(cycle)),
            syncStateDao = stateDao,
            dailyEntryDao = FakeDailyEntryDao(),
            symptomDao = FakeSymptomDao(),
            biomarkerDao = FakeBiomarkerDao(),
            credentialStore = creds,
            apiClientFactory = { api },
            cursorStore = FakeCursorStore(),
            recordSealer = RecordSealer(creds)
        )
        return engine to creds
    }

    private suspend fun buildWorker(
        engine: CycleSyncEngine,
        runAttemptCount: Int = 0
    ): SyncWorker {
        val builder = TestListenableWorkerBuilder<SyncWorker>(context)
            .setRunAttemptCount(runAttemptCount)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker? = SyncWorker(
                    appContext,
                    workerParameters,
                    engine,
                    userPreferencesDataStore,
                    syncDataStore,
                    fixedClock
                )
            })
        return builder.build() as SyncWorker
    }

    private fun api(pushError: Throwable? = null) = FakeApiClient().apply {
        registerResponse = this@SyncWorkerTest.registerResponse
        this.pushError = pushError
    }

    @Test
    fun successfulSyncRecordsSuccessAndClearsError() = runTest {
        syncDataStore.recordError("stale error")
        val (engine, _) = buildEngine(api())
        val worker = buildWorker(engine)

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        val prefs = syncDataStore.syncPreferencesFlow.first()
        assertNull(prefs.lastError)
        assertFalse(prefs.inProgress)
        assertEquals(1_000L, prefs.lastSyncedEpochMillis)
    }

    @Test
    fun offlineModeIsANoOpWithoutRecordingAnything() = runTest {
        userPreferencesDataStore.setSyncMode(SyncMode.OFFLINE_LOCAL.name)
        val failingApi = api()
        failingApi.pushError = RuntimeException("sync must not run")
        val (engine, _) = buildEngine(failingApi)
        val worker = buildWorker(engine)

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertNull(syncDataStore.syncPreferencesFlow.first().lastError)
    }

    @Test
    fun authFailureIsTerminalWithLocalizedMessageAndKeepsCredentials() = runTest {
        val (engine, creds) = buildEngine(
            api(pushError = FolicularApiException(401, "unauthorized", "token rejected"))
        )
        val worker = buildWorker(engine)

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals(
            context.getString(R.string.sync_error_auth_rejected),
            syncDataStore.syncPreferencesFlow.first().lastError
        )
        // The account code survives: recovery stays possible from Settings.
        assertNotNull(creds.credentials)
        assertFalse(syncDataStore.syncPreferencesFlow.first().inProgress)
    }

    @Test
    fun transportFailureRetriesBeforeAttemptBudgetIsSpent() = runTest {
        val (engine, _) = buildEngine(api(pushError = IOException("offline")))
        val worker = buildWorker(engine, runAttemptCount = 0)

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
        assertFalse(syncDataStore.syncPreferencesFlow.first().inProgress)
    }

    @Test
    fun server5xxRetriesBeforeAttemptBudgetIsSpent() = runTest {
        val (engine, _) = buildEngine(
            api(pushError = FolicularApiException(503, "unavailable", "try later"))
        )
        val worker = buildWorker(engine, runAttemptCount = 0)

        assertTrue(worker.doWork() is ListenableWorker.Result.Retry)
    }

    @Test
    fun exhaustedTransportBudgetRecordsErrorAndFails() = runTest {
        val (engine, _) = buildEngine(api(pushError = IOException("still offline")))
        val worker = buildWorker(engine, runAttemptCount = SyncWorker.MAX_TRANSIENT_ATTEMPTS)

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals("still offline", syncDataStore.syncPreferencesFlow.first().lastError)
        assertFalse(syncDataStore.syncPreferencesFlow.first().inProgress)
    }

    @Test
    fun cancellationPropagatesAndDoesNotStrandInProgressFlag() = runTest {
        val (engine, _) = buildEngine(
            api(pushError = CancellationException("replaced by a newer sync"))
        )
        val worker = buildWorker(engine)

        val thrown = runCatching { worker.doWork() }.exceptionOrNull()

        assertTrue(thrown is CancellationException)
        // The finally block must reset the flag even though the job threw.
        assertFalse(syncDataStore.syncPreferencesFlow.first().inProgress)
        assertNull(syncDataStore.syncPreferencesFlow.first().lastError)
    }
}
