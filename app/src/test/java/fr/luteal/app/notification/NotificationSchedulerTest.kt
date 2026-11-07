package fr.luteal.app.notification

import org.junit.Assert.assertEquals
import fr.luteal.core.model.PillRegimenConfig
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class NotificationSchedulerTest {

    @Test
    fun `when target time is in future and no entry today, schedules for today`() {
        val now = LocalDateTime.of(2026, 9, 30, 14, 0)
        val targetTime = LocalTime.of(20, 0)
        val target = NotificationScheduler.calculateDailyCheckInTarget(
            targetTime = targetTime,
            now = now,
            hasEntryToday = false
        )
        assertEquals(LocalDateTime.of(2026, 9, 30, 20, 0), target)
    }

    @Test
    fun `when target time is in past and no entry today, schedules for tomorrow`() {
        val now = LocalDateTime.of(2026, 9, 30, 21, 30)
        val targetTime = LocalTime.of(20, 0)
        val target = NotificationScheduler.calculateDailyCheckInTarget(
            targetTime = targetTime,
            now = now,
            hasEntryToday = false
        )
        assertEquals(LocalDateTime.of(2026, 10, 1, 20, 0), target)
    }

    @Test
    fun `when has entry today, schedules for tomorrow even if target time is in future`() {
        val now = LocalDateTime.of(2026, 9, 30, 14, 0)
        val targetTime = LocalTime.of(20, 0)
        val target = NotificationScheduler.calculateDailyCheckInTarget(
            targetTime = targetTime,
            now = now,
            hasEntryToday = true
        )
        assertEquals(LocalDateTime.of(2026, 10, 1, 20, 0), target)
    }

    @Test
    fun `when target time exactly equals now, schedules for today`() {
        val now = LocalDateTime.of(2026, 9, 30, 20, 0)
        val targetTime = LocalTime.of(20, 0)
        val target = NotificationScheduler.calculateDailyCheckInTarget(
            targetTime = targetTime,
            now = now,
            hasEntryToday = false
        )
        assertEquals(LocalDateTime.of(2026, 9, 30, 20, 0), target)
    }

    @Test
    fun `period window target subtracts lead days at 9am`() {
        val earliestDate = LocalDate.of(2026, 10, 10)
        val leadDays = 3
        val target = NotificationScheduler.calculatePeriodWindowTarget(
            earliestDate = earliestDate,
            leadDays = leadDays
        )
        assertEquals(LocalDateTime.of(2026, 10, 7, 9, 0), target)
    }

    @Test
    fun `late cycle target adds grace days at 10am`() {
        val latestDate = LocalDate.of(2026, 10, 15)
        val graceDays = 4
        val target = NotificationScheduler.calculateLateCycleTarget(
            latestDate = latestDate,
            graceDays = graceDays
        )
        assertEquals(LocalDateTime.of(2026, 10, 19, 10, 0), target)
    }

    @Test
    fun `calculatePillReminderTarget returns today when before reminder time and not taken`() {
        val startDate = LocalDate.of(2026, 10, 1)
        val config = PillRegimenConfig(
            isEnabled = true,
            activeDays = 21,
            pauseDays = 7,
            startDate = startDate,
            reminderTime = "20:30"
        )
        val now = LocalDateTime.of(2026, 10, 3, 15, 0)
        val target = NotificationScheduler.calculatePillReminderTarget(config, now)
        assertEquals(LocalDateTime.of(2026, 10, 3, 20, 30), target)
    }

    @Test
    fun `calculatePillReminderTarget returns tomorrow when already taken today`() {
        val startDate = LocalDate.of(2026, 10, 1)
        val config = PillRegimenConfig(
            isEnabled = true,
            activeDays = 21,
            pauseDays = 7,
            startDate = startDate,
            reminderTime = "20:30",
            lastTakenDate = LocalDate.of(2026, 10, 3)
        )
        val now = LocalDateTime.of(2026, 10, 3, 15, 0)
        val target = NotificationScheduler.calculatePillReminderTarget(config, now)
        assertEquals(LocalDateTime.of(2026, 10, 4, 20, 30), target)
    }

    @Test
    fun `calculatePillReminderTarget skips pause days to land on day 1 of next pack`() {
        val startDate = LocalDate.of(2026, 10, 1)
        val config = PillRegimenConfig(
            isEnabled = true,
            activeDays = 21,
            pauseDays = 7,
            startDate = startDate,
            reminderTime = "20:00"
        )
        // Day 22 is pause day 1 (2026-10-22)
        val now = LocalDateTime.of(2026, 10, 22, 10, 0)
        val target = NotificationScheduler.calculatePillReminderTarget(config, now)
        // Day 29 is 2026-10-29
        assertEquals(LocalDateTime.of(2026, 10, 29, 20, 0), target)
    }

    @Test
    fun `calculatePillReminderTarget returns null when pill tracking is disabled`() {
        val config = PillRegimenConfig(
            isEnabled = false,
            startDate = LocalDate.of(2026, 10, 1)
        )
        val now = LocalDateTime.of(2026, 10, 3, 15, 0)
        assertNull(NotificationScheduler.calculatePillReminderTarget(config, now))
    }
}
