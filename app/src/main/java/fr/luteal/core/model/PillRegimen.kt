package fr.luteal.core.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

data class PillRegimenConfig(
    val isEnabled: Boolean = false,
    val activeDays: Int = 21,
    val pauseDays: Int = 7,
    val startDate: LocalDate? = null,
    val reminderTime: String = "20:00",
    val lastTakenDate: LocalDate? = null
)

sealed interface PillDayStatus {
    data object Disabled : PillDayStatus
    data class ActiveDay(
        val dayOfPack: Int,
        val activeDays: Int,
        val pauseDays: Int,
        val isTakenToday: Boolean
    ) : PillDayStatus
    data class PauseDay(
        val dayOfPause: Int,
        val pauseDays: Int,
        val activeDays: Int
    ) : PillDayStatus
}

object PillRegimenCalculator {
    const val MIN_ACTIVE_DAYS = 1
    const val MAX_ACTIVE_DAYS = 90
    const val MIN_PAUSE_DAYS = 0
    const val MAX_PAUSE_DAYS = 30

    fun evaluate(config: PillRegimenConfig, today: LocalDate): PillDayStatus {
        if (!config.isEnabled || config.startDate == null || today.isBefore(config.startDate)) {
            return PillDayStatus.Disabled
        }
        val active = config.activeDays.coerceIn(MIN_ACTIVE_DAYS, MAX_ACTIVE_DAYS)
        val pause = config.pauseDays.coerceIn(MIN_PAUSE_DAYS, MAX_PAUSE_DAYS)
        val cycleLength = active + pause
        if (cycleLength <= 0) {
            return PillDayStatus.Disabled
        }
        val daysSinceStart = ChronoUnit.DAYS.between(config.startDate, today)
        val indexInPack = Math.floorMod(daysSinceStart, cycleLength.toLong()).toInt()

        return if (indexInPack < active) {
            PillDayStatus.ActiveDay(
                dayOfPack = indexInPack + 1,
                activeDays = active,
                pauseDays = pause,
                isTakenToday = config.lastTakenDate == today
            )
        } else {
            PillDayStatus.PauseDay(
                dayOfPause = (indexInPack - active) + 1,
                pauseDays = pause,
                activeDays = active
            )
        }
    }

    fun nextReminderDateTime(config: PillRegimenConfig, now: LocalDateTime): LocalDateTime? {
        if (!config.isEnabled || config.startDate == null) {
            return null
        }
        val targetTime = runCatching { LocalTime.parse(config.reminderTime) }
            .getOrDefault(LocalTime.of(20, 0))
        val today = now.toLocalDate()

        if (today.isBefore(config.startDate)) {
            return config.startDate.atTime(targetTime)
        }

        val active = config.activeDays.coerceIn(MIN_ACTIVE_DAYS, MAX_ACTIVE_DAYS)
        val pause = config.pauseDays.coerceIn(MIN_PAUSE_DAYS, MAX_PAUSE_DAYS)
        val maxOffset = active + pause + 1

        for (offset in 0..maxOffset) {
            val candidateDate = today.plusDays(offset.toLong())
            val status = evaluate(config, candidateDate)
            if (status is PillDayStatus.ActiveDay) {
                if (offset == 0) {
                    if (!status.isTakenToday && !now.toLocalTime().isAfter(targetTime)) {
                        return candidateDate.atTime(targetTime)
                    }
                } else {
                    return candidateDate.atTime(targetTime)
                }
            }
        }
        return null
    }
}
