package fr.luteal.app.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class WidgetFreshnessTest {
    @Test
    fun staleAfterEightDays() {
        val now = Instant.parse("2026-08-19T12:00:00Z")
        val stamp = now.minus(8, ChronoUnit.DAYS)
        assertEquals(WidgetFreshness.STALE, WidgetFreshness.of(stamp, now))
    }

    @Test
    fun currentWithinADay() {
        val now = Instant.parse("2026-08-19T12:00:00Z")
        assertEquals(WidgetFreshness.CURRENT, WidgetFreshness.of(now.minus(2, ChronoUnit.HOURS), now))
    }
}
