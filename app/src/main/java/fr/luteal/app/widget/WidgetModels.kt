package fr.luteal.app.widget

import fr.luteal.core.model.CycleEstimateResult
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

sealed interface PersonalWidgetSnapshot {
    data object OnboardingRequired : PersonalWidgetSnapshot

    data class NoCurrentCycle(
        val today: LocalDate,
        val estimateResult: CycleEstimateResult
    ) : PersonalWidgetSnapshot

    data class Available(
        val today: LocalDate,
        val cycleDay: Int,
        val recordedStart: LocalDate,
        val estimateResult: CycleEstimateResult,
        val hasTodayObservation: Boolean
    ) : PersonalWidgetSnapshot

    data object ReadFailure : PersonalWidgetSnapshot
}

sealed interface DuoWidgetSnapshot {
    data object SetupRequired : DuoWidgetSnapshot
    data object NoCachedProjection : DuoWidgetSnapshot
    data object NothingShared : DuoWidgetSnapshot
    data object KeyMissing : DuoWidgetSnapshot
    data object InvalidPayload : DuoWidgetSnapshot

    data class Available(
        val cycleDay: Int?,
        val estimateStart: LocalDate?,
        val estimateEnd: LocalDate?,
        val refreshedAt: Instant,
        val freshness: WidgetFreshness
    ) : DuoWidgetSnapshot

    data object ReadFailure : DuoWidgetSnapshot
}

enum class WidgetFreshness {
    CURRENT,
    AGING,
    STALE;

    companion object {
        fun of(refreshedAt: Instant, now: Instant = Instant.now()): WidgetFreshness {
            val age = Duration.between(refreshedAt, now)
            return when {
                age < Duration.ofHours(24) -> CURRENT
                age <= Duration.ofDays(7) -> AGING
                else -> STALE
            }
        }
    }
}
