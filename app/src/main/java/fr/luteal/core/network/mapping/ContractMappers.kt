package fr.luteal.core.network.mapping

import fr.luteal.core.model.BleedingIntensity
import fr.luteal.core.model.Cycle
import fr.luteal.core.model.DailyEntry
import fr.luteal.core.model.PeriodDay
import fr.luteal.core.model.SymptomCategory
import fr.luteal.core.model.SymptomLog
import fr.luteal.core.network.contract.models.BleedingObservationData
import fr.luteal.core.network.contract.models.Certainty
import fr.luteal.core.network.contract.models.CycleData
import fr.luteal.core.network.contract.models.DailyEntryData
import fr.luteal.core.network.contract.models.Flow
import fr.luteal.core.network.contract.models.RecordSource
import fr.luteal.core.network.contract.models.SymptomLogData
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import fr.luteal.core.network.contract.models.SymptomCategory as ContractSymptomCategory

/**
 * Mappers between local domain models (`core.model`) and the generated
 * contract DTOs (`core.network.contract.models`). The backend is the source
 * of truth; these translations are the seam where the client's shape meets
 * the canonical shape.
 *
 * The important structural divergence: the client embeds bleeding days inside
 * a [Cycle] (`periodDays`) and again on [DailyEntry], whereas the backend
 * stores one [BleedingObservationData] row per observed day. [fanOutBleeding]
 * and [collapseBleeding] bridge that.
 */

// --- Enums -----------------------------------------------------------------

fun BleedingIntensity.toFlow(): Flow = when (this) {
    BleedingIntensity.NONE -> Flow.NONE
    BleedingIntensity.SPOTTING -> Flow.SPOTTING
    BleedingIntensity.LIGHT -> Flow.LIGHT
    BleedingIntensity.MEDIUM -> Flow.MEDIUM
    BleedingIntensity.HEAVY -> Flow.HEAVY
}

fun Flow.toBleedingIntensity(): BleedingIntensity = when (this) {
    Flow.NONE -> BleedingIntensity.NONE
    Flow.SPOTTING -> BleedingIntensity.SPOTTING
    Flow.LIGHT -> BleedingIntensity.LIGHT
    Flow.MEDIUM -> BleedingIntensity.MEDIUM
    Flow.HEAVY -> BleedingIntensity.HEAVY
}

fun SymptomCategory.toContract(): ContractSymptomCategory = when (this) {
    SymptomCategory.MOOD -> ContractSymptomCategory.MOOD
    SymptomCategory.PHYSICAL -> ContractSymptomCategory.PHYSICAL
    SymptomCategory.ENERGY -> ContractSymptomCategory.ENERGY
    SymptomCategory.PAIN -> ContractSymptomCategory.PAIN
    SymptomCategory.CERVICAL_FLUID -> ContractSymptomCategory.CERVICAL_FLUID
}

/** The contract adds OTHER; it has no local equivalent, so fall back to PHYSICAL. */
fun ContractSymptomCategory.toDomain(): SymptomCategory = when (this) {
    ContractSymptomCategory.MOOD -> SymptomCategory.MOOD
    ContractSymptomCategory.PHYSICAL -> SymptomCategory.PHYSICAL
    ContractSymptomCategory.ENERGY -> SymptomCategory.ENERGY
    ContractSymptomCategory.PAIN -> SymptomCategory.PAIN
    ContractSymptomCategory.CERVICAL_FLUID -> SymptomCategory.CERVICAL_FLUID
    ContractSymptomCategory.OTHER -> SymptomCategory.PHYSICAL
}

// --- Cycle -----------------------------------------------------------------

/**
 * Maps a [Cycle] to its canonical [CycleData]. `length_days` is derived when
 * the cycle is closed; `bleeding_days` counts non-none period days. Envelope
 * fields come from [meta]. Requires [Cycle.id] to be a UUID string.
 */
fun Cycle.toCycleData(meta: SyncMeta): CycleData = CycleData(
    id = UUID.fromString(id),
    clientRev = meta.clientRev,
    createdAt = meta.createdAt,
    updatedAt = meta.updatedAt,
    deletedAt = meta.deletedAt,
    startDate = startDate,
    endDate = endDate,
    lengthDays = if (endDate != null) lengthInDays else null,
    bleedingDays = periodDays
        .count { it.bleedingIntensity != BleedingIntensity.NONE }
        .takeIf { periodDays.isNotEmpty() },
    certainty = Certainty.RECORDED,
    source = RecordSource.MANUAL,
    notes = "",
)

/**
 * Rebuilds a [Cycle] from canonical [CycleData]. Display-only hints
 * (averageLengthDays, lutealPhaseLengthDays) are not synced and keep their
 * defaults. [periodDays] must be supplied separately via [collapseBleeding].
 */
fun CycleData.toCycle(periodDays: List<PeriodDay> = emptyList()): Cycle = Cycle(
    id = id.toString(),
    startDate = startDate,
    endDate = endDate,
    periodDays = periodDays,
)

// --- Bleeding fan-out / collapse (the structural gotcha) -------------------

/**
 * Fans a cycle's embedded [PeriodDay]s out into one [BleedingObservationData]
 * per day, as the backend expects. Derived ids are deterministic
 * (cycleId + date) so re-syncing the same cycle upserts rather than
 * duplicates. Period days are menstrual by definition, so `intermenstrual`
 * is false.
 */
fun fanOutBleeding(cycleId: String, periodDays: List<PeriodDay>, meta: SyncMeta): List<BleedingObservationData> =
    periodDays.map { day ->
        BleedingObservationData(
            id = deterministicId("bleeding", day.date.toString()),
            clientRev = meta.clientRev,
            createdAt = meta.createdAt,
            updatedAt = meta.updatedAt,
            deletedAt = meta.deletedAt,
            observedDate = day.date,
            flow = day.bleedingIntensity.toFlow(),
            intermenstrual = false,
            productCount = null,
            notes = day.notes,
        )
    }

/**
 * Collapses per-day [BleedingObservationData] rows back into [PeriodDay]s for
 * local display. Days with no bleeding ([Flow.NONE]) are dropped, since a
 * period day implies bleeding.
 */
fun collapseBleeding(observations: List<BleedingObservationData>): List<PeriodDay> =
    observations
        .filter { it.flow != Flow.NONE }
        .map { obs ->
            PeriodDay(
                date = obs.observedDate,
                bleedingIntensity = obs.flow.toBleedingIntensity(),
                notes = obs.notes,
            )
        }
        .sortedBy { it.date }

/**
 * Canonical bleeding-to-cycle association rule.
 *
 * The backend stores bleeding as cycle-agnostic per-day observations (one per
 * date, unique live `(account_id, observed_date)`; "bleeding is a neutral
 * observation", see folicular/docs/data-model.md). The client embeds period
 * days inside a [Cycle]. This is the single authoritative rule every device
 * uses to rebuild a cycle's period days from bleeding observations, so
 * convergence is deterministic:
 *
 *  - an observation belongs to the cycle when its date falls within
 *    [startDate, endDate]; an open cycle (`endDate == null`) takes every
 *    observation from [startDate] onward;
 *  - observations with `flow == none` are not period days;
 *  - intermenstrual observations are excluded (by definition outside the
 *    menstrual period; FIGO aligns spotting with intermenstrual bleeding);
 *  - one period day per date; if duplicates ever occur, the heaviest flow
 *    wins, making the result independent of input order;
 *  - the result is sorted by date;
 *  - when nothing matches, [fallback] (the cycle's existing local period
 *    days) is preserved, so an incremental pull of just a cycle record never
 *    wipes locally known period days.
 */
fun associatePeriodDays(
    startDate: LocalDate,
    endDate: LocalDate?,
    observations: List<BleedingObservationData>,
    fallback: List<PeriodDay> = emptyList()
): List<PeriodDay> {
    val derived = observations
        .filter { it.flow != Flow.NONE && !it.intermenstrual }
        .filter { obs ->
            !obs.observedDate.isBefore(startDate) &&
                (endDate == null || !obs.observedDate.isAfter(endDate))
        }
        .groupBy { it.observedDate }
        .map { (date, obs) ->
            val representative = obs.maxByOrNull { it.flow.ordinal }!!
            PeriodDay(date, representative.flow.toBleedingIntensity(), representative.notes)
        }
        .sortedBy { it.date }
    return derived.ifEmpty { fallback }
}

/**
 * A [DailyEntry]'s bleeding is a second source of bleeding truth. When set, it
 * maps to its own [BleedingObservationData] (id derived from the date). Returns
 * null when the entry records no bleeding.
 */
fun DailyEntry.toBleedingObservation(meta: SyncMeta): BleedingObservationData? {
    val intensity = bleedingIntensity ?: return null
    return BleedingObservationData(
        id = deterministicId("bleeding", date.toString()),
        clientRev = meta.clientRev,
        createdAt = meta.createdAt,
        updatedAt = meta.updatedAt,
        deletedAt = meta.deletedAt,
        observedDate = date,
        flow = intensity.toFlow(),
        intermenstrual = false,
        productCount = null,
        notes = "",
    )
}

// --- Daily entry -----------------------------------------------------------

/**
 * Maps a [DailyEntry] to [DailyEntryData]. Entries are date-keyed and carry no
 * id, so one is derived deterministically from the date. Bleeding and symptom
 * ids are NOT carried here - they map to separate bleeding_observation and
 * symptom_log records (see [toBleedingObservation] and the symptom mappers).
 */
fun DailyEntry.toDailyEntryData(meta: SyncMeta): DailyEntryData = DailyEntryData(
    id = deterministicId("daily-entry", date.toString()),
    clientRev = meta.clientRev,
    createdAt = meta.createdAt,
    updatedAt = meta.updatedAt,
    deletedAt = meta.deletedAt,
    entryDate = date,
    painLevel = painLevel,
    moodLevel = moodLevel,
    energyLevel = energyLevel,
    notes = notes,
)

fun DailyEntryData.toDailyEntry(): DailyEntry = DailyEntry(
    date = entryDate,
    painLevel = painLevel,
    moodLevel = moodLevel,
    energyLevel = energyLevel,
    notes = notes,
    updatedAt = updatedAt.toInstant(),
)

// --- Symptom log -----------------------------------------------------------

fun SymptomLog.toSymptomLogData(meta: SyncMeta): SymptomLogData = SymptomLogData(
    id = UUID.fromString(id),
    clientRev = meta.clientRev,
    createdAt = meta.createdAt,
    updatedAt = meta.updatedAt,
    deletedAt = meta.deletedAt,
    logDate = date,
    loggedAt = OffsetDateTime.ofInstant(timestamp, ZoneOffset.UTC),
    symptomKey = symptomId,
    severity = severity,
    notes = notes,
)

fun SymptomLogData.toSymptomLog(): SymptomLog = SymptomLog(
    id = id.toString(),
    timestamp = loggedAt.toInstant(),
    date = logDate,
    symptomId = symptomKey,
    severity = severity,
    notes = notes,
)
