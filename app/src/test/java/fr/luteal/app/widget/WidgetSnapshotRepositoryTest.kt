package fr.luteal.app.widget

import fr.luteal.core.data.datastore.UserPreferences
import fr.luteal.core.data.entity.DuoWidgetCacheEntity
import fr.luteal.core.data.local.DuoWidgetCacheDao
import fr.luteal.core.data.repository.BiomarkerRepository
import fr.luteal.core.data.repository.CycleRepository
import fr.luteal.core.data.repository.DailyEntryRepository
import fr.luteal.core.data.repository.DuoRepository
import fr.luteal.core.data.repository.DuoWidgetCacheRepository
import fr.luteal.core.data.repository.UserRepository
import fr.luteal.core.model.BasalBodyTemperature
import fr.luteal.core.model.BiomarkerObservation
import fr.luteal.core.model.Cycle
import fr.luteal.core.model.CycleExclusionReason
import fr.luteal.core.model.DailyEntry
import fr.luteal.core.model.DuoSharingField
import fr.luteal.core.model.SyncMode
import fr.luteal.core.model.UserProfile
import fr.luteal.core.model.UserRole
import fr.luteal.core.network.sync.FakeApiClient
import fr.luteal.core.network.sync.FakeCredentialStore
import fr.luteal.core.network.sync.FakeCursorStore
import fr.luteal.core.network.sync.FolicularApiClientFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class WidgetSnapshotRepositoryTest {

    private val fixedDate = LocalDate.parse("2026-08-20")
    private val clock = Clock.fixed(fixedDate.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault())
    private val readyPreferences = UserPreferences(hasCompletedOnboarding = true)

    private class TestUserRepository(private val prefs: UserPreferences) : UserRepository {
        override fun getUserProfile(): Flow<UserProfile?> = flowOf(null)
        override fun getUserPreferences(): Flow<UserPreferences> = flowOf(prefs)
        override suspend fun updateSyncMode(mode: SyncMode) {}
        override suspend fun updateUserRole(role: UserRole) {}
        override suspend fun updateDuoSharing(field: DuoSharingField, enabled: Boolean) {}
        override suspend fun setCouplePairing(pairingCode: String?, partnerName: String?) {}
        override suspend fun completeOnboarding(role: UserRole, disorderTracking: Map<String, Boolean>, ageBandId: String?) {}
        override suspend fun setTrackingContext(contextId: String, enabled: Boolean) {}
        override suspend fun setAgeBand(ageBandId: String?) {}
        override suspend fun setPillLastTakenDate(date: String?) {}
    }

    private class TestCycleRepository(private val cycles: List<Cycle>) : CycleRepository {
        override fun getCycles(): Flow<List<Cycle>> = flowOf(cycles)
        override fun getCurrentCycle(): Flow<Cycle?> = flowOf(cycles.firstOrNull { it.endDate == null })
        override suspend fun getCyclesOnce(): List<Cycle> = cycles
        override suspend fun saveCycle(cycle: Cycle) {}
        override suspend fun upsertCycle(cycle: Cycle) {}
        override suspend fun deleteCycle(id: String) {}
        override suspend fun updateCycleExclusion(id: String, isExcluded: Boolean, reason: CycleExclusionReason?) {}
        override suspend fun addBackfilledCycle(startDate: LocalDate) {}
        override suspend fun editCycleStartDate(cycleId: String, newStartDate: LocalDate) {}
        override suspend fun deleteCycleAndReconcile(cycleId: String) {}
    }

    private class TestDailyEntryRepository(private val entries: Map<LocalDate, DailyEntry> = emptyMap()) : DailyEntryRepository {
        override fun observeEntries(): Flow<List<DailyEntry>> = flowOf(entries.values.toList())
        override fun observeEntry(date: LocalDate): Flow<DailyEntry?> = flowOf(entries[date])
        override suspend fun getEntryOnce(date: LocalDate): DailyEntry? = entries[date]
        override suspend fun save(entry: DailyEntry) {}
        override suspend fun delete(date: LocalDate) {}
    }

    private class TestBiomarkerRepository(private val observations: Map<LocalDate, BiomarkerObservation> = emptyMap()) : BiomarkerRepository {
        override fun observeObservations(): Flow<List<BiomarkerObservation>> = flowOf(observations.values.toList())
        override fun observeObservation(date: LocalDate): Flow<BiomarkerObservation?> = flowOf(observations[date])
        override suspend fun getObservationOnce(date: LocalDate): BiomarkerObservation? = observations[date]
        override suspend fun save(observation: BiomarkerObservation) {}
        override suspend fun delete(date: LocalDate) {}
    }

    private class FakeDuoWidgetCacheDao : DuoWidgetCacheDao {
        var entity: DuoWidgetCacheEntity? = null
        override fun observeLatest(): Flow<DuoWidgetCacheEntity?> = flowOf(entity)
        override suspend fun getLatest(): DuoWidgetCacheEntity? = entity
        override suspend fun upsert(entity: DuoWidgetCacheEntity) { this.entity = entity }
        override suspend fun clear() { entity = null }
    }

    private fun createRepository(
        dailyEntries: Map<LocalDate, DailyEntry> = emptyMap(),
        biomarkers: Map<LocalDate, BiomarkerObservation> = emptyMap(),
        cycles: List<Cycle> = listOf(Cycle("c1", startDate = fixedDate.minusDays(5)))
    ): WidgetSnapshotRepository {
        val credStore = FakeCredentialStore()
        val cursorStore = FakeCursorStore()
        val apiClientFactory = FolicularApiClientFactory { FakeApiClient() }
        val duoRepo = DuoRepository(credStore, apiClientFactory, cursorStore)
        val cacheRepo = DuoWidgetCacheRepository(FakeDuoWidgetCacheDao())

        return WidgetSnapshotRepository(
            cycleRepository = TestCycleRepository(cycles),
            dailyEntryRepository = TestDailyEntryRepository(dailyEntries),
            biomarkerRepository = TestBiomarkerRepository(biomarkers),
            userRepository = TestUserRepository(readyPreferences),
            duoRepository = duoRepo,
            duoCacheRepository = cacheRepo,
            factory = WidgetSnapshotFactory(),
            clock = clock
        )
    }

    @Test
    fun `personal snapshot reports hasTodayObservation when only biomarker is recorded`() = runTest {
        val biomarker = BiomarkerObservation(
            date = fixedDate,
            bbt = BasalBodyTemperature(valueCelsius = 36.6)
        )

        val repository = createRepository(biomarkers = mapOf(fixedDate to biomarker))
        val snapshot = repository.personal()

        assertTrue(snapshot is PersonalWidgetSnapshot.Available)
        assertEquals(true, (snapshot as PersonalWidgetSnapshot.Available).hasTodayObservation)
    }

    @Test
    fun `personal snapshot reports hasTodayObservation false when no observations recorded`() = runTest {
        val repository = createRepository()
        val snapshot = repository.personal()

        assertTrue(snapshot is PersonalWidgetSnapshot.Available)
        assertEquals(false, (snapshot as PersonalWidgetSnapshot.Available).hasTodayObservation)
    }

    @Test
    fun `personal snapshot reports hasTodayObservation when daily entry is recorded`() = runTest {
        val entry = DailyEntry(date = fixedDate, painLevel = 2)
        val repository = createRepository(dailyEntries = mapOf(fixedDate to entry))
        val snapshot = repository.personal()

        assertTrue(snapshot is PersonalWidgetSnapshot.Available)
        assertEquals(true, (snapshot as PersonalWidgetSnapshot.Available).hasTodayObservation)
    }
}
