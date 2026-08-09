package fr.luteal.app.widget

import fr.luteal.core.data.datastore.UserPreferences
import fr.luteal.core.model.CachedDuoCycleProjection
import fr.luteal.core.model.Cycle
import fr.luteal.core.model.DuoCycleProjectionStatus
import fr.luteal.core.model.CycleEstimateResult
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetSnapshotFactoryTest {
    private val factory = WidgetSnapshotFactory()
    private val readyPreferences = UserPreferences(hasCompletedOnboarding = true)
    private val today = LocalDate.parse("2026-07-20")

    @Test
    fun `personal widget waits for onboarding`() {
        val result = factory.personal(
            cycles = emptyList(),
            preferences = UserPreferences(hasCompletedOnboarding = false),
            hasTodayObservation = false,
            today = today
        )

        assertEquals(PersonalWidgetSnapshot.OnboardingRequired, result)
    }

    @Test
    fun `personal widget derives day only from an open recorded cycle`() {
        val cycles = listOf(
            Cycle("old", LocalDate.parse("2026-06-20"), LocalDate.parse("2026-07-18")),
            Cycle("current", LocalDate.parse("2026-07-19"))
        )

        val snapshot = factory.personal(cycles, readyPreferences, true, today)
        assertTrue(snapshot is PersonalWidgetSnapshot.Available)
        val result = snapshot as PersonalWidgetSnapshot.Available

        assertEquals(2, result.cycleDay)
        assertEquals(LocalDate.parse("2026-07-19"), result.recordedStart)
        assertEquals(true, result.hasTodayObservation)
    }

    @Test
    fun `future open cycle is not presented as current`() {
        val result = factory.personal(
            cycles = listOf(Cycle("future", today.plusDays(1))),
            preferences = readyPreferences,
            hasTodayObservation = false,
            today = today
        )

        assertTrue(result is PersonalWidgetSnapshot.NoCurrentCycle)
    }

    @Test
    fun `personal estimate uses the domain calculator result`() {
        val cycles = listOf(
            Cycle("1", LocalDate.parse("2026-05-20"), LocalDate.parse("2026-06-18")),
            Cycle("2", LocalDate.parse("2026-06-19"), LocalDate.parse("2026-07-18")),
            Cycle("3", LocalDate.parse("2026-07-19"))
        )

        val snapshot = factory.personal(cycles, readyPreferences, false, today)
        assertTrue(snapshot is PersonalWidgetSnapshot.Available)
        val result = snapshot as PersonalWidgetSnapshot.Available

        assertTrue(result.estimateResult is CycleEstimateResult.Available)
    }

    @Test
    fun `duo grants remove fields even when cache contains values`() {
        val cached = cachedProjection(
            cycleDay = 9,
            cycleDayGranted = false,
            estimateGranted = true
        )

        val snapshot = factory.duo(true, cached, Instant.parse("2026-07-20T12:00:00Z"))
        assertTrue(snapshot is DuoWidgetSnapshot.Available)
        val result = snapshot as DuoWidgetSnapshot.Available

        assertNull(result.cycleDay)
        assertEquals(LocalDate.parse("2026-07-25"), result.estimateStart)
    }

    @Test
    fun `duo cache older than seven days is stale`() {
        val snapshot = factory.duo(
            hasAccount = true,
            cached = cachedProjection(refreshedAt = Instant.parse("2026-07-10T00:00:00Z")),
            now = Instant.parse("2026-07-20T00:00:01Z")
        )
        assertTrue(snapshot is DuoWidgetSnapshot.Available)
        val result = snapshot as DuoWidgetSnapshot.Available

        assertEquals(WidgetFreshness.STALE, result.freshness)
    }

    @Test
    fun `duo without granted cycle fields shows nothing shared`() {
        val result = factory.duo(
            hasAccount = true,
            cached = cachedProjection(cycleDayGranted = false, estimateGranted = false),
            now = Instant.parse("2026-07-20T00:00:00Z")
        )

        assertEquals(DuoWidgetSnapshot.NothingShared, result)
    }

    private fun cachedProjection(
        cycleDay: Int? = 9,
        cycleDayGranted: Boolean = true,
        estimateGranted: Boolean = true,
        refreshedAt: Instant = Instant.parse("2026-07-20T00:00:00Z")
    ) = CachedDuoCycleProjection(
        linkId = "link",
        role = "PARTNER",
        cycleDay = cycleDay,
        estimateStart = LocalDate.parse("2026-07-25"),
        estimateEnd = LocalDate.parse("2026-07-31"),
        cycleDayGranted = cycleDayGranted,
        estimateGranted = estimateGranted,
        status = DuoCycleProjectionStatus.ACTIVE,
        refreshedAt = refreshedAt
    )
}
