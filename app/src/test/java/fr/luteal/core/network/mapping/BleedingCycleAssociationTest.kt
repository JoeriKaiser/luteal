package fr.luteal.core.network.mapping

import fr.luteal.core.model.BleedingIntensity
import fr.luteal.core.model.PeriodDay
import fr.luteal.core.network.contract.models.BleedingObservationData
import fr.luteal.core.network.contract.models.Flow
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Deterministic tests for the canonical bleeding-to-cycle association rule
 * ([associatePeriodDays]). Every device must derive the same period days from
 * the same bleeding observations for multi-device convergence to be silent-loss
 * free.
 */
class BleedingCycleAssociationTest {

    private val now: OffsetDateTime = OffsetDateTime.of(2026, 7, 1, 8, 0, 0, 0, ZoneOffset.UTC)
    private val start = LocalDate.of(2026, 6, 30)

    private fun obs(
        date: LocalDate,
        flow: Flow,
        intermenstrual: Boolean = false,
        notes: String = ""
    ) = BleedingObservationData(
        id = UUID.randomUUID(),
        clientRev = UUID.randomUUID(),
        createdAt = now,
        updatedAt = now,
        observedDate = date,
        flow = flow,
        intermenstrual = intermenstrual,
        notes = notes
    )

    @Test
    fun `observations within bounds become sorted period days`() {
        val observations = listOf(
            obs(start.plusDays(1), Flow.LIGHT),
            obs(start, Flow.MEDIUM, notes = "day one")
        )

        val result = associatePeriodDays(start, start.plusDays(4), observations)

        assertEquals(
            listOf(
                PeriodDay(start, BleedingIntensity.MEDIUM, "day one"),
                PeriodDay(start.plusDays(1), BleedingIntensity.LIGHT)
            ),
            result
        )
    }

    @Test
    fun `observations outside bounds are excluded`() {
        val observations = listOf(
            obs(start.minusDays(1), Flow.HEAVY), // before start
            obs(start, Flow.MEDIUM),
            obs(start.plusDays(5), Flow.HEAVY) // after end
        )

        val result = associatePeriodDays(start, start.plusDays(4), observations)

        assertEquals(listOf(start), result.map { it.date })
    }

    @Test
    fun `open cycle takes every observation from start onward`() {
        val observations = listOf(
            obs(start.minusDays(1), Flow.HEAVY), // before start, excluded
            obs(start, Flow.MEDIUM),
            obs(start.plusDays(30), Flow.SPOTTING) // far future, still in an open cycle
        )

        val result = associatePeriodDays(start, null, observations)

        assertEquals(listOf(start, start.plusDays(30)), result.map { it.date })
    }

    @Test
    fun `none flow and intermenstrual observations are not period days`() {
        val observations = listOf(
            obs(start, Flow.NONE),
            obs(start.plusDays(1), Flow.SPOTTING, intermenstrual = true),
            obs(start.plusDays(2), Flow.LIGHT)
        )

        val result = associatePeriodDays(start, start.plusDays(4), observations)

        assertEquals(listOf(start.plusDays(2)), result.map { it.date })
    }

    @Test
    fun `duplicate dates collapse to the heaviest flow regardless of order`() {
        val heavyFirst = listOf(
            obs(start, Flow.HEAVY),
            obs(start, Flow.SPOTTING)
        )
        val spottingFirst = listOf(
            obs(start, Flow.SPOTTING),
            obs(start, Flow.HEAVY)
        )

        val expected = listOf(PeriodDay(start, BleedingIntensity.HEAVY))
        assertEquals(expected, associatePeriodDays(start, start.plusDays(4), heavyFirst))
        assertEquals(expected, associatePeriodDays(start, start.plusDays(4), spottingFirst))
    }

    @Test
    fun `empty match preserves the fallback period days`() {
        val fallback = listOf(PeriodDay(start, BleedingIntensity.MEDIUM))

        val result = associatePeriodDays(start, start.plusDays(4), observations = emptyList(), fallback = fallback)

        assertEquals(fallback, result)
    }

    @Test
    fun `notes come from the representative observation`() {
        val observations = listOf(
            obs(start, Flow.LIGHT, notes = "kept"),
            obs(start, Flow.HEAVY, notes = "heaviest")
        )

        val result = associatePeriodDays(start, start.plusDays(4), observations)

        assertEquals(listOf(PeriodDay(start, BleedingIntensity.HEAVY, "heaviest")), result)
    }
}
