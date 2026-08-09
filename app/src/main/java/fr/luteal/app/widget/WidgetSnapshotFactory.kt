package fr.luteal.app.widget

import fr.luteal.core.data.datastore.UserPreferences
import fr.luteal.core.model.AgeBand
import fr.luteal.core.model.CachedDuoCycleProjection
import fr.luteal.core.model.Cycle
import fr.luteal.core.model.CycleEstimateCalculator
import fr.luteal.core.model.DuoCycleProjectionStatus
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** Pure mapping from domain data to the deliberately small widget surface. */
class WidgetSnapshotFactory @Inject constructor() {
    fun personal(
        cycles: List<Cycle>,
        preferences: UserPreferences,
        hasTodayObservation: Boolean,
        today: LocalDate
    ): PersonalWidgetSnapshot {
        if (!preferences.hasCompletedOnboarding) {
            return PersonalWidgetSnapshot.OnboardingRequired
        }

        val estimateResult = CycleEstimateCalculator.evaluate(
            cycles = cycles,
            ageBand = AgeBand.fromId(preferences.ageBand),
            hasTimingContext = preferences.hasTimingContext
        )
        val currentCycle = cycles
            .asSequence()
            .filter { it.endDate == null && !it.startDate.isAfter(today) }
            .maxByOrNull(Cycle::startDate)
            ?: return PersonalWidgetSnapshot.NoCurrentCycle(today, estimateResult)

        return PersonalWidgetSnapshot.Available(
            today = today,
            cycleDay = ChronoUnit.DAYS.between(currentCycle.startDate, today).toInt() + 1,
            recordedStart = currentCycle.startDate,
            estimateResult = estimateResult,
            hasTodayObservation = hasTodayObservation
        )
    }

    fun duo(
        hasAccount: Boolean,
        cached: CachedDuoCycleProjection?,
        now: Instant
    ): DuoWidgetSnapshot {
        if (!hasAccount) return DuoWidgetSnapshot.SetupRequired
        cached ?: return DuoWidgetSnapshot.NoCachedProjection

        return when (cached.status) {
            DuoCycleProjectionStatus.NO_PAYLOAD -> DuoWidgetSnapshot.NoCachedProjection
            DuoCycleProjectionStatus.KEY_MISSING -> DuoWidgetSnapshot.KeyMissing
            DuoCycleProjectionStatus.INVALID_PAYLOAD -> DuoWidgetSnapshot.InvalidPayload
            DuoCycleProjectionStatus.ACTIVE -> {
                val cycleDay = cached.cycleDay.takeIf { cached.cycleDayGranted }
                val estimateStart = cached.estimateStart.takeIf { cached.estimateGranted }
                val estimateEnd = cached.estimateEnd.takeIf { cached.estimateGranted }
                if (cycleDay == null && (estimateStart == null || estimateEnd == null)) {
                    DuoWidgetSnapshot.NothingShared
                } else {
                    val age = Duration.between(cached.refreshedAt, now)
                        .coerceAtLeast(Duration.ZERO)
                    val freshness = when {
                        age < Duration.ofHours(24) -> WidgetFreshness.CURRENT
                        age <= Duration.ofDays(7) -> WidgetFreshness.AGING
                        else -> WidgetFreshness.STALE
                    }
                    DuoWidgetSnapshot.Available(
                        cycleDay = cycleDay,
                        estimateStart = estimateStart,
                        estimateEnd = estimateEnd,
                        refreshedAt = cached.refreshedAt,
                        freshness = freshness
                    )
                }
            }
        }
    }
}
