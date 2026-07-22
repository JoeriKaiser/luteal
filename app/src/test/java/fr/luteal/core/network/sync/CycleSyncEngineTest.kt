package fr.luteal.core.network.sync

import fr.luteal.core.data.entity.SyncStateEntity
import fr.luteal.core.model.BleedingIntensity
import fr.luteal.core.model.Cycle
import fr.luteal.core.model.PeriodDay
import fr.luteal.core.network.FolicularApiException
import fr.luteal.core.network.ConflictWire
import fr.luteal.core.network.PullChangeWire
import fr.luteal.core.network.PullResultWire
import fr.luteal.core.network.PushResultWire
import fr.luteal.core.network.auth.SyncCredentials
import fr.luteal.core.network.contract.models.AppliedChange
import fr.luteal.core.network.contract.models.BleedingObservationData
import fr.luteal.core.network.contract.models.Certainty
import fr.luteal.core.network.contract.models.CycleData
import fr.luteal.core.network.contract.models.EntityType
import fr.luteal.core.network.contract.models.Flow
import fr.luteal.core.network.contract.models.NewDevice
import fr.luteal.core.network.contract.models.RecordSource
import fr.luteal.core.network.contract.models.Register201Response
import fr.luteal.core.network.contract.models.Register201ResponseAccount
import fr.luteal.core.network.toJsonElement
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CycleSyncEngineTest {

    private val now: OffsetDateTime = OffsetDateTime.of(2026, 7, 1, 8, 0, 0, 0, ZoneOffset.UTC)
    private val cycleId: UUID = UUID.fromString("019832e0-6c14-7000-8000-000000000001")
    private val startDate: LocalDate = LocalDate.of(2026, 6, 30)

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

    private fun localCycle() = Cycle(
        id = cycleId.toString(),
        startDate = startDate,
        endDate = null,
        periodDays = listOf(
            PeriodDay(startDate, BleedingIntensity.MEDIUM),
            PeriodDay(startDate.plusDays(1), BleedingIntensity.LIGHT)
        )
    )

    private fun dirtyState() = SyncStateEntity(
        entityId = cycleId.toString(),
        entityType = SyncStateEntity.TYPE_CYCLE,
        clientRev = UUID.randomUUID().toString(),
        createdAtEpochMillis = now.toInstant().toEpochMilli(),
        updatedAtEpochMillis = now.toInstant().toEpochMilli(),
        dirty = true
    )

    private fun cycleData(id: UUID = cycleId, start: LocalDate = startDate) = CycleData(
        id = id,
        clientRev = UUID.randomUUID(),
        createdAt = now,
        updatedAt = now,
        deletedAt = null,
        startDate = start,
        endDate = null,
        lengthDays = null,
        bleedingDays = 2,
        certainty = Certainty.RECORDED,
        source = RecordSource.MANUAL,
        notes = ""
    )

    private fun bleedingData(date: LocalDate, flow: Flow) = BleedingObservationData(
        id = UUID.randomUUID(),
        clientRev = UUID.randomUUID(),
        createdAt = now,
        updatedAt = now,
        observedDate = date,
        flow = flow,
        intermenstrual = false,
        notes = ""
    )

    private fun engine(
        repo: FakeCycleRepository,
        stateDao: FakeSyncStateDao,
        creds: FakeCredentialStore,
        cursor: FakeCursorStore,
        api: FakeApiClient
    ) = CycleSyncEngine(
        cycleRepository = repo,
        syncStateDao = stateDao,
        dailyEntryDao = FakeDailyEntryDao(),
        symptomDao = FakeSymptomDao(),
        credentialStore = creds,
        apiClientFactory = FolicularApiClientFactory { api },
        cursorStore = cursor,
        deviceNameProvider = { "test-device" }
    )

    @Test
    fun `first run registers, pushes cycle with fanned bleeding, pulls and advances cursor`() = runTest {
        val repo = FakeCycleRepository(listOf(localCycle()))
        val stateDao = FakeSyncStateDao().apply { upsert(dirtyState()) }
        val creds = FakeCredentialStore(null)
        val cursor = FakeCursorStore(cursor = 0L)
        val api = FakeApiClient().apply {
            registerResponse = this@CycleSyncEngineTest.registerResponse
            pushResult = PushResultWire(
                applied = listOf(AppliedChange(EntityType.CYCLE, cycleId, 3L)),
                rejected = emptyList(),
                conflicts = emptyList(),
                cursor = 5L
            )
            pullResults += PullResultWire(
                changes = listOf(
                    PullChangeWire(3L, EntityType.CYCLE, cycleId, false, now, cycleData().toJsonElement()),
                    PullChangeWire(4L, EntityType.BLEEDING_OBSERVATION, UUID.randomUUID(), false, now,
                        bleedingData(startDate, Flow.MEDIUM).toJsonElement()),
                    PullChangeWire(5L, EntityType.BLEEDING_OBSERVATION, UUID.randomUUID(), false, now,
                        bleedingData(startDate.plusDays(1), Flow.LIGHT).toJsonElement())
                ),
                cursor = 5L,
                hasMore = false
            )
        }

        val report = engine(repo, stateDao, creds, cursor, api).sync()

        // Registered and stored credentials (device token reused next time).
        assertTrue(report.registered)
        assertNotNull(creds.load())
        assertEquals("ltok_test_token", creds.load()!!.deviceToken)
        assertEquals(1, api.registerCalls)

        // Pushed one cycle plus one bleeding observation per period day.
        val pushed = api.pushCalls.single()
        assertEquals(1, pushed.count { it.entityType == EntityType.CYCLE })
        assertEquals(2, pushed.count { it.entityType == EntityType.BLEEDING_OBSERVATION })
        assertEquals(1, report.pushedRecords)

        // Pulled, applied, and advanced the cursor.
        assertEquals(listOf(0L), api.pullSinceValues)
        assertEquals(5L, cursor.getCursor())
        assertEquals(5L, report.cursor)
        assertEquals(1, report.recordsApplied)

        // The cycle was re-adopted with its period days rebuilt from bleeding.
        val adopted = repo.cycles[cycleId.toString()]!!
        assertEquals(2, adopted.periodDays.size)
        assertEquals(startDate, adopted.periodDays.first().date)

        // The sync state is clean after a successful round trip.
        assertFalse(stateDao.states[cycleId.toString()]!!.dirty)
    }

    @Test
    fun `returning run reuses stored token and does not register again`() = runTest {
        val repo = FakeCycleRepository()
        val stateDao = FakeSyncStateDao()
        val creds = FakeCredentialStore(
            SyncCredentials("acct", "LTL-XXXXX-XXXXX-XXXXX-XXXXX", "ltok_existing")
        )
        val cursor = FakeCursorStore(cursor = 7L)
        val api = FakeApiClient().apply {
            registerResponse = this@CycleSyncEngineTest.registerResponse
            pullResults += PullResultWire(emptyList(), 7L, false)
        }

        val report = engine(repo, stateDao, creds, cursor, api).sync()

        assertFalse(report.registered)
        assertEquals(0, api.registerCalls)
        assertEquals(0, api.pushCalls.size)
        assertEquals(listOf(7L), api.pullSinceValues)
    }

    @Test
    fun `push conflict adopts the server current record`() = runTest {
        val repo = FakeCycleRepository(listOf(localCycle()))
        val stateDao = FakeSyncStateDao().apply { upsert(dirtyState()) }
        val creds = FakeCredentialStore(SyncCredentials("acct", "code", "ltok"))
        val cursor = FakeCursorStore(cursor = 9L)
        val serverStart = LocalDate.of(2026, 6, 29)
        val api = FakeApiClient().apply {
            pushResult = PushResultWire(
                applied = emptyList(),
                rejected = emptyList(),
                conflicts = listOf(
                    ConflictWire(
                        entityType = EntityType.CYCLE,
                        entityId = cycleId,
                        reason = "superseded",
                        current = cycleData(start = serverStart).toJsonElement()
                    )
                ),
                cursor = 9L
            )
            pullResults += PullResultWire(emptyList(), 9L, false)
        }

        val report = engine(repo, stateDao, creds, cursor, api).sync()

        assertEquals(1, report.conflictsAdopted)
        // Server state wins: the local start date is replaced.
        assertEquals(serverStart, repo.cycles[cycleId.toString()]!!.startDate)
        assertFalse(stateDao.states[cycleId.toString()]!!.dirty)
    }

    @Test
    fun `pull tombstone deletes the local cycle`() = runTest {
        val repo = FakeCycleRepository(listOf(localCycle()))
        val stateDao = FakeSyncStateDao().apply { upsert(dirtyState().copy(dirty = false)) }
        val creds = FakeCredentialStore(SyncCredentials("acct", "code", "ltok"))
        val cursor = FakeCursorStore(cursor = 0L)
        val api = FakeApiClient().apply {
            pullResults += PullResultWire(
                changes = listOf(
                    PullChangeWire(1L, EntityType.CYCLE, cycleId, true, now, null)
                ),
                cursor = 1L,
                hasMore = false
            )
        }

        val report = engine(repo, stateDao, creds, cursor, api).sync()

        assertEquals(1, report.tombstonesApplied)
        assertNull(repo.cycles[cycleId.toString()])
        assertTrue(repo.deletedIds.contains(cycleId.toString()))
    }

    @Test
    fun `paginated pull follows has_more and advances cursor per page`() = runTest {
        val repo = FakeCycleRepository()
        val stateDao = FakeSyncStateDao()
        val creds = FakeCredentialStore(SyncCredentials("acct", "code", "ltok"))
        val cursor = FakeCursorStore(cursor = 0L)
        val api = FakeApiClient().apply {
            pullResults += PullResultWire(emptyList(), 10L, true)
            pullResults += PullResultWire(emptyList(), 20L, false)
        }

        val report = engine(repo, stateDao, creds, cursor, api).sync()

        assertEquals(listOf(0L, 10L), api.pullSinceValues)
        assertEquals(20L, report.cursor)
        assertEquals(20L, cursor.getCursor())
    }

    @Test
    fun `auth failure clears stored credentials for re-registration`() = runTest {
        val repo = FakeCycleRepository()
        val stateDao = FakeSyncStateDao()
        val creds = FakeCredentialStore(SyncCredentials("acct", "code", "ltok_revoked"))
        val cursor = FakeCursorStore(cursor = 0L)
        val api = FakeApiClient().apply {
            pullError = FolicularApiException(401, "about:blank", "invalid token")
        }

        val result = runCatching { engine(repo, stateDao, creds, cursor, api).sync() }

        assertTrue(result.isFailure)
        assertNull(creds.load())
    }
}
