package fr.luteal.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.luteal.core.data.local.LutealDatabase
import fr.luteal.core.model.Cycle
import fr.luteal.core.model.CycleExclusionReason
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
        repo = CycleRepositoryImpl(database, database.cycleDao(), database.syncStateDao(), clock)
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
}
