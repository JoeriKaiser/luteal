# Spec 09: Longitudinal Cycle History, Variability Visualizer, and Cycle Exclusion

## Problem Statement

1. **Absence of Longitudinal Context & Variance Visualization:**
   Currently, Luteal computes cycle length averages and raw min-max ranges (e.g. in the `TodayScreen` cycle stats section) but lacks a long-term, visual representation of historical cycles over time. Users cannot readily observe their longitudinal cycle length trajectory, seasonal shifts, rolling baseline trends, or natural cycle-to-cycle dispersion across months and years.

2. **Estimation Distortion from Transient Atypical Cycles:**
   Menstrual cycles are biologically sensitive to acute physiological and environmental perturbations — such as high fever, severe systemic illness, surgical interventions, emergency contraception (e.g. levonorgestrel or ulipristal acetate), sudden medication changes, extreme physical stress, or major circadian disruptions (jet lag, night shift transitions). When an anomalous cycle of 16 days or 54 days occurs due to a known transient event, Luteal's Bayesian estimation engine (`CycleEstimateCalculator`) incorporates this outlier into its recent interval window. This artificially skews the central prediction date and excessively expands the uncertainty range ($\pm \text{radius}$) for subsequent regular cycles.

3. **Destructive Data Loss Dilemma:**
   Without an explicit, non-destructive cycle exclusion mechanism, users who recognize that a past cycle was atypical are forced into an unacceptable dilemma:
   - **Delete the cycle start:** Permanently erases their historical record, bleeding logs, symptom notes, and timeline integrity, creating a false gap in their longitudinal health journal.
   - **Retain the cycle start:** Leaves the atypical cycle active in the estimation algorithm, causing distorted period predictions and inaccurate phase projections for the next 6 to 10 cycles.

4. **Stigmatizing Terminology vs. Objective Health Observation:**
   Mainstream cycle trackers often pathologize natural cycle variability by branding non-normative cycle lengths as "abnormal", "irregular", or "defective", or by attempting unprompted diagnostic screening. In alignment with Luteal's "The Quiet Instrument" design philosophy, cycle variability must be presented neutrally as factual empirical observations, and exclusion reasons must use respectful, non-stigmatizing, user-selected categories.

---

## Solution

1. **Interactive Longitudinal Cycle History & Variability Visualizer:**
   - Integrate a dedicated, interactive `CycleVariabilityVisualizer` component into `JournalScreen` (accessible via a dedicated tab or expandable section).
   - Render historical completed and active cycles as horizontal bar range charts anchored along a chronological timeline.
   - Visually present individual cycle lengths (in days) alongside a rolling median and rolling mean baseline.
   - Display a subtle population reference band (21–35 days, reflecting the clinical consensus range for typical cycle lengths) without framing cycles outside this band as "abnormal".
   - Incorporate objective STRAW+10 variability cues (highlighting consecutive cycle length differences $\ge 7$ days across a 10-cycle window) to help users observe patterns of increased dispersion without clinical staging.
   - Support direct touch/click interaction on any cycle bar to open a detailed summary card showing exact start/end dates, total cycle length, recorded bleeding days, logged symptoms, and estimation eligibility.

2. **Non-Destructive Cycle Exclusion Mechanism:**
   - Add an `isExcludedFromEstimates: Boolean` property (default `false`) and an optional `exclusionReason: CycleExclusionReason?` to the cycle domain model.
   - Provide a dedicated, accessible `CycleExclusionDialog` accessible from the cycle detail sheet, the timeline entry header, and the calendar cycle-start contextual menu.
   - Provide non-judgmental, predefined reason tags:
     - **Traitement médical / Chirurgie** (*Medical treatment / Surgery*)
     - **Maladie / Fièvre** (*Illness / Fever*)
     - **Changement contraceptif / Urgence** (*Contraceptive change / Emergency contraception*)
     - **Stress intense / Déplacement** (*Severe stress / Travel*)
     - **Autre motif** (*Other reason*)
   - Visually differentiate excluded cycles in the longitudinal visualizer using distinct styling (e.g. a muted, hatched/striped bar with an exclusion badge) while keeping them fully legible in the user's chronological journal.

3. **Bayesian Estimator Filtering (`CycleEstimateCalculator`):**
   - Update `CycleEstimateCalculator.evaluate()` to filter out cycles marked as `isExcludedFromEstimates = true` during interval length extraction.
   - When an intermediate cycle start is excluded, treat the boundary intervals appropriately: do not compute an artificial aggregate interval across the excluded start; instead, omit the intervals adjacent to the excluded event so that atypical timing does not corrupt the Bayesian prior or sample variance.
   - Preserve all recorded cycles for longitudinal visual history, statistics, and data exports, ensuring a clean separation between empirical historical facts and statistical prediction inputs.

4. **Offline-First Persistence, E2EE Sync, and Data Portability:**
   - Update Room `CycleEntity` with `isExcludedFromEstimates` and `exclusionReason` columns, backed by an automated Room migration (`MIGRATION_5_6`).
   - Extend the E2EE wire payload (`CycleData`) in the client-sealed cryptographic envelope with backwards-compatible optional fields.
   - Update JSON backup export/import schemas (`CycleBackupDto` in `LutealBackupPayload`) to preserve exclusion flags across device migrations and manual backups.

5. **French-First Localization & Accessible Design:**
   - Provide 100% string parity between French default (`values-fr`) and English (`values-en`).
   - Implement comprehensive WCAG 2.2 AA accessibility: minimum 48dp touch targets, TalkBack custom actions, high-contrast chart palettes ($\ge 4.5:1$ text, $\ge 3:1$ graphics), and semantic content descriptions for screen readers.

---

## User Stories

1. As a cycle tracking user, I want to view a visual chart of all my past cycle lengths over time in the Journal, so that I can understand my long-term patterns and natural variability at a glance.
2. As a cycle tracking user who experienced high fever and severe flu that delayed my period by two weeks, I want to mark that specific cycle as excluded from future predictions, so that my upcoming cycle estimates are not distorted by a one-off illness.
3. As a cycle tracking user who took emergency contraception, I want to exclude that atypical cycle while keeping its bleeding dates in my journal, so that my health record remains complete without throwing off my calendar forecasts.
4. As a cycle tracking user who underwent surgery or started a new medical treatment, I want to assign a respectful reason tag ("Traitement médical") to an excluded cycle, so that I remember why that cycle length diverged when reviewing my history months later.
5. As a cycle tracking user, I want excluded cycles to remain visible in my longitudinal chart with a clear visual indicator (e.g. hatched styling and exclusion badge), so that I know they are recorded in my history but omitted from the prediction math.
6. As a cycle tracking user, I want to toggle a cycle's exclusion status back on at any time if I change my mind, so that I retain full control over my data and calculations.
7. As a cycle tracking user, I want to see a rolling median or mean line on my cycle history chart, so that I can distinguish my baseline central tendency from temporary variations.
8. As a cycle tracking user, I want to tap on any bar in the longitudinal chart to view its start date, end date, duration, bleeding duration, and logged symptoms in a bottom sheet, so that I can inspect the full context of that cycle easily.
9. As a cycle tracking user with irregular cycles or SOPK, I want the history visualizer to display my cycle range without labeling my cycles as "abnormal" or "defective", so that I receive objective information without clinical judgment.
10. As a cycle tracking user entering perimenopause, I want the app to visualize consecutive cycle length swings ($\ge 7$ days) objectively, so that I can observe increasing variability without receiving unsolicited medical diagnoses.
11. As a cycle tracking user, I want to access the cycle exclusion dialog directly from the Journal timeline or calendar grid by tapping the cycle header, so that I don't have to navigate away from where I am reviewing my entries.
12. As a TalkBack user, I want each bar in the cycle history chart to have an informative accessibility label (e.g. "Cycle de mai 2026, 28 jours, du 2 mai au 29 mai, inclus dans les estimations"), so that I can navigate my cycle history via screen reader.
13. As a TalkBack user, I want TalkBack to state clearly whether a cycle is excluded and read its exclusion reason, so that I have the same contextual awareness as visual users.
14. As an offline user, I want my cycle exclusion toggles and reason tags to be saved immediately to local Room storage, so that my preference is preserved without an internet connection.
15. As a user with multiple synced devices, I want my cycle exclusion flags and reason tags to sync securely via end-to-end encryption, so that my cycle predictions match across my phone and tablet.
16. As a user performing a full JSON data export, I want my cycle exclusion states and tags to be included in the backup file, so that restoring my data on a new device restores my exact estimation settings.
17. As a Duo partner viewer, I want the shared cycle estimates to reflect the tracker's excluded cycle preferences without exposing private medical reason notes unless explicitly shared, so that prediction accuracy is maintained while respecting tracker privacy.
18. As a French-speaking user, I want all exclusion tags, chart labels, and dialog explanations to be written in natural, idiomatic French by default, so that the interface feels native and clear.

---

## Implementation Decisions

### 1. Architecture & Unidirectional Data Flow (MVI/UDF)

- **State Modeling:**
  The longitudinal history and exclusion state are modeled as immutable data structures exposed through `LutealUiState` and reactive `StateFlow` streams from `LutealViewModel`.
- **Component State:**
  Introduce `CycleHistoryUiState` representing:
  - `cycles: List<LongitudinalCycleItem>` (chronologically sorted cycles with computed length, bleeding days, exclusion metadata, and STRAW+10 swing indicators).
  - `rollingMedianDays: Double?`
  - `rollingMeanDays: Double?`
  - `eligibleCycleCount: Int`
  - `excludedCycleCount: Int`
  - `selectedCycle: LongitudinalCycleItem?` (for detail sheet inspection).
- **User Actions:**
  Actions are processed as explicit intent calls on `LutealViewModel`:
  - `onToggleCycleExclusion(cycleId: String, isExcluded: Boolean, reason: CycleExclusionReason?)`
  - `onSelectHistoryCycle(cycleId: String?)`

```
┌──────────────────────────────────────────────────────────────────────────┐
│                            LutealViewModel                               │
│                                                                          │
│  CycleRepository.getAllCycles() ──► LongitudinalCycleStatsCalculator    │
│                                     │                                    │
│                                     ▼                                    │
│                          CycleHistoryUiState                             │
│                                     │                                    │
│  CycleEstimateCalculator.evaluate(nonExcludedCycles) ──► EstimateResult  │
└─────────────────────────────────────┬────────────────────────────────────┘
                                      │ StateFlow<LutealUiState>
                                      ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                          JournalScreen (UI)                              │
│                                                                          │
│  ┌────────────────────────┐         ┌─────────────────────────────────┐  │
│  │   MonthCalendarGrid    │         │     CycleVariabilityVisualizer  │  │
│  │   (Calendar View)      │   OR    │     (Longitudinal Chart & Bar)  │  │
│  └────────────────────────┘         └────────────────┬────────────────┘  │
│                                                      │                   │
│                                                      ▼                   │
│                                            CycleExclusionDialog          │
└──────────────────────────────────────────────────────────────────────────┘
```

---

### 2. Domain Models & Calculation Engine

#### Domain Model Updates

Update `Cycle.kt` and define `CycleExclusionReason.kt`:

```kotlin
package fr.luteal.core.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Non-judgmental reason for excluding a cycle from Bayesian predictions.
 */
enum class CycleExclusionReason(val serialKey: String) {
    ILLNESS("illness"),
    MEDICAL_TREATMENT("medical_treatment"),
    CONTRACEPTION_CHANGE("contraception_change"),
    STRESS_OR_TRAVEL("stress_or_travel"),
    OTHER("other");

    companion object {
        fun fromSerialKey(key: String?): CycleExclusionReason? =
            entries.firstOrNull { it.serialKey.equals(key, ignoreCase = true) }
    }
}

data class Cycle(
    val id: String,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val averageLengthDays: Int = 28,
    val lutealPhaseLengthDays: Int = 14,
    val periodDays: List<PeriodDay> = emptyList(),
    val isExcludedFromEstimates: Boolean = false,
    val exclusionReason: CycleExclusionReason? = null
) {
    val isCurrent: Boolean
        get() = endDate == null

    val lengthInDays: Int
        get() = if (endDate != null) {
            ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1
        } else {
            ChronoUnit.DAYS.between(startDate, LocalDate.now()).toInt() + 1
        }
}
```

#### `CycleEstimateCalculator` Updates

Update `CycleEstimateCalculator.kt` to filter out excluded cycles before computing intervals:

```kotlin
object CycleEstimateCalculator {
    private const val MINIMUM_INTERVALS = 1
    private const val MINIMUM_CYCLE_DAYS = 15
    private const val MAXIMUM_CYCLE_DAYS = 90
    private const val RECENT_INTERVAL_WINDOW = 6

    val plausibleCycleDays: IntRange = MINIMUM_CYCLE_DAYS..MAXIMUM_CYCLE_DAYS

    fun evaluate(
        cycles: List<Cycle>,
        ageBand: AgeBand? = null,
        hasTimingContext: Boolean = false
    ): CycleEstimateResult {
        // Sort chronologically
        val sortedCycles = cycles.sortedBy(Cycle::startDate)
        
        // Exclude cycles marked by the user
        val eligibleCycles = sortedCycles.filterNot(Cycle::isExcludedFromEstimates)

        val starts = eligibleCycles
            .map(Cycle::startDate)
            .distinct()
            .sorted()

        if (starts.size < MINIMUM_INTERVALS + 1) {
            return CycleEstimateResult.NeedsMoreHistory
        }

        // Compute consecutive intervals between eligible cycles.
        // If an intermediate cycle was excluded, we only measure intervals between
        // consecutive eligible cycles whose start-date gap is within plausible range.
        val lengths = starts.zipWithNext { first, second ->
            ChronoUnit.DAYS.between(first, second).toInt()
        }.filter { it in plausibleCycleDays }

        if (lengths.size < MINIMUM_INTERVALS) {
            return CycleEstimateResult.IntervalsOutOfRange
        }

        val recentLengths = lengths.takeLast(RECENT_INTERVAL_WINDOW)
        val averageLength = recentLengths.average().roundToInt()

        val highVariability = hasPersistentVariability(lengths)
        val rangeRadius = rangeRadiusDays(
            lengths = recentLengths,
            highVariability = highVariability,
            hasTimingContext = hasTimingContext,
            priorSdDays = ageBand?.variationSdDays ?: AgeBand.UNDECLARED_VARIATION_SD_DAYS
        )
        val centralDate = starts.last().plusDays(averageLength.toLong())

        return CycleEstimateResult.Available(
            CycleEstimate(
                earliestDate = centralDate.minusDays(rangeRadius.toLong()),
                centralDate = centralDate,
                latestDate = centralDate.plusDays(rangeRadius.toLong()),
                cycleCount = recentLengths.size,
                variabilityDays = recentLengths.maxOrNull()!! - recentLengths.minOrNull()!!
            )
        )
    }
}
```

#### Longitudinal History & Variability Calculator

Introduce `LongitudinalCycleStatsCalculator.kt` to prepare display items and metrics:

```kotlin
data class LongitudinalCycleItem(
    val cycle: Cycle,
    val lengthDays: Int,
    val bleedingDaysCount: Int,
    val consecutiveDifferenceDays: Int?,
    val isStrawSwing: Boolean,
    val isExcluded: Boolean,
    val exclusionReason: CycleExclusionReason?
)

object LongitudinalCycleStatsCalculator {
    private const val STRAW_SWING_THRESHOLD = 7

    fun compute(cycles: List<Cycle>): List<LongitudinalCycleItem> {
        val sorted = cycles.sortedBy(Cycle::startDate)
        val completedCycles = sorted.filterNot(Cycle::isCurrent)

        return sorted.mapIndexed { index, cycle ->
            val length = cycle.lengthInDays
            val bleedingCount = cycle.periodDays.count { it.bleedingIntensity != BleedingIntensity.NONE }
            
            val prevCycle = sorted.getOrNull(index - 1)
            val diff = if (prevCycle != null && !prevCycle.isCurrent && !cycle.isCurrent) {
                kotlin.math.abs(length - prevCycle.lengthInDays)
            } else null

            val isStraw = diff != null && diff >= STRAW_SWING_THRESHOLD

            LongitudinalCycleItem(
                cycle = cycle,
                lengthDays = length,
                bleedingDaysCount = bleedingCount,
                consecutiveDifferenceDays = diff,
                isStrawSwing = isStraw,
                isExcluded = cycle.isExcludedFromEstimates,
                exclusionReason = cycle.exclusionReason
            )
        }
    }

    fun calculateMedian(lengths: List<Int>): Double? {
        if (lengths.isEmpty()) return null
        val sorted = lengths.sorted()
        val size = sorted.size
        return if (size % 2 == 0) {
            (sorted[size / 2 - 1] + sorted[size / 2]) / 2.0
        } else {
            sorted[size / 2].toDouble()
        }
    }
}
```

---

### 3. Room Database & Persistence Layer

#### Room Entity Update (`CycleEntity.kt`)

```kotlin
package fr.luteal.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cycles")
data class CycleEntity(
    @PrimaryKey val id: String,
    val startDate: String,
    val endDate: String? = null,
    val periodDaysJson: String,
    val averageLengthDays: Int,
    val lutealPhaseLengthDays: Int,
    val isExcludedFromEstimates: Boolean = false,
    val exclusionReason: String? = null,
    val isSynced: Boolean = false
)
```

#### Room Migration (`MIGRATION_5_6`)

In `LutealDatabase.kt`:

```kotlin
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE cycles 
            ADD COLUMN isExcludedFromEstimates INTEGER NOT NULL DEFAULT 0
            """.trimIndent()
        )
        db.execSQL(
            """
            ALTER TABLE cycles 
            ADD COLUMN exclusionReason TEXT DEFAULT NULL
            """.trimIndent()
        )
    }
}
```

Update `LutealDatabase` annotation:
- `version = 6`
- Add `MIGRATION_5_6` to the migrations array.

#### Room DAO (`CycleDao.kt`)

```kotlin
@Dao
interface CycleDao {
    @Query("SELECT * FROM cycles ORDER BY startDate DESC")
    fun getAllCycles(): Flow<List<CycleEntity>>

    @Query("SELECT * FROM cycles WHERE id = :cycleId")
    suspend fun getCycleById(cycleId: String): CycleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCycle(cycle: CycleEntity)

    @Query("""
        UPDATE cycles 
        SET isExcludedFromEstimates = :isExcluded, 
            exclusionReason = :reason, 
            isSynced = 0 
        WHERE id = :cycleId
    """)
    suspend fun updateExclusion(cycleId: String, isExcluded: Boolean, reason: String?)

    @Query("DELETE FROM cycles WHERE id = :cycleId")
    suspend fun deleteCycle(cycleId: String)
}
```

#### Repository Updates (`CycleRepository.kt` & `CycleRepositoryImpl.kt`)

Add methods:
```kotlin
interface CycleRepository {
    fun getAllCycles(): Flow<List<Cycle>>
    suspend fun getCyclesOnce(): List<Cycle>
    suspend fun saveCycle(cycle: Cycle)
    suspend fun updateCycleExclusion(cycleId: String, isExcluded: Boolean, reason: CycleExclusionReason?)
    suspend fun deleteCycle(cycleId: String)
}
```

`CycleRepositoryImpl` implementation:
- Updates the database row via `cycleDao.updateExclusion()`.
- Updates `SyncStateDao` marking entity type `CYCLE` as dirty with a new UTC timestamp and client revision to trigger E2EE delta sync.

---

### 4. UI & Jetpack Compose Components

#### 1. `CycleVariabilityVisualizer.kt`

A responsive, accessible Compose chart rendering cycle bars along a shared day-count scale (e.g. 15 to 60+ days):

- **Layout Structure:**
  - Header with summary badges: Total recorded cycles, active rolling median ($M$ days), rolling mean ($\mu$ days), count of excluded cycles.
  - Scrollable horizontal or vertical bar chart with fixed 48dp bar height/width for tap target compliance.
  - Horizontal reference lines at 21 and 35 days (typical biological span) rendered in subtle dashed `outlineVariant` color.
  - Rolling median vertical indicator across the chart.
- **Bar Visual Styling:**
  - **Normal Cycle:** Filled rounded bar using `MaterialTheme.colorScheme.primaryContainer`, text label showing cycle length in days (`28 j`).
  - **Current Cycle:** Open gradient bar with `primary` outline and pulsing/distinct cap indicating in-progress status.
  - **Excluded Cycle:** Striped/hatched pattern or muted container (`surfaceVariant` with `0.6f` alpha), strike-through text or slash badge, and a distinct exclusion tag pill (`Maladie`).
  - **STRAW+10 Swing Cue:** A small, neutral delta badge (`Δ 8j`) positioned between consecutive bars when the absolute difference $\ge 7$ days.
- **Interaction:**
  - Tapping a bar triggers `onSelectCycle(item)` opening `CycleDetailBottomSheet`.

```
┌──────────────────────────────────────────────────────────────────────────┐
│  Variabilité & Historique des cycles                         [3 Exclus]  │
│  Médiane : 29 jours  •  Moyenne : 29.4 jours  •  Total : 14 cycles       │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  21j (Réf min)                  28j (Médiane)             35j (Réf max)  │
│   │                                  │                         │         │
│   │   [Jan 2026] 28 jours            │                         │         │
│   ├───████████████████████████████───┤                         │         │
│   │                                  │                         │         │
│   │   [Fév 2026] 30 jours            │                         │         │
│   ├───██████████████████████████████─┼                         │         │
│   │                                  │                         │         │
│   │   [Mar 2026] 42 jours [EXCLU: Maladie]                     │         │
│   ├───░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░─────────────┼─── (42j)│
│   │   (Exclu des calculs de prédiction)                        │         │
│   │                                  │                         │         │
│   │   [Avr 2026] 29 jours            │                         │         │
│   ├───█████████████████████████████──┤                         │         │
│   │                                  │                         │         │
│   │   [Mai 2026] Cycle en cours (14e jour)                     │         │
│   ├───▓▓▓▓▓▓▓▓▓▓▓▓▓▓────────►        │                         │         │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

#### 2. `CycleExclusionDialog.kt`

Modal dialog or bottom sheet allowing the user to toggle exclusion and select a reason:

```kotlin
@Composable
fun CycleExclusionDialog(
    cycle: Cycle,
    onDismiss: () -> Unit,
    onConfirm: (isExcluded: Boolean, reason: CycleExclusionReason?) -> Unit
) {
    var isExcluded by rememberSaveable { mutableStateOf(cycle.isExcludedFromEstimates) }
    var selectedReason by rememberSaveable { mutableStateOf(cycle.exclusionReason ?: CycleExclusionReason.OTHER) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.cycle_exclusion_dialog_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(LutealSpacing.md)
            ) {
                Text(
                    text = stringResource(R.string.cycle_exclusion_dialog_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.cycle_exclusion_toggle_label),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = isExcluded,
                        onCheckedChange = { isExcluded = it }
                    )
                }

                AnimatedVisibility(visible = isExcluded) {
                    Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
                        Text(
                            text = stringResource(R.string.cycle_exclusion_reason_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs),
                            verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)
                        ) {
                            CycleExclusionReason.entries.forEach { reason ->
                                FilterChip(
                                    selected = selectedReason == reason,
                                    onClick = { selectedReason = reason },
                                    label = { Text(stringResource(reason.labelResId())) },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(isExcluded, if (isExcluded) selectedReason else null)
                },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
```

#### 3. `JournalScreen.kt` Integration

In `JournalScreen.kt`, add a view mode tab or sub-mode switcher:
- `JournalViewMode.CALENDAR`
- `JournalViewMode.TIMELINE`
- `JournalViewMode.VARIABILITY` (or integrated `CycleVariabilityVisualizerCard` in the timeline header).

Add contextual "Gérer l'exclusion" / "Exclure des estimations" action items to:
- `JournalEntryRow` overflow menu when a cycle start is present.
- `EditCycleDialog` as an optional switch.
- Direct tap on `CycleVariabilityVisualizer` bar.

---

### 5. E2EE Sync, Wire Serialization, and Backup Compatibility

#### E2EE Wire Contract Mapping (`CycleData.kt` & `ContractMappers.kt`)

In client-side sync mapping:
- Extend `CycleData` serialization mapping to include `is_excluded_from_estimates: Boolean` and `exclusion_reason: String?`.
- Envelope properties remain fully encrypted using AES-256-GCM under the client's local record sealer key before transmission to the Go backend (`folicular`).
- The backend stores the opaque ciphertext payload and remains completely agnostic to the inner record schema changes.

```kotlin
fun Cycle.toCycleData(meta: SyncMeta): CycleData = CycleData(
    id = UUID.fromString(id),
    clientRev = meta.clientRev,
    createdAt = meta.createdAt,
    updatedAt = meta.updatedAt,
    deletedAt = meta.deletedAt,
    startDate = startDate,
    endDate = endDate,
    lengthDays = if (endDate != null) lengthInDays else null,
    bleedingDays = periodDays.count { it.bleedingIntensity != BleedingIntensity.NONE },
    certainty = Certainty.CONFIRMED,
    source = RecordSource.MANUAL,
    notes = buildString {
        if (isExcludedFromEstimates) {
            append("[EXCLUDED:${exclusionReason?.serialKey ?: "other"}]")
        }
    }
)
```

#### Backup & Portability Schema (`LutealBackupPayload.kt`)

Update `CycleBackupDto`:

```kotlin
@Serializable
data class CycleBackupDto(
    @SerialName("id") val id: String,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("average_length_days") val averageLengthDays: Int = 28,
    @SerialName("luteal_phase_length_days") val lutealPhaseLengthDays: Int = 14,
    @SerialName("period_days") val periodDays: List<PeriodDayBackupDto> = emptyList(),
    @SerialName("is_excluded_from_estimates") val isExcludedFromEstimates: Boolean = false,
    @SerialName("exclusion_reason") val exclusionReason: String? = null
)
```

When importing backups:
- If `is_excluded_from_estimates` is absent in legacy JSON files (schema version 1), default to `false` and `null` reason, ensuring flawless backwards compatibility.

---

### 6. Localization & Neutral Copy

#### French Default (`values-fr/strings.xml`)
```xml
<resources>
    <string name="cycle_history_title">Historique &amp; Variabilité</string>
    <string name="cycle_history_subtitle">Observation longitudinale de vos durées de cycles</string>
    <string name="cycle_history_median">Médiane : %1$s jours</string>
    <string name="cycle_history_mean">Moyenne : %1$s jours</string>
    <string name="cycle_history_straw_swing">Écart consécutif de %1$d jours</string>
    <string name="cycle_history_excluded_badge">Exclu des calculs</string>
    <string name="cycle_history_in_progress">Cycle en cours (%1$d jours)</string>
    
    <string name="cycle_exclusion_dialog_title">Exclusion du cycle</string>
    <string name="cycle_exclusion_dialog_explanation">Exclure ce cycle le conserve intact dans votre journal mais l\'omet des calculs de prévision pour éviter de fausser les prochaines estimations.</string>
    <string name="cycle_exclusion_toggle_label">Exclure des estimations</string>
    <string name="cycle_exclusion_reason_label">Motif d\'observation (optionnel)</string>
    <string name="cycle_exclusion_action_edit">Gérer l\'exclusion</string>
    
    <!-- Reasons -->
    <string name="cycle_reason_illness">Maladie / Fièvre</string>
    <string name="cycle_reason_medical_treatment">Traitement médical / Chirurgie</string>
    <string name="cycle_reason_contraception_change">Changement contraceptif / Pilule du lendemain</string>
    <string name="cycle_reason_stress_or_travel">Stress intense / Déplacement</string>
    <string name="cycle_reason_other">Autre motif</string>
</resources>
```

#### English (`values-en/strings.xml`)
```xml
<resources>
    <string name="cycle_history_title">History &amp; Variability</string>
    <string name="cycle_history_subtitle">Longitudinal view of your cycle lengths</string>
    <string name="cycle_history_median">Median: %1$s days</string>
    <string name="cycle_history_mean">Mean: %1$s days</string>
    <string name="cycle_history_straw_swing">%1$d-day consecutive swing</string>
    <string name="cycle_history_excluded_badge">Excluded from estimates</string>
    <string name="cycle_history_in_progress">Current cycle (%1$d days)</string>
    
    <string name="cycle_exclusion_dialog_title">Cycle Exclusion</string>
    <string name="cycle_exclusion_dialog_explanation">Excluding this cycle preserves it in your journal history while omitting it from future prediction calculations to prevent skewing estimates.</string>
    <string name="cycle_exclusion_toggle_label">Exclude from estimates</string>
    <string name="cycle_exclusion_reason_label">Observation reason (optional)</string>
    <string name="cycle_exclusion_action_edit">Manage exclusion</string>
    
    <!-- Reasons -->
    <string name="cycle_reason_illness">Illness / Fever</string>
    <string name="cycle_reason_medical_treatment">Medical treatment / Surgery</string>
    <string name="cycle_reason_contraception_change">Contraception change / Emergency contraception</string>
    <string name="cycle_reason_stress_or_travel">Severe stress / Travel</string>
    <string name="cycle_reason_other">Other reason</string>
</resources>
```

---

### 7. Accessibility & WCAG 2.2 AA Compliance

- **Touch Target Size:**
  Every interactive element — chart bars, filter chips, switch toggles, and dialog action buttons — enforces a minimum touch target bounding box of $48 \times 48\text{dp}$ (`Modifier.heightIn(min = 48.dp)`).
- **TalkBack Semantics:**
  Each bar in `CycleVariabilityVisualizer` provides a rich semantic description via `Modifier.semantics { contentDescription = ... }`:
  - E.g. *"Cycle démarré le 12 janvier 2026, durée 28 jours, 5 jours de saignements. Inclus dans les estimations."*
  - E.g. *"Cycle démarré le 9 février 2026, durée 45 jours, 4 jours de saignements. Exclu des estimations : Maladie ou fièvre."*
- **Color Contrast:**
  All text against backgrounds satisfies $\ge 4.5:1$ contrast ratio. Chart graphical boundaries, striped patterns, and reference threshold lines satisfy $\ge 3:1$ graphical object contrast under both light and dark themes.
- **Dynamic Type / Adaptive Font Scaling:**
  All typography uses scalable `sp` units and accommodates 200% font scaling without text clipping or layout breakdown.

---

## Testing Decisions

### 1. Behavioral Test Criteria

#### `CycleEstimateCalculatorTest`
- **Single Atypical Cycle Exclusion:**
  Verify that excluding an extreme outlier cycle (e.g. 52 days in a series of 28-day cycles) restores the central estimate and tight uncertainty window to match the underlying 28-day baseline.
- **Consecutive Cycle Exclusions:**
  Verify that excluding two consecutive atypical cycles does not produce invalid negative intervals and cleanly computes intervals across remaining eligible boundaries.
- **Leading / Trailing Cycle Exclusions:**
  Verify that excluding the first recorded cycle or the most recent completed cycle correctly shifts the available interval set.
- **All Cycles Excluded Fallback:**
  Verify that if all recorded cycles are excluded, `CycleEstimateCalculator.evaluate()` returns `CycleEstimateResult.NeedsMoreHistory`.
- **STRAW+10 Persistence Interaction:**
  Verify that excluding a one-off illness cycle correctly removes an artificial 7-day swing from the STRAW+10 persistent variability counter.

#### `LongitudinalCycleStatsCalculatorTest`
- **Rolling Statistics:**
  Verify deterministic calculation of median, mean, and min/max lengths across varying list sizes.
- **Consecutive Difference:**
  Verify absolute difference calculations and STRAW swing flag triggering when consecutive completed cycle lengths differ by $\ge 7$ days.

#### `RoomMigrationTest` (`MigrationTest.kt`)
- Verify `MIGRATION_5_6` upgrades a database containing legacy cycle rows, inserting `isExcludedFromEstimates = 0` and `exclusionReason = NULL` without data loss or column corruption.

#### `CycleRepositoryTest`
- Verify `updateCycleExclusion(id, true, ILLNESS)` persists the new entity values in `CycleDao` and creates a dirty `SyncStateEntity` record for background E2EE synchronization.

#### `CycleSyncEngineTest`
- Verify round-trip encryption/decryption of `CycleData` with exclusion fields.
- Verify backward compatibility when decrypting payloads generated by older client versions.

#### `ComposeUiTest` (`CycleVariabilityVisualizerTest.kt`)
- Verify rendering of normal, current, and excluded bars.
- Verify that clicking a bar triggers the selection callback.
- Verify TalkBack semantic node assertions for accessibility text.

---

## Out of Scope

1. **Automated / Heuristic Outlier Auto-Exclusion:**
   The application must never automatically exclude a cycle without explicit user confirmation. Silent algorithmic data rejection violates the principle of "The Quiet Instrument".
2. **Fertility Window / Conception Scoring:**
   Exclusion is used strictly for menstrual timing and phase estimation. Luteal does not calculate fertile windows or conception probabilities.
3. **Clinical Diagnosis & Perimenopause Staging:**
   STRAW+10 variability patterns are used strictly as internal mathematical uncertainty triggers and neutral visual history cues. The app must never diagnose perimenopause, PCOS, or any endocrinological disorder.
4. **Third-Party Proprietary Cloud Health Sync:**
   Direct syncing to Google Health Connect, Apple Health, or unencrypted vendor clouds is out of scope. Sync is strictly E2EE with the user's private `folicular` server instance.

---

## Further Notes

### Research Register Citations

1. **Bull et al. (2019):**
   *Real-world menstrual cycle characteristics of more than 600,000 menstrual cycles*, npj Digital Medicine (PMC6710244).
   - Demonstrated mean cycle length 29.3 days (SD 5.2) with mean within-person variation of 2.6 days (SD 2.5). Validates the importance of within-person baseline stability and motivates filtering transient exogenous shocks.
2. **Li et al. (2023):**
   *Menstrual cycle length variation by demographic characteristics from the Apple Women's Health Study*, npj Digital Medicine 6:100 (PMC10226714).
   - Characterized age-stratified cycle variability (within-person SD rising from 3.79 days at ages 35–39 to 11.19 days above age 50). Informs the dynamic uncertainty bounds and non-pathologizing visualization of natural life-stage transitions.
3. **Harlow et al. (2012):**
   *Executive summary of the Stages of Reproductive Aging Workshop +10 (STRAW+10)*, Menopause 19(4):387–395 (PMC3340903).
   - Sources the 7-day consecutive cycle difference threshold within a 10-cycle window used as the objective criterion for recognizing persistent cycle variability.
4. **CNIL Health Data Baseline:**
   - Treating all cycle tracking and reason tags as sensitive health data requiring offline-first local storage, client-side cryptographic sealing, and zero third-party telemetry.

### "The Quiet Instrument" Philosophy

- **Empirical Facts vs. Statistical Models:**
  What happened in the user's life (a 45-day cycle during illness) is an incontrovertible empirical fact that belongs in their journal forever. What the app calculates (a Bayesian period prediction) is a mathematical estimation. Cycle exclusion creates a principled bridge: the fact remains preserved, while the model is protected from distortion.
