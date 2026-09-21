package fr.luteal.core.network.sync

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRevisionClockTest {

    @Test
    fun `two edits in one minute sort in order`() = runTest {
        val fixed = Clock.fixed(Instant.parse("2026-09-21T12:00:30Z"), ZoneOffset.UTC)
        val clock = SyncRevisionClock(MemoryRevisionWatermarkStore(), fixed)
        val first = clock.allocate()
        val second = clock.allocate()

        assertEquals(first.updatedAtEpochMillis, second.updatedAtEpochMillis)
        assertTrue(SyncRevisionClock.sortsAfter(second.clientRev, first.clientRev))
        assertTrue(first.pushable)
        assertTrue(second.pushable)
    }

    @Test
    fun `a higher remote revision in the same minute uses the next minute`() = runTest {
        val instant = Instant.parse("2026-09-21T12:00:30Z")
        val fixed = Clock.fixed(instant, ZoneOffset.UTC)
        val clock = SyncRevisionClock(MemoryRevisionWatermarkStore(), fixed)
        val ticket = clock.allocate(
            floorUpdatedAtEpochMillis = instant.toEpochMilli(),
            floorClientRev = "ffffffff-ffff-7fff-bfff-ffffffffffff"
        )

        assertTrue(ticket.updatedAtEpochMillis > SyncRevisionClock.truncateToMinute(instant.toEpochMilli()))
        assertTrue(ticket.pushable)
    }
}
