package fr.luteal.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class PillRegimenCalculatorTest {

    private val startDate = LocalDate.of(2026, 10, 1)

    @Test
    fun `pack calculation 21 7 across day 1, 21, 22 pause day 1, 28 pause day 7, and 29 pack 2 active day 1`() {
        val config = PillRegimenConfig(
            isEnabled = true,
            activeDays = 21,
            pauseDays = 7,
            startDate = startDate
        )

        // Day 1: active day 1 of pack
        val day1 = PillRegimenCalculator.evaluate(config, startDate)
        assertTrue(day1 is PillDayStatus.ActiveDay)
        val active1 = day1 as PillDayStatus.ActiveDay
        assertEquals(1, active1.dayOfPack)
        assertEquals(21, active1.activeDays)
        assertEquals(7, active1.pauseDays)
        assertFalse(active1.isTakenToday)

        // Day 21: active day 21 of pack
        val day21Date = startDate.plusDays(20)
        val day21 = PillRegimenCalculator.evaluate(config, day21Date)
        assertTrue(day21 is PillDayStatus.ActiveDay)
        val active21 = day21 as PillDayStatus.ActiveDay
        assertEquals(21, active21.dayOfPack)
        assertEquals(21, active21.activeDays)
        assertEquals(7, active21.pauseDays)

        // Day 22: pause day 1
        val day22Date = startDate.plusDays(21)
        val day22 = PillRegimenCalculator.evaluate(config, day22Date)
        assertTrue(day22 is PillDayStatus.PauseDay)
        val pause1 = day22 as PillDayStatus.PauseDay
        assertEquals(1, pause1.dayOfPause)
        assertEquals(7, pause1.pauseDays)
        assertEquals(21, pause1.activeDays)

        // Day 28: pause day 7
        val day28Date = startDate.plusDays(27)
        val day28 = PillRegimenCalculator.evaluate(config, day28Date)
        assertTrue(day28 is PillDayStatus.PauseDay)
        val pause7 = day28 as PillDayStatus.PauseDay
        assertEquals(7, pause7.dayOfPause)
        assertEquals(7, pause7.pauseDays)
        assertEquals(21, pause7.activeDays)

        // Day 29: pack 2, active day 1
        val day29Date = startDate.plusDays(28)
        val day29 = PillRegimenCalculator.evaluate(config, day29Date)
        assertTrue(day29 is PillDayStatus.ActiveDay)
        val active29 = day29 as PillDayStatus.ActiveDay
        assertEquals(1, active29.dayOfPack)
        assertEquals(21, active29.activeDays)
        assertEquals(7, active29.pauseDays)
    }

    @Test
    fun `continuous 28 0 regimen never enters PauseDay`() {
        val config = PillRegimenConfig(
            isEnabled = true,
            activeDays = 28,
            pauseDays = 0,
            startDate = startDate
        )

        for (dayOffset in 0 until 56) {
            val date = startDate.plusDays(dayOffset.toLong())
            val status = PillRegimenCalculator.evaluate(config, date)
            assertTrue("Expected ActiveDay on offset $dayOffset but was $status", status is PillDayStatus.ActiveDay)
            val active = status as PillDayStatus.ActiveDay
            val expectedDayOfPack = (dayOffset % 28) + 1
            assertEquals(expectedDayOfPack, active.dayOfPack)
            assertEquals(28, active.activeDays)
            assertEquals(0, active.pauseDays)
        }
    }

    @Test
    fun `isTakenToday is true when lastTakenDate is today and false when yesterday or null`() {
        val today = startDate.plusDays(5)

        val configNotTaken = PillRegimenConfig(
            isEnabled = true,
            activeDays = 21,
            pauseDays = 7,
            startDate = startDate,
            lastTakenDate = null
        )
        val statusNotTaken = PillRegimenCalculator.evaluate(configNotTaken, today) as PillDayStatus.ActiveDay
        assertFalse(statusNotTaken.isTakenToday)

        val configYesterday = PillRegimenConfig(
            isEnabled = true,
            activeDays = 21,
            pauseDays = 7,
            startDate = startDate,
            lastTakenDate = today.minusDays(1)
        )
        val statusYesterday = PillRegimenCalculator.evaluate(configYesterday, today) as PillDayStatus.ActiveDay
        assertFalse(statusYesterday.isTakenToday)

        val configToday = PillRegimenConfig(
            isEnabled = true,
            activeDays = 21,
            pauseDays = 7,
            startDate = startDate,
            lastTakenDate = today
        )
        val statusToday = PillRegimenCalculator.evaluate(configToday, today) as PillDayStatus.ActiveDay
        assertTrue(statusToday.isTakenToday)
    }

    @Test
    fun `nextReminderDateTime schedules today when before reminder time and not taken`() {
        val config = PillRegimenConfig(
            isEnabled = true,
            activeDays = 21,
            pauseDays = 7,
            startDate = startDate,
            reminderTime = "20:00",
            lastTakenDate = null
        )
        val now = LocalDateTime.of(2026, 10, 5, 14, 0)
        val reminder = PillRegimenCalculator.nextReminderDateTime(config, now)
        assertEquals(LocalDateTime.of(2026, 10, 5, 20, 0), reminder)
    }

    @Test
    fun `nextReminderDateTime schedules tomorrow when already taken today`() {
        val today = LocalDate.of(2026, 10, 5)
        val config = PillRegimenConfig(
            isEnabled = true,
            activeDays = 21,
            pauseDays = 7,
            startDate = startDate,
            reminderTime = "20:00",
            lastTakenDate = today
        )
        val now = LocalDateTime.of(2026, 10, 5, 14, 0)
        val reminder = PillRegimenCalculator.nextReminderDateTime(config, now)
        assertEquals(LocalDateTime.of(2026, 10, 6, 20, 0), reminder)
    }

    @Test
    fun `nextReminderDateTime schedules tomorrow when after reminder time and not taken`() {
        val config = PillRegimenConfig(
            isEnabled = true,
            activeDays = 21,
            pauseDays = 7,
            startDate = startDate,
            reminderTime = "20:00",
            lastTakenDate = null
        )
        val now = LocalDateTime.of(2026, 10, 5, 21, 15)
        val reminder = PillRegimenCalculator.nextReminderDateTime(config, now)
        assertEquals(LocalDateTime.of(2026, 10, 6, 20, 0), reminder)
    }

    @Test
    fun `nextReminderDateTime skips all 7 pause days to land on day 1 of the next pack`() {
        val config = PillRegimenConfig(
            isEnabled = true,
            activeDays = 21,
            pauseDays = 7,
            startDate = startDate,
            reminderTime = "20:00"
        )
        // Day 22 is pause day 1 (2026-10-22)
        val nowOnPauseDay1 = LocalDateTime.of(2026, 10, 22, 9, 0)
        val reminderFromPause1 = PillRegimenCalculator.nextReminderDateTime(config, nowOnPauseDay1)
        // Day 29 is 2026-10-29
        assertEquals(LocalDateTime.of(2026, 10, 29, 20, 0), reminderFromPause1)

        // Also test from day 21 when taken on day 21
        val configTakenOnDay21 = config.copy(lastTakenDate = LocalDate.of(2026, 10, 21))
        val nowOnDay21 = LocalDateTime.of(2026, 10, 21, 15, 0)
        val reminderFromDay21 = PillRegimenCalculator.nextReminderDateTime(configTakenOnDay21, nowOnDay21)
        assertEquals(LocalDateTime.of(2026, 10, 29, 20, 0), reminderFromDay21)
    }

    @Test
    fun `disabled config or missing start date yields Disabled status and null reminder`() {
        val disabledConfig = PillRegimenConfig(
            isEnabled = false,
            startDate = startDate
        )
        assertEquals(PillDayStatus.Disabled, PillRegimenCalculator.evaluate(disabledConfig, startDate))
        assertNull(PillRegimenCalculator.nextReminderDateTime(disabledConfig, startDate.atTime(10, 0)))

        val noStartDateConfig = PillRegimenConfig(
            isEnabled = true,
            startDate = null
        )
        assertEquals(PillDayStatus.Disabled, PillRegimenCalculator.evaluate(noStartDateConfig, startDate))
        assertNull(PillRegimenCalculator.nextReminderDateTime(noStartDateConfig, startDate.atTime(10, 0)))
    }

    @Test
    fun `dates before pack start date yield Disabled status and reminder starts on start date`() {
        val config = PillRegimenConfig(
            isEnabled = true,
            activeDays = 21,
            pauseDays = 7,
            startDate = startDate,
            reminderTime = "20:00"
        )
        val beforeDate = startDate.minusDays(3)
        assertEquals(PillDayStatus.Disabled, PillRegimenCalculator.evaluate(config, beforeDate))

        val nowBefore = beforeDate.atTime(12, 0)
        val reminder = PillRegimenCalculator.nextReminderDateTime(config, nowBefore)
        assertEquals(startDate.atTime(20, 0), reminder)
    }
}
