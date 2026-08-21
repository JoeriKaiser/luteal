package fr.luteal.app

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Mutable [Clock] so the ticker's date derivation can be driven manually. */
private class FakeClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = now
}

/**
 * Regression tests for the midnight ticker behind LutealUiState.today: a
 * process kept alive overnight must observe the date rollover instead of
 * serving yesterday as today (which misdated new entries).
 */
class TodayFlowTest {

    @Test
    fun `emits today immediately then the next day at local midnight`() = runTest {
        val zone = ZoneId.systemDefault()
        val clock = FakeClock(
            LocalDate.of(2026, 8, 20).atTime(23, 58).atZone(zone).toInstant()
        )
        val emitted = mutableListOf<LocalDate>()

        val job = launch { todayFlow(clock).take(2).toCollection(emitted) }

        runCurrent() // first emission: Aug 20
        assertEquals(listOf(LocalDate.of(2026, 8, 20)), emitted)

        // Wall time crosses midnight while the process stays alive.
        clock.now = LocalDate.of(2026, 8, 21).atStartOfDay(zone).toInstant().plusSeconds(30)
        advanceTimeBy(Duration.ofMinutes(2).toMillis() + 1_000)
        runCurrent()

        assertEquals(
            listOf(LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 21)),
            emitted
        )
        job.join() // take(2) completed the collection
    }

    @Test
    fun `consecutive same-day emissions are deduplicated`() = runTest {
        val zone = ZoneId.systemDefault()
        val clock = FakeClock(
            LocalDate.of(2026, 8, 20).atTime(10, 0).atZone(zone).toInstant()
        )
        val emitted = mutableListOf<LocalDate>()

        val job = launch { todayFlow(clock).take(3).toCollection(emitted) }

        // Two delay periods elapse without the wall clock moving: the ticker
        // recomputes the same date and must not emit it twice.
        advanceTimeBy(Duration.ofHours(14).toMillis())
        runCurrent()

        assertEquals(listOf(LocalDate.of(2026, 8, 20)), emitted)
        job.cancel()
    }
}
