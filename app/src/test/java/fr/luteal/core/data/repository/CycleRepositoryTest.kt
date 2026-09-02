package fr.luteal.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.luteal.core.data.entity.DailyEntryEntity
import fr.luteal.core.data.local.LutealDatabase
import fr.luteal.core.model.BleedingIntensity
import fr.luteal.core.model.Cycle
import fr.luteal.core.model.CycleExclusionReason
import fr.luteal.core.model.PeriodDay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Real-Room tests for the cycle repository write paths. Entity and sync
 * envelope writes are transactional; these tests pin the envelope outcomes.
 */
@RunWith(RobolectricTestRunner::class)
class CycleRepositoryTest {

    private val fixedInstant = Instant.parse("2026-08-13T12:00:00Z")
    private val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    private lateinit var database: LutealDatabase
    private lateinit var repo: CycleRepositoryImpl

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LutealDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = CycleRepositoryImpl(database, database.cycleDao(), database.dailyEntryDao(), database.syncStateDao(), clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun saveCycleInsertsAndMarksDirty() = runTest {
        val cycle = Cycle(
            id = "c1",
            startDate = LocalDate.parse("2026-06-01"),
            endDate = LocalDate.parse("2026-06-28")
        )

        repo.saveCycle(cycle)

        assertNotNull(database.cycleDao().getCycleById("c1"))
        val state = database.syncStateDao().getState("c1")
        assertNotNull(state)
        assertTrue(state!!.dirty)
        assertNull(state.deletedAtEpochMillis)
        assertEquals(fixedInstant.toEpochMilli(), state.updatedAtEpochMillis)
    }

    @Test
    fun saveTwiceKeepsCreatedAtFromFirstEnvelope() = runTest {
        val first = Cycle(id = "c1", startDate = LocalDate.parse("2026-06-01"), endDate = null)
        repo.saveCycle(first)
        val original = database.syncStateDao().getState("c1")!!

        // A later edit lands after the fixed clock moved on: createdAt preserved.
        repo.saveCycle(first.copy(endDate = LocalDate.parse("2026-06-28")))

        val updated = database.syncStateDao().getState("c1")!!
        assertEquals(original.createdAtEpochMillis, updated.createdAtEpochMillis)
        assertTrue(updated.clientRev != original.clientRev)
    }

    @Test
    fun deleteCycleRemovesAndWritesSyncTombstone() = runTest {
        val cycle = Cycle(id = "c1", startDate = LocalDate.parse("2026-06-01"), endDate = null)
        repo.saveCycle(cycle)

        repo.deleteCycle("c1")

        assertNull(database.cycleDao().getCycleById("c1"))
        val tombstone = database.syncStateDao().getState("c1")
        assertNotNull(tombstone)
        assertEquals(fixedInstant.toEpochMilli(), tombstone!!.deletedAtEpochMillis)
        assertTrue(tombstone.dirty)
    }

    @Test
    fun updateCycleExclusionPersistsAndMarksDirty() = runTest {
        val cycle = Cycle(id = "cycle-1", startDate = LocalDate.parse("2026-06-01"), endDate = LocalDate.parse("2026-06-28"))
        repo.saveCycle(cycle)

        repo.updateCycleExclusion("cycle-1", true, CycleExclusionReason.ILLNESS)

        val updated = repo.getCyclesOnce().first()
        assertTrue(updated.isExcludedFromEstimates)
        val state = database.syncStateDao().getState("cycle-1")
        assertNotNull(state)
        assertTrue(state!!.dirty)
    }

    @Test
    fun getCyclesPopulatesPeriodDaysFromDailyEntriesAndFallsBack() = runTest {
        val fallbackDays = listOf(
            PeriodDay(LocalDate.parse("2026-06-01"), BleedingIntensity.SPOTTING)
        )
        val cycle = Cycle(
            id = "c1",
            startDate = LocalDate.parse("2026-06-01"),
            endDate = LocalDate.parse("2026-06-28"),
            periodDays = fallbackDays
        )
        repo.saveCycle(cycle)

        // When no daily entries exist, fallback to saved periodDays
        val initialCycles = repo.getCycles().first()
        assertEquals(1, initialCycles.size)
        assertEquals(fallbackDays, initialCycles.first().periodDays)

        // Insert daily entries: within range with bleeding, within range NONE, and outside range
        database.dailyEntryDao().upsert(
            DailyEntryEntity(
                date = "2026-06-01",
                bleedingIntensity = BleedingIntensity.HEAVY.name,
                painLevel = 1,
                moodLevel = 2,
                energyLevel = 3,
                symptomIdsJson = "[\"cramps\"]",
                notes = "Day 1",
                updatedAtEpochMillis = clock.millis()
            )
        )
        database.dailyEntryDao().upsert(
            DailyEntryEntity(
                date = "2026-06-02",
                bleedingIntensity = BleedingIntensity.LIGHT.name,
                painLevel = null,
                moodLevel = null,
                energyLevel = null,
                symptomIdsJson = "[]",
                notes = "",
                updatedAtEpochMillis = clock.millis()
            )
        )
        database.dailyEntryDao().upsert(
            DailyEntryEntity(
                date = "2026-06-03",
                bleedingIntensity = BleedingIntensity.NONE.name,
                painLevel = null,
                moodLevel = null,
                energyLevel = null,
                symptomIdsJson = "[]",
                notes = "",
                updatedAtEpochMillis = clock.millis()
            )
        )
        database.dailyEntryDao().upsert(
            DailyEntryEntity(
                date = "2026-07-01",
                bleedingIntensity = BleedingIntensity.MEDIUM.name,
                painLevel = null,
                moodLevel = null,
                energyLevel = null,
                symptomIdsJson = "[]",
                notes = "",
                updatedAtEpochMillis = clock.millis()
            )
        )

        val cyclesWithEntries = repo.getCycles().first()
        val periodDays = cyclesWithEntries.first().periodDays
        assertEquals(2, periodDays.size)
        assertEquals(LocalDate.parse("2026-06-01"), periodDays[0].date)
        assertEquals(BleedingIntensity.HEAVY, periodDays[0].bleedingIntensity)
        assertEquals("Day 1", periodDays[0].notes)
        assertEquals(listOf("cramps"), periodDays[0].symptomIds)

        assertEquals(LocalDate.parse("2026-06-02"), periodDays[1].date)
        assertEquals(BleedingIntensity.LIGHT, periodDays[1].bleedingIntensity)
    }

    @Test
    fun addBackfilledCycleReconcilesAdjacentCycles() = runTest {
        val c1 = Cycle(id = "c1", startDate = LocalDate.parse("2026-05-01"), endDate = null)
        repo.saveCycle(c1)

        repo.addBackfilledCycle(LocalDate.parse("2026-06-01"))

        val cycles = repo.getCyclesOnce().sortedBy { it.startDate }
        assertEquals(2, cycles.size)
        assertEquals(LocalDate.parse("2026-05-01"), cycles[0].startDate)
        assertEquals(LocalDate.parse("2026-05-31"), cycles[0].endDate)
        assertEquals(LocalDate.parse("2026-06-01"), cycles[1].startDate)
        assertNull(cycles[1].endDate)

        assertTrue(database.syncStateDao().getState("c1")!!.dirty)
        assertTrue(database.syncStateDao().getState(cycles[1].id)!!.dirty)
    }

    @Test
    fun editCycleStartDateReconcilesAdjacentCycles() = runTest {
        val c1 = Cycle(id = "c1", startDate = LocalDate.parse("2026-05-01"), endDate = LocalDate.parse("2026-05-31"))
        val c2 = Cycle(id = "c2", startDate = LocalDate.parse("2026-06-01"), endDate = null)
        repo.saveCycle(c1)
        repo.saveCycle(c2)

        repo.editCycleStartDate("c2", LocalDate.parse("2026-06-05"))

        val cycles = repo.getCyclesOnce().sortedBy { it.startDate }
        assertEquals(2, cycles.size)
        assertEquals(LocalDate.parse("2026-06-04"), cycles[0].endDate)
        assertEquals(LocalDate.parse("2026-06-05"), cycles[1].startDate)
        assertNull(cycles[1].endDate)

        assertTrue(database.syncStateDao().getState("c1")!!.dirty)
        assertTrue(database.syncStateDao().getState("c2")!!.dirty)
    }

    @Test
    fun deleteCycleAndReconcileReconcilesAdjacentCycles() = runTest {
        val c1 = Cycle(id = "c1", startDate = LocalDate.parse("2026-05-01"), endDate = LocalDate.parse("2026-05-31"))
        val c2 = Cycle(id = "c2", startDate = LocalDate.parse("2026-06-01"), endDate = LocalDate.parse("2026-06-30"))
        val c3 = Cycle(id = "c3", startDate = LocalDate.parse("2026-07-01"), endDate = null)
        repo.saveCycle(c1)
        repo.saveCycle(c2)
        repo.saveCycle(c3)

        repo.deleteCycleAndReconcile("c2")

        val cycles = repo.getCyclesOnce().sortedBy { it.startDate }
        assertEquals(2, cycles.size)
        assertEquals("c1", cycles[0].id)
        assertEquals(LocalDate.parse("2026-06-30"), cycles[0].endDate)
        assertEquals("c3", cycles[1].id)
        assertNull(cycles[1].endDate)

        val c2Tombstone = database.syncStateDao().getState("c2")
        assertNotNull(c2Tombstone)
        assertTrue(c2Tombstone!!.dirty)
        assertNotNull(c2Tombstone.deletedAtEpochMillis)

        assertTrue(database.syncStateDao().getState("c1")!!.dirty)
    }
}
