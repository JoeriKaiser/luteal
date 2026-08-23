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
import fr.luteal.core.network.crypto.RecordSealer
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
import kotlinx.serialization.json.JsonElement
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
        api: FakeApiClient,
        dailyEntryDao: FakeDailyEntryDao = FakeDailyEntryDao(),
        symptomDao: FakeSymptomDao = FakeSymptomDao(),
        biomarkerDao: FakeBiomarkerDao = FakeBiomarkerDao()
    ) = CycleSyncEngine(
        cycleRepository = repo,
        syncStateDao = stateDao,
        dailyEntryDao = dailyEntryDao,
        symptomDao = symptomDao,
        biomarkerDao = biomarkerDao,
        credentialStore = creds,
        apiClientFactory = FolicularApiClientFactory { api },
        cursorStore = cursor,
        recordSealer = RecordSealer(creds)
    )

    /**
     * A sealer holding the credentials the engine ends up with after
     * registering, so fixtures are sealed under the same key the engine will
     * derive. Pull tests then exercise the real decrypt path rather than a
     * plaintext shortcut.
     */
    private val fixtureSealer = RecordSealer(
        FakeCredentialStore(
            SyncCredentials(
                accountId = registerResponse.account.id.toString(),
                accountCode = registerResponse.account.code,
                deviceToken = registerResponse.device.token
            )
        )
    )

    /** Seals the server's current state for a conflict, under [creds]' key. */
    private fun sealedConflict(
        entityId: UUID,
        payload: JsonElement,
        creds: FakeCredentialStore,
        clientRev: UUID = UUID.randomUUID()
    ) = ConflictWire(
        entityType = EntityType.CYCLE,
        entityId = entityId,
        reason = "superseded",
        currentClientRev = clientRev,
        currentUpdatedAt = now,
        currentDeleted = false,
        currentCiphertext = RecordSealer(creds).seal(
            EntityType.CYCLE.value, entityId.toString(), clientRev.toString(), payload
        )
    )

    /** Seals a record the way the server would have stored it. */
    private fun sealedPull(
        seq: Long,
        entityType: EntityType,
        entityId: UUID,
        payload: JsonElement,
        clientRev: UUID = UUID.randomUUID()
    ) = PullChangeWire(
        seq = seq,
        entityType = entityType,
        entityId = entityId,
        clientRev = clientRev,
        deleted = false,
        updatedAt = now,
        ciphertext = fixtureSealer.seal(
            entityType.value, entityId.toString(), clientRev.toString(), payload
        )
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
                    sealedPull(3L, EntityType.CYCLE, cycleId, cycleData().toJsonElement()),
                    sealedPull(
                        4L, EntityType.BLEEDING_OBSERVATION, UUID.randomUUID(),
                        bleedingData(startDate, Flow.MEDIUM).toJsonElement()
                    ),
                    sealedPull(
                        5L, EntityType.BLEEDING_OBSERVATION, UUID.randomUUID(),
                        bleedingData(startDate.plusDays(1), Flow.LIGHT).toJsonElement()
                    )
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
                    sealedConflict(
                        entityId = cycleId,
                        payload = cycleData(start = serverStart).toJsonElement(),
                        creds = creds
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
                    PullChangeWire(
                        seq = 1L,
                        entityType = EntityType.CYCLE,
                        entityId = cycleId,
                        clientRev = UUID.randomUUID(),
                        deleted = true,
                        updatedAt = now,
                        ciphertext = null
                    )
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
    fun `auth failure surfaces terminal error and never re-registers`() = runTest {
        val existing = SyncCredentials("acct", "code", "ltok_revoked")
        val repo = FakeCycleRepository()
        val stateDao = FakeSyncStateDao()
        val creds = FakeCredentialStore(existing)
        val cursor = FakeCursorStore(cursor = 0L)
        val api = FakeApiClient().apply {
            pullError = FolicularApiException(401, "about:blank", "invalid token")
        }

        val result = runCatching { engine(repo, stateDao, creds, cursor, api).sync() }

        assertTrue(result.exceptionOrNull() is SyncAuthException)
        // The account code is the only recovery credential: it must survive a
        // revoked device token, and the engine must not silently register a
        // fresh account on the next pass.
        assertEquals(existing, creds.load())
    }

    @Test
    fun `biomarker push marks the local date-prefixed state clean`() = runTest {
        val date = "2026-08-15"
        val localId = SyncStateEntity.biomarkerEntityId(date)
        val wireId = fr.luteal.core.network.mapping.deterministicId("biomarker", date)
        val biomarkerDao = FakeBiomarkerDao().apply {
            upsert(
                fr.luteal.core.data.entity.BiomarkerObservationEntity(
                    date = date,
                    bbtCelsius = 36.55,
                    bbtTime = "07:00",
                    bbtQuality = "normal",
                    bbtDisturbancesJson = "[]",
                    cervicalSensation = null,
                    cervicalTexture = null,
                    lhTestResult = null,
                    hcgTestResult = null,
                    notes = "",
                    updatedAtEpochMillis = now.toInstant().toEpochMilli()
                )
            )
        }
        val stateDao = FakeSyncStateDao().apply {
            upsert(
                SyncStateEntity(
                    entityId = localId,
                    entityType = SyncStateEntity.TYPE_BIOMARKER_OBSERVATION,
                    clientRev = UUID.randomUUID().toString(),
                    createdAtEpochMillis = now.toInstant().toEpochMilli(),
                    updatedAtEpochMillis = now.toInstant().toEpochMilli(),
                    dirty = true
                )
            )
        }
        val creds = FakeCredentialStore(SyncCredentials("acct", "code", "ltok"))
        val api = FakeApiClient().apply {
            pushResult = PushResultWire(
                applied = listOf(AppliedChange(EntityType.BIOMARKER_OBSERVATION, wireId, 1L)),
                cursor = 1L
            )
        }
        engine(FakeCycleRepository(), stateDao, creds, FakeCursorStore(), api, biomarkerDao = biomarkerDao).sync()
        assertFalse(stateDao.getState(localId)!!.dirty)
    }

    @Test
    fun `push 401 surfaces terminal auth error and keeps credentials`() = runTest {
        val existing = SyncCredentials(
            accountId = registerResponse.account.id.toString(),
            accountCode = registerResponse.account.code,
            deviceToken = "ltok_stale"
        )
        val stateDao = FakeSyncStateDao().apply { upsert(dirtyState()) }
        val creds = FakeCredentialStore(existing)
        val api = FakeApiClient().apply {
            pushError = FolicularApiException(401, "unauthorized", "token rejected")
        }

        val thrown = runCatching {
            engine(FakeCycleRepository(listOf(localCycle())), stateDao, creds, FakeCursorStore(), api).sync()
        }.exceptionOrNull()

        assertTrue(thrown is SyncAuthException)
        // The account code — the only recovery credential — must survive.
        assertEquals(existing, creds.credentials)
        assertEquals(0, api.registerCalls)
    }

    @Test
    fun `pull 401 surfaces terminal auth error and keeps credentials`() = runTest {
        val existing = SyncCredentials(
            accountId = registerResponse.account.id.toString(),
            accountCode = registerResponse.account.code,
            deviceToken = "ltok_stale"
        )
        val creds = FakeCredentialStore(existing)
        val api = FakeApiClient().apply {
            pullError = FolicularApiException(401, "unauthorized", "token rejected")
        }

        val thrown = runCatching {
            engine(FakeCycleRepository(), FakeSyncStateDao(), creds, FakeCursorStore(), api).sync()
        }.exceptionOrNull()

        assertTrue(thrown is SyncAuthException)
        assertEquals(existing, creds.credentials)
    }

    @Test
    fun `edit landing mid-push keeps its new revision dirty instead of being cleaned`() = runTest {
        val repo = FakeCycleRepository(listOf(localCycle()))
        val stateDao = FakeSyncStateDao()
        val snapshot = dirtyState()
        stateDao.upsert(snapshot)
        val editedRev = UUID.randomUUID().toString()
        val creds = FakeCredentialStore(
            SyncCredentials(
                accountId = registerResponse.account.id.toString(),
                accountCode = registerResponse.account.code,
                deviceToken = registerResponse.device.token
            )
        )
        val api = FakeApiClient().apply {
            pushResult = PushResultWire(
                applied = listOf(AppliedChange(EntityType.CYCLE, cycleId, 1L)),
                cursor = 2L
            )
            // The user's edit lands while the request is in flight: fresh
            // clientRev, still dirty.
            onPush = { stateDao.upsert(snapshot.copy(clientRev = editedRev)) }
        }

        engine(repo, stateDao, creds, FakeCursorStore(), api).sync()

        val state = stateDao.states[cycleId.toString()]!!
        assertTrue(state.dirty)
        assertEquals(editedRev, state.clientRev)
    }

    @Test
    fun `re-created cycle during tombstone push keeps its newer dirty envelope`() = runTest {
        val stateDao = FakeSyncStateDao()
        val tombstone = dirtyState().copy(deletedAtEpochMillis = now.toInstant().toEpochMilli())
        stateDao.upsert(tombstone)
        val recreatedRev = UUID.randomUUID().toString()
        val repo = FakeCycleRepository(emptyList())
        val creds = FakeCredentialStore(
            SyncCredentials(
                accountId = registerResponse.account.id.toString(),
                accountCode = registerResponse.account.code,
                deviceToken = registerResponse.device.token
            )
        )
        val api = FakeApiClient().apply {
            pushResult = PushResultWire(
                applied = listOf(AppliedChange(EntityType.CYCLE, cycleId, 1L)),
                cursor = 2L
            )
            // The cycle is re-created mid-flight: live row + dirty envelope
            // with a fresh revision.
            onPush = {
                repo.cycles[cycleId.toString()] = localCycle()
                stateDao.upsert(tombstone.copy(clientRev = recreatedRev, deletedAtEpochMillis = null))
            }
        }

        engine(repo, stateDao, creds, FakeCursorStore(), api).sync()

        val state = stateDao.states[cycleId.toString()]!!
        assertTrue(state.dirty)
        assertNull(state.deletedAtEpochMillis)
        assertEquals(recreatedRev, state.clientRev)
    }
}
