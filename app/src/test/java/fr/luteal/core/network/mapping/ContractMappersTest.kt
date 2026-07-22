package fr.luteal.core.network.mapping

import fr.luteal.core.model.BleedingIntensity
import fr.luteal.core.model.Cycle
import fr.luteal.core.model.DailyEntry
import fr.luteal.core.model.PeriodDay
import fr.luteal.core.model.SymptomLog
import fr.luteal.core.network.contract.models.BleedingObservationData
import fr.luteal.core.network.contract.models.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractMappersTest {

    private val now = OffsetDateTime.of(2026, 7, 21, 12, 0, 0, 0, ZoneOffset.UTC)
    private fun meta() = SyncMeta(UUID.randomUUID(), now, now)

    @Test
    fun `bleeding fans out one row per period day with deterministic ids`() {
        val cycleId = "019832e0-6c14-7000-8000-000000000001"
        val days = listOf(
            PeriodDay(LocalDate.of(2026, 6, 30), BleedingIntensity.HEAVY),
            PeriodDay(LocalDate.of(2026, 7, 1), BleedingIntensity.LIGHT, notes = "jour 2"),
        )

        val obs = fanOutBleeding(cycleId, days, meta())

        assertEquals(2, obs.size)
        assertEquals(Flow.HEAVY, obs[0].flow)
        assertEquals(LocalDate.of(2026, 6, 30), obs[0].observedDate)
        assertEquals("jour 2", obs[1].notes)
        assertFalse(obs[0].intermenstrual)

        // Deterministic: same source data maps to the same ids across syncs.
        val again = fanOutBleeding(cycleId, days, meta())
        assertEquals(obs[0].id, again[0].id)
        assertEquals(obs[1].id, again[1].id)
    }

    @Test
    fun `bleeding collapse drops none days and sorts ascending`() {
        val m = meta()
        val obs = listOf(
            BleedingObservationData(
                id = UUID.randomUUID(), clientRev = m.clientRev, createdAt = now, updatedAt = now,
                observedDate = LocalDate.of(2026, 7, 2), flow = Flow.NONE, intermenstrual = false, notes = "",
            ),
            BleedingObservationData(
                id = UUID.randomUUID(), clientRev = m.clientRev, createdAt = now, updatedAt = now,
                observedDate = LocalDate.of(2026, 7, 1), flow = Flow.LIGHT, intermenstrual = false, notes = "",
            ),
            BleedingObservationData(
                id = UUID.randomUUID(), clientRev = m.clientRev, createdAt = now, updatedAt = now,
                observedDate = LocalDate.of(2026, 6, 30), flow = Flow.HEAVY, intermenstrual = false, notes = "",
            ),
        )

        val days = collapseBleeding(obs)

        assertEquals(2, days.size) // NONE day dropped
        assertEquals(LocalDate.of(2026, 6, 30), days[0].date)
        assertEquals(BleedingIntensity.HEAVY, days[0].bleedingIntensity)
        assertEquals(LocalDate.of(2026, 7, 1), days[1].date)
    }

    @Test
    fun `cycle round-trips core fields through fan-out and collapse`() {
        val cycle = Cycle(
            id = "019832e0-6c14-7000-8000-000000000001",
            startDate = LocalDate.of(2026, 6, 30),
            endDate = LocalDate.of(2026, 7, 27),
            periodDays = listOf(
                PeriodDay(LocalDate.of(2026, 6, 30), BleedingIntensity.HEAVY),
                PeriodDay(LocalDate.of(2026, 7, 1), BleedingIntensity.MEDIUM),
            ),
        )

        val data = cycle.toCycleData(meta())
        assertEquals(28, data.lengthDays) // 30 Jun -> 27 Jul inclusive
        assertEquals(2, data.bleedingDays)

        val rebuilt = data.toCycle(collapseBleeding(fanOutBleeding(cycle.id, cycle.periodDays, meta())))
        assertEquals(cycle.startDate, rebuilt.startDate)
        assertEquals(cycle.endDate, rebuilt.endDate)
        assertEquals(
            cycle.periodDays.map { it.date to it.bleedingIntensity },
            rebuilt.periodDays.map { it.date to it.bleedingIntensity },
        )
    }

    @Test
    fun `open cycle has null length and derives id-required fields`() {
        val cycle = Cycle(
            id = "019832e0-6c14-7000-8000-000000000001",
            startDate = LocalDate.of(2026, 6, 30),
        )
        val data = cycle.toCycleData(meta())
        assertNull(data.lengthDays)
        assertNull(data.endDate)
    }

    @Test
    fun `daily entry maps levels and derives a stable id from the date`() {
        val entry = DailyEntry(
            date = LocalDate.of(2026, 7, 21),
            painLevel = 2,
            moodLevel = 3,
            energyLevel = 4,
            notes = "ras",
        )
        val data = entry.toDailyEntryData(meta())
        assertEquals(LocalDate.of(2026, 7, 21), data.entryDate)
        assertEquals(2, data.painLevel)
        assertEquals(3, data.moodLevel)
        assertEquals(4, data.energyLevel)

        // Same date -> same derived id.
        assertEquals(entry.toDailyEntryData(meta()).id, data.id)

        val back = data.toDailyEntry()
        assertEquals(entry.date, back.date)
        assertEquals(entry.painLevel, back.painLevel)
        assertEquals(entry.notes, back.notes)
    }

    @Test
    fun `daily entry bleeding maps to a separate observation or null`() {
        val withBleeding = DailyEntry(date = LocalDate.of(2026, 7, 21), bleedingIntensity = BleedingIntensity.MEDIUM)
        val obs = withBleeding.toBleedingObservation(meta())
        assertNotNull(obs)
        assertEquals(Flow.MEDIUM, obs!!.flow)

        val without = DailyEntry(date = LocalDate.of(2026, 7, 21), painLevel = 1)
        assertNull(without.toBleedingObservation(meta()))
    }
    @Test
    fun `bleeding observation ids match between cycle period days and daily entry for the same date`() {
        val date = LocalDate.of(2026, 7, 21)
        val m = meta()
        val cycleId = "019832e0-6c14-7000-8000-000000000001"
        val periodDays = listOf(PeriodDay(date, BleedingIntensity.MEDIUM))
        val cycleObs = fanOutBleeding(cycleId, periodDays, m).first()

        val dailyEntry = DailyEntry(date = date, bleedingIntensity = BleedingIntensity.MEDIUM)
        val dailyObs = dailyEntry.toBleedingObservation(m)

        assertNotNull(dailyObs)
        assertEquals(cycleObs.id, dailyObs!!.id)
    }

    @Test
    fun `symptom log round-trips with utc logged_at`() {
        val ts = Instant.parse("2026-07-21T09:30:00Z")
        val log = SymptomLog(
            id = "019832e0-6c14-7000-8000-000000000010",
            timestamp = ts,
            date = LocalDate.of(2026, 7, 21),
            symptomId = "cramps",
            severity = 3,
            notes = "",
        )
        val data = log.toSymptomLogData(meta())
        assertEquals("cramps", data.symptomKey)
        assertEquals(3, data.severity)
        assertEquals(ts, data.loggedAt.toInstant())

        val back = data.toSymptomLog()
        assertEquals(log.id, back.id)
        assertEquals(log.symptomId, back.symptomId)
        assertEquals(log.timestamp, back.timestamp)
    }

    @Test
    fun `bleeding intensity and flow enums are bijective`() {
        for (intensity in BleedingIntensity.values()) {
            assertEquals(intensity, intensity.toFlow().toBleedingIntensity())
        }
        assertTrue(Flow.values().size == BleedingIntensity.values().size)
    }
}
