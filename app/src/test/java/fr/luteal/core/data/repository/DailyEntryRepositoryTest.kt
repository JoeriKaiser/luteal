package fr.luteal.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.luteal.core.data.local.LutealDatabase
import fr.luteal.core.model.BleedingIntensity
import fr.luteal.core.model.DailyEntry
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.After
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
 * Regression tests for the daily-entry write paths. The delete path must
 * tombstone the sync envelope: without it, a locally deleted entry is
 * resurrected by the next server pull.
 */
@RunWith(RobolectricTestRunner::class)
class DailyEntryRepositoryTest {

    private val fixedInstant = Instant.parse("2026-08-13T12:00:00Z")
    private val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    private lateinit var database: LutealDatabase
    private lateinit var repo: DailyEntryRepositoryImpl

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LutealDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = DailyEntryRepositoryImpl(database, database.dailyEntryDao(), database.syncStateDao(), clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun entry(date: String) = DailyEntry(
        date = LocalDate.parse(date),
        bleedingIntensity = BleedingIntensity.MEDIUM,
        updatedAt = fixedInstant
    )

    @Test
    fun saveInsertsRowAndDirtyEnvelopeAtomically() = runTest {
        repo.save(entry("2026-08-10"))

        assertNotNull(database.dailyEntryDao().getEntryOnce("2026-08-10"))
        val state = database.syncStateDao().getState("2026-08-10")!!
        assertTrue(state.dirty)
        assertNull(state.deletedAtEpochMillis)
    }

    @Test
    fun deleteWritesTombstoneInsteadOfSilentRemoval() = runTest {
        val date = LocalDate.parse("2026-08-10")
        repo.save(entry(date.toString()))

        repo.delete(date)

        // Row gone...
        assertNull(database.dailyEntryDao().getEntryOnce(date.toString()))
        // ...but the envelope carries a deletion marker so the next push
        // propagates it and the pull does not resurrect the row.
        val tombstone = database.syncStateDao().getState(date.toString())!!
        assertEquals(fixedInstant.toEpochMilli(), tombstone.deletedAtEpochMillis)
        assertTrue(tombstone.dirty)
    }

    @Test
    fun saveAfterDeleteClearsTombstoneAndMarksDirty() = runTest {
        val date = LocalDate.parse("2026-08-10")
        repo.save(entry(date.toString()))
        repo.delete(date)

        repo.save(entry(date.toString()))

        assertNotNull(database.dailyEntryDao().getEntryOnce(date.toString()))
        val state = database.syncStateDao().getState(date.toString())!!
        assertNull(state.deletedAtEpochMillis)
        assertTrue(state.dirty)
        assertFalse(state.clientRev.isEmpty())
    }
}
