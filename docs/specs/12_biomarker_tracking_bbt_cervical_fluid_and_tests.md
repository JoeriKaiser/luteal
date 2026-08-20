# Spec 12: Biomarker Tracking: Basal Body Temperature, Cervical Fluid, and Rapid Tests

## Problem Statement

Users practicing Fertility Awareness Methods (FAM / Symptothermal Method, Billings Ovulation Method, Sensiplan) or investigating their reproductive health (e.g., confirming ovulation, evaluating luteal phase duration, navigating perimenopause, or managing cycle irregularities) require objective, daily physiological biomarkers.

The three primary evidence-based biomarkers are:
1. **Basal Body Temperature (BBT):** The waking resting body temperature, which exhibits a biphasic shift (a sustained increase of ~0.2°C / 0.4°F) driven by post-ovulatory progesterone secretion from the corpus luteum, allowing retrospective confirmation of ovulation.
2. **Cervical Fluid (Mucus):** Estrogen-stimulated secretions produced by the cervix that transition predictably in sensation (dry → damp → wet → slippery) and texture (sticky → creamy → egg-white / watery) as the ovulatory phase approaches.
3. **Rapid Test Strips:** At-home urine test strips for Luteinizing Hormone (LH / OPK) to detect the acute LH surge preceding follicular rupture, and Human Chorionic Gonadotropin (HCG) for qualitative pregnancy detection.

**Current Gaps in Luteal:**
- While `SymptomCategory.CERVICAL_FLUID` exists in the codebase enums and OpenAPI specification, Luteal offers no structured domain models, UI selectors, or database persistence for cervical fluid observations.
- Basal Body Temperature cannot be recorded with decimal precision, waking timestamps, or confounding disturbance tags (e.g., alcohol, fever, poor sleep).
- Rapid test strip results cannot be recorded as discrete diagnostic states.
- Users are forced to record these critical biomarkers in freeform text notes. This prevents graphical visualization (thermal curve overlays), retrospective shift analysis, structured end-to-end encrypted synchronization, and exportable clinical charting.

---

## Solution

Implement a comprehensive, privacy-first biomarker tracking system in Luteal structured into three distinct physiological domains, accompanied by retrospective charting in the Journal and full end-to-end encrypted synchronization.

### 1. Basal Body Temperature (BBT) Engine
- **Precision Input:** Morning temperature logging in Celsius (34.00°C–42.00°C in 0.05°C steps) or Fahrenheit (93.20°F–107.60°F in 0.1°F steps) with seamless preference-based unit switching.
- **Wake Time Capture:** Optional measurement timestamp (HH:mm) to track circadian variation.
- **Disturbance Tagging:** Multi-select flags for confounding factors that elevate or distort basal temperature:
  - Fever / Illness (*Fièvre / Maladie*)
  - Alcohol consumption (*Consommation d'alcool*)
  - Disturbed / Short sleep (*Sommeil perturbé / insuffisant*)
  - Travel / Time-zone shift (*Voyage / Décalage horaire*)
  - Late / Irregular measurement time (*Prise tardive*)
  - Stress / Medication (*Stress / Médicaments*)
- **Thermal Shift Charting in Journal:** Retrospective visual overlay rendering the temperature curve across the cycle, marking disturbed readings with hollow/warning glyphs, and calculating the coverline (baseline) using the validated **3-over-6 rule** (three consecutive valid temperatures at least 0.20°C / 0.36°F above the highest of the preceding six consecutive low temperatures).

### 2. Cervical Fluid 2D Structured Selector
- Sourced from international symptothermal standards (SOGC / Sensiplan), decoupling internal/external vulvar sensation from physical mucus texture:
  - **Sensation (*Sensation vulvaire*):**
    - *Sec / Dry* (No sensation, dry feel)
    - *Humide / Damp* (Slightly moist, non-lubricative)
    - *Mouillé / Wet* (Distinct wetness)
    - *Glissant / Slippery* (Highly lubricative, slick)
  - **Texture & Appearance (*Texture & Aspect*):**
    - *Collant / Sticky* (Tacky, breaks easily, thick, opaque)
    - *Crémeux / Creamy* (Lotion-like, milky, smooth)
    - *Blanc d'œuf / Egg-white* (Clear, stretchy, raw egg-white consistency)
    - *Aqueux / Watery* (Thin, clear, liquid)
- Clear state reset action allowing users to record an explicit lack of observable mucus.

### 3. Rapid Test Strip Logging
- **LH (Luteinizing Hormone / Ovulation Test Strips):**
  - *Négatif / Negative* (Test line lighter than control)
  - *Faible / Low* (Faint test line)
  - *Positif/Pic / Positive/Peak* (Test line equal to or darker than control line, indicating LH surge)
  - *Indéterminé / Indeterminate* (Invalid control line or unreadable strip)
- **HCG (Human Chorionic Gonadotropin / Pregnancy Test Strips):**
  - *Négatif / Negative* (Control line only)
  - *Positif / Positive* (Distinct positive test line)
  - *Douteux / Faint / Uncertain* (Faint shadow or evaporation line requiring follow-up retest)

### 4. Ethical Guardrails & "The Quiet Instrument"
- **Strict Distinction Between Facts and Predictions:** Biomarkers represent recorded physiological observations. The app performs retrospective pattern recognition (thermal shift detection), but **NEVER** issues automated predictive fertile window claims (*"You are fertile today"* or *"Unsafe day for intercourse"*).
- **No Contraception Guarantees:** Luteal does not provide algorithmic birth control or automated contraceptive clearance. The interface provides neutral, factual charting tools for informed user and clinician interpretation.
- **Zero Diagnostic Inference:** The app does not diagnose conditions such as luteal phase deficiency, anovulatory cycles, or pregnancy, presenting data purely as objective observation logs.

---

## User Stories

1. As a cycle tracking user, I want to record my morning Basal Body Temperature with 0.05°C precision, so that I can capture subtle post-ovulatory thermal shifts accurately.
2. As a user accustomed to Imperial measurements, I want to record and view my BBT in Fahrenheit (°F), so that I can use my existing Fahrenheit basal thermometer without mental conversions.
3. As a cycle tracking user, I want to log the exact time I took my temperature, so that I can identify measurements taken outside my normal waking window.
4. As a cycle tracking user, I want to tag disturbance flags (such as fever, alcohol, or disrupted sleep) on a temperature reading, so that abnormal outliers do not distort my baseline thermal chart.
5. As a symptothermal method practitioner, I want to record my vulvar sensation (Dry, Damp, Wet, Slippery) independently from mucus texture, so that I adhere to clinically validated cervical fluid recording protocols.
6. As a symptothermal method practitioner, I want to record cervical mucus texture (Sticky, Creamy, Egg-white, Watery), so that I can document peak estrogenic fluid signs.
7. As a cycle tracking user, I want to log LH (ovulation) test strip results as Negative, Low, Positive/Peak, or Indeterminate, so that I have a clear record of my LH surge timing relative to temperature shift.
8. As a cycle tracking user, I want to log at-home HCG pregnancy test strip results as Negative, Positive, or Faint/Uncertain, so that I can document early testing during the luteal phase.
9. As a cycle tracking user, I want to view an interactive Thermal Shift Chart in the Journal screen, so that I can visualize my temperature curve and biphasic shift across the active cycle.
10. As a cycle tracking user, I want disturbed temperature points to be visually distinguished (e.g., hollow data points with a disturbance badge) on the thermal chart, so that I can immediately spot non-representative measurements.
11. As a cycle tracking user, I want the thermal chart to automatically compute and display a coverline (baseline) once the 3-over-6 rule criteria are satisfied, so that I can retrospectively verify a sustained thermal shift.
12. As a cycle tracking user, I want cervical mucus observations and LH test results to be displayed as aligned chronological tracks beneath the temperature chart, so that I can correlate mucus peak days with LH surges and temperature rises.
13. As a user changing my preferred temperature unit in Settings, I want all historical temperature records to display in the new unit accurately without data corruption or precision loss.
14. As an offline user, I want all biomarker observations to be stored immediately in local Room database tables, so that logging is instant and works without network access.
15. As an end-to-end encrypted sync user, I want my biomarker observations to be encrypted with AES-256-GCM client-side before synchronization, so that the Folicular server never has access to my physiological health data in plaintext.
16. As a Duo primary tracker, I want my biomarker records (BBT, mucus, and pregnancy tests) to remain strictly private by default, so that intimate fertility observations are not shared with my partner without my explicit grant.
17. As a user exporting my data, I want all biomarker observations (temperatures, disturbance reasons, mucus classifications, rapid test logs) to be included in JSON and CSV exports, so that I maintain complete data sovereignty and can share records with my healthcare provider.
18. As a TalkBack screen-reader user, I want every biomarker control, chip, and chart point to have complete accessibility semantics and localized content descriptions, so that I can track and review my biomarkers non-visually.
19. As a user with enlarged system font scaling (up to 200%), I want the biomarker inputs and thermal chart legends to adapt dynamically without clipped text or broken touch targets.
20. As a French-first user, I want all biomarker terms, disturbance categories, and chart labels to use standard, neutral French medical terminology (*Température Basale, Sensation vulvaire, Glaire cervicale, Décalage thermique*), with complete parity in English.

---

## Implementation Decisions

### 1. Domain Architecture & Models (`fr.luteal.core.model`)

Create dedicated, immutable domain models representing biomarker observations and evaluation engines:

```kotlin
package fr.luteal.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

enum class TemperatureUnit {
    CELSIUS,
    FAHRENHEIT
}

enum class BbtDisturbance {
    FEVER,
    ALCOHOL,
    POOR_SLEEP,
    TIME_SHIFT,
    STRESS,
    MEDICATION
}

enum class CervicalMucusSensation {
    DRY,        // Sec
    DAMP,       // Humide
    WET,        // Mouillé
    SLIPPERY    // Glissant
}

enum class CervicalMucusTexture {
    STICKY,     // Collant
    CREAMY,     // Crémeux
    EGG_WHITE,  // Blanc d'œuf
    WATERY      // Aqueux
}

enum class LhTestResult {
    NEGATIVE,       // Négatif
    LOW,            // Faible
    PEAK_POSITIVE,  // Positif / Pic
    INDETERMINATE   // Indéterminé
}

enum class HcgTestResult {
    NEGATIVE,       // Négatif
    POSITIVE,       // Positif
    FAINT_UNCERTAIN // Douteux
}

data class BasalBodyTemperature(
    val valueCelsius: Double,
    val measuredTime: LocalTime? = null,
    val disturbances: Set<BbtDisturbance> = emptySet()
) {
    val isDisturbed: Boolean
        get() = disturbances.isNotEmpty()

    fun valueInUnit(unit: TemperatureUnit): Double = when (unit) {
        TemperatureUnit.CELSIUS -> valueCelsius
        TemperatureUnit.FAHRENHEIT -> celsiusToFahrenheit(valueCelsius)
    }

    companion object {
        val CELSIUS_VALID_RANGE = 34.00..42.00
        val FAHRENHEIT_VALID_RANGE = 93.20..107.60

        fun celsiusToFahrenheit(c: Double): Double = (c * 9.0 / 5.0) + 32.0
        fun fahrenheitToCelsius(f: Double): Double = (f - 32.0) * 5.0 / 9.0
    }
}

data class CervicalFluidObservation(
    val sensation: CervicalMucusSensation? = null,
    val texture: CervicalMucusTexture? = null
) {
    val hasObservation: Boolean
        get() = sensation != null || texture != null
}

data class RapidTestLogs(
    val lhTest: LhTestResult? = null,
    val hcgTest: HcgTestResult? = null
) {
    val hasLogs: Boolean
        get() = lhTest != null || hcgTest != null
}

data class BiomarkerObservation(
    val date: LocalDate,
    val bbt: BasalBodyTemperature? = null,
    val cervicalFluid: CervicalFluidObservation? = null,
    val rapidTests: RapidTestLogs? = null,
    val notes: String = "",
    val updatedAt: Instant = Instant.now()
) {
    val isEmpty: Boolean
        get() = bbt == null &&
            (cervicalFluid == null || !cervicalFluid.hasObservation) &&
            (rapidTests == null || !rapidTests.hasLogs) &&
            notes.isBlank()
}
```

### 2. Thermal Shift & Coverline Calculator (`fr.luteal.core.model.ThermalShiftCalculator`)

Implement a pure domain calculation engine for retrospective thermal shift evaluation based on the Roetzer / Sensiplan 3-over-6 rule:

```kotlin
sealed interface ThermalShiftResult {
    data object None : ThermalShiftResult
    data class Possible(val candidateShiftDate: LocalDate) : ThermalShiftResult
    data class Confirmed(
        val coverlineCelsius: Double,
        val firstHighDay: LocalDate,
        val baselineLowTemps: List<Double>,
        val highTemps: List<Double>
    ) : ThermalShiftResult
}

object ThermalShiftCalculator {
    private const val MIN_SHIFT_DELTA_CELSIUS = 0.20 // 0.2°C requirement on 3rd day

    fun evaluateCycle(
        cycleStartDate: LocalDate,
        observations: List<BiomarkerObservation>
    ): ThermalShiftResult {
        val validTemps = observations
            .filter { it.date >= cycleStartDate && it.bbt != null && !it.bbt.isDisturbed }
            .sortedBy { it.date }

        if (validTemps.size < 9) return ThermalShiftResult.None

        // Sliding window: evaluate 6 low temperatures followed by 3 elevated temperatures
        for (i in 0..(validTemps.size - 9)) {
            val sixLows = validTemps.subList(i, i + 6).map { it.bbt!!.valueCelsius }
            val threeHighs = validTemps.subList(i + 6, i + 9).map { it.bbt!!.valueCelsius }
            val maxLow = sixLows.maxOrNull() ?: continue

            val allThreeHigher = threeHighs.all { it > maxLow }
            val thirdDaySignificantlyHigher = threeHighs[2] >= (maxLow + MIN_SHIFT_DELTA_CELSIUS)

            if (allThreeHigher && thirdDaySignificantlyHigher) {
                val coverline = maxLow + 0.05 // Standard visual coverline placement
                return ThermalShiftResult.Confirmed(
                    coverlineCelsius = coverline,
                    firstHighDay = validTemps[i + 6].date,
                    baselineLowTemps = sixLows,
                    highTemps = threeHighs
                )
            }
        }
        return ThermalShiftResult.None
    }
}
```

### 3. Room & DataStore Persistence (Migration v5 -> v6)

#### Room Schema Migration:
Create table `biomarker_observations` in `LutealDatabase` version 6:

```kotlin
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS biomarker_observations (
                date TEXT NOT NULL PRIMARY KEY,
                bbtCelsius REAL,
                bbtTime TEXT,
                bbtQuality TEXT NOT NULL DEFAULT 'normal',
                bbtDisturbancesJson TEXT NOT NULL DEFAULT '[]',
                cervicalSensation TEXT,
                cervicalTexture TEXT,
                lhTestResult TEXT,
                hcgTestResult TEXT,
                notes TEXT NOT NULL DEFAULT '',
                updatedAtEpochMillis INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}
```

#### Room Entity & DAO:
```kotlin
@Entity(tableName = "biomarker_observations")
data class BiomarkerObservationEntity(
    @PrimaryKey val date: String,
    val bbtCelsius: Double?,
    val bbtTime: String?,
    val bbtQuality: String,
    val bbtDisturbancesJson: String,
    val cervicalSensation: String?,
    val cervicalTexture: String?,
    val lhTestResult: String?,
    val hcgTestResult: String?,
    val notes: String,
    val updatedAtEpochMillis: Long
)

@Dao
interface BiomarkerDao {
    @Query("SELECT * FROM biomarker_observations WHERE date = :date")
    fun getObservationForDate(date: String): Flow<BiomarkerObservationEntity?>

    @Query("SELECT * FROM biomarker_observations WHERE date >= :startDate AND date <= :endDate ORDER BY date ASC")
    fun getObservationsBetween(startDate: String, endDate: String): Flow<List<BiomarkerObservationEntity>>

    @Upsert
    suspend fun upsert(entity: BiomarkerObservationEntity)

    @Query("DELETE FROM biomarker_observations WHERE date = :date")
    suspend fun deleteForDate(date: String)
}
```

#### DataStore Preferences (`UserPreferencesDataStore.kt`):
- `KEY_TEMPERATURE_UNIT`: String ("CELSIUS" / "FAHRENHEIT", default "CELSIUS").
- `KEY_DEFAULT_WAKE_TIME`: String (e.g. "07:00").
- `KEY_SHOW_BIOMARKERS_IN_ENTRY_SHEET`: Boolean (default `true`).

### 4. UI & Presentation Components

#### Daily Entry Sheet Integration (`DailyEntrySheet.kt`):
Add an expandable "Biomarqueurs / Biomarkers" card containing:
1. **BBT Input Section:**
   - Custom decimal stepper with 0.05°C (or 0.1°F) increment/decrement buttons and direct numerical input field.
   - Quick wake-time chip launching a standard Android `TimePicker`.
   - FlowRow of disturbance toggle chips (*Fièvre, Alcool, Sommeil perturbé, Voyage, etc.*) with immediate visual feedback.
2. **Cervical Fluid Section:**
   - 2-Dimensional chip grid:
     - Row 1: Sensation (*Sec, Humide, Mouillé, Glissant*).
     - Row 2: Texture (*Collant, Crémeux, Blanc d'œuf, Aqueux*).
   - "Effacer" button to clear mucus selection.
3. **Rapid Test Section:**
   - LH strip segmented control (*Négatif, Faible, Positif/Pic, Indéterminé*).
   - HCG strip segmented control (*Négatif, Positif, Douteux*).

#### Thermal Shift Chart (`core/designsystem/component/ThermalShiftChart.kt`):
- Native Canvas-driven Compose chart rendered inside `JournalScreen`.
- Plots temperature (°C or °F) against cycle days ($J_1, J_2, \dots, J_n$).
- Data markers:
  - Solid circles for undisturbed BBT.
  - Outlined hollow circles with warning triangles for disturbed BBT readings.
  - Dashed horizontal line indicating the computed Coverline (Ligne de base) once shift is confirmed.
- Sub-axis biomarker track aligned below the chart:
  - Cervical fluid badges (e.g., "BO" for *Blanc d'œuf*, "GL" for *Glissant*).
  - LH test result badges (e.g., distinct pill for $LH^+$ peak days).
- Pinch-to-zoom and pan support for extended cycle lengths ($>35$ days).

### 5. Wire & End-to-End Encrypted Sync Mapping

- **Entity Type:** `"biomarker_observation"`.
- **Payload Serialization:** Maps to OpenAPI `BiomarkerObservationData` schema inside `SyncChangeInput.ciphertext`:
  ```json
  {
    "id": "018e3e4a-9b1b-7a31-b844-482a1768c2f1",
    "client_rev": "018e3e4a-9b1b-7a31-b844-482a1768c2f2",
    "created_at": "2026-08-15T06:30:00Z",
    "updated_at": "2026-08-15T06:30:00Z",
    "deleted_at": null,
    "observed_date": "2026-08-15",
    "bbt_celsius": 36.65,
    "bbt_time": "06:30",
    "bbt_quality": "normal",
    "bbt_disturbances": [],
    "cervical_sensation": "slippery",
    "cervical_texture": "egg_white",
    "lh_test_result": "peak_positive",
    "hcg_test_result": null,
    "notes": ""
  }
  ```
- **Sealed Encryption:** AES-256-GCM encryption with HKDF-SHA256 account keys ensures zero plaintext biomarker visibility on the Folicular backend.
- **Duo Sharing Isolation:** Biomarker observations are strictly omitted from `DuoViewContent` payloads to prevent accidental disclosure of sensitive fertility observations to paired partners.

### 6. Localization & Neutral Copy

Strict French-first copy in `res/values/strings.xml` and `res/values-fr/strings.xml` with 100% parity in `res/values-en/strings.xml`:

| String Key | French (FR) Default | English (EN) Parity |
|---|---|---|
| `biomarker_section_title` | Biomarqueurs | Biomarkers |
| `bbt_title` | Température Basale (BBT) | Basal Body Temperature (BBT) |
| `bbt_time_label` | Heure de prise | Measurement time |
| `bbt_disturbances_title` | Facteurs perturbateurs | Disturbance factors |
| `disturbance_fever` | Fièvre / Maladie | Fever / Illness |
| `disturbance_alcohol` | Alcool | Alcohol |
| `disturbance_poor_sleep` | Sommeil perturbé | Poor sleep |
| `disturbance_time_shift` | Décalage horaire | Time-zone / Travel |
| `disturbance_late_measurement` | Prise tardive | Late measurement |
| `disturbance_stress` | Stress / Médicament | Stress / Medication |
| `cervical_fluid_title` | Glaire cervicale | Cervical fluid |
| `cervical_sensation_header` | Sensation vulvaire | Vulvar sensation |
| `sensation_dry` | Sec | Dry |
| `sensation_damp` | Humide | Damp |
| `sensation_wet` | Mouillé | Wet |
| `sensation_slippery` | Glissant | Slippery |
| `cervical_texture_header` | Texture & Aspect | Texture & Appearance |
| `texture_sticky` | Collant | Sticky |
| `texture_creamy` | Crémeux | Creamy |
| `texture_egg_white` | Blanc d'œuf | Egg-white |
| `texture_watery` | Aqueux | Watery |
| `rapid_tests_title` | Tests rapides | Rapid tests |
| `lh_test_title` | Test d'ovulation (LH) | Ovulation test (LH) |
| `lh_negative` | Négatif | Negative |
| `lh_low` | Faible | Low |
| `lh_peak_positive` | Positif / Pic | Positive / Peak |
| `lh_indeterminate` | Indéterminé | Indeterminate |
| `hcg_test_title` | Test de grossesse (HCG) | Pregnancy test (HCG) |
| `hcg_negative` | Négatif | Negative |
| `hcg_positive` | Positif | Positive |
| `hcg_faint_uncertain` | Douteux | Faint / Uncertain |
| `thermal_chart_title` | Graphique thermique | Thermal chart |
| `coverline_label` | Ligne de base | Coverline |
| `thermal_shift_confirmed` | Décalage thermique confirmé | Thermal shift confirmed |

### 7. Accessibility & Design System

- **Touch Targets:** All chips, steppers, and segmented selectors enforce minimum dimensions of $48\text{dp} \times 48\text{dp}$.
- **Screen Reader Announcements:** TalkBack announcements provide complete contextual descriptions for chart elements (e.g., *"Jour de cycle 14, 15 août : 36,65 °C, sensation glissante, glaire blanc d'œuf, test LH positif"*).
- **Non-Color Reliance:** Disturbed temperature readings and test results utilize distinct geometric icons (triangles, checkmarks, dashes) alongside color fills.
- **Adaptive Typography:** Full layout compatibility with up to 200% Android system font scaling without text clipping or overlapping.

---

## Testing Decisions

### 1. Domain Unit Tests (`fr.luteal.core.model`)
- **`ThermalShiftCalculatorTest`:**
  - Standard biphasic cycle: 6 low temperatures (36.30°C–36.45°C) followed by 3 elevated temperatures (36.70°C, 36.75°C, 36.85°C) → asserts `Confirmed` with coverline at 36.50°C.
  - Disturbed temperature exclusion: verifies that elevated readings tagged with `FEVER` or `ALCOHOL` are omitted from the 6-low baseline calculation.
  - Monophasic / anovulatory cycle: asserts `None` when no sustained rise occurs.
  - Missing days / gaps: asserts correct evaluation across intermittent logging days.
- **`BasalBodyTemperatureTest`:**
  - Round-trip conversions between Celsius and Fahrenheit preserving numerical precision ($36.65^\circ\text{C} \leftrightarrow 97.97^\circ\text{F}$).
  - Validation bounds enforcement (rejects inputs $<34.0^\circ\text{C}$ or $>42.0^\circ\text{C}$).

### 2. Room Database & Migration Tests (`fr.luteal.core.data.local`)
- **`Migration5To6Test`:**
  - Uses `MigrationTestHelper` to create a v5 database, insert mock `daily_entries` and `cycle` records, execute `MIGRATION_5_6`, and verify that the `biomarker_observations` table is created with correct column types and nullability constraints.
  - Verifies CRUD operations on `BiomarkerDao` across date ranges.

### 3. Sync & Cryptographic Mapping Tests (`fr.luteal.core.network.mapping`)
- **`BiomarkerSyncMapperTest`:**
  - Serializes `BiomarkerObservation` to JSON, encrypts under AES-256-GCM, decrypts, and verifies exact field equality.
  - Validates Last-Write-Wins (LWW) conflict resolution logic using `updatedAtEpochMillis`.

### 4. Compose UI & Accessibility Tests (`fr.luteal.app`)
- **`DailyEntrySheetBiomarkerTest`:**
  - Verifies temperature stepper interaction, disturbance chip toggling, and 2D mucus matrix selections.
  - Confirms that saving the sheet emits a valid `BiomarkerObservation` payload.
- **`ThermalShiftChartSemanticsTest`:**
  - Tests TalkBack semantic node hierarchy and content descriptions on the thermal chart canvas.

---

## Out of Scope

1. **Automated Contraceptive / Fertile Window Predictions:** Algorithmic determination of "green / red days" or predictive fertile window countdowns (Natural Cycles / Daysy model).
2. **Bluetooth (BLE) Basal Thermometer Hardware Sync:** Automated BLE GATT wireless pairing with smart thermometers (reserved for a dedicated hardware integration specification).
3. **Camera-Based Optical Test Strip Scanning (OCR):** Computer-vision strip photo analysis and color intensity quantification.
4. **Automated Diagnostic Assertions:** Clinical diagnostic conclusions regarding luteal phase deficiency, PCOS, or pregnancy viability.

---

## Further Notes

### Sourced Clinical Guidelines & Literature
- **Society of Obstetricians and Gynaecologists of Canada (SOGC):** *Clinical Practice Guideline No. 401: Natural Family Planning and Fertility Awareness-Based Methods*. Affirms the dual-biomarker symptothermal framework (BBT + cervical mucus) as an evidence-based method for physiological cycle evaluation.
- **American College of Obstetricians and Gynecologists (ACOG):** *FAQ 112: Fertility Awareness-Based Methods of Family Planning*. Defines standard cervical mucus progression and post-ovulatory basal temperature shift criteria.
- **Roetzer, J. & Sensiplan Working Group (2015):** *Natural and Safe: The Symptothermal Method*. Sources the standardized **3-over-6 temperature evaluation rule** and the 2-dimensional cervical fluid classification matrix (sensation + texture).
- **Frank-Herrmann et al. (2007):** *The effectiveness of a fertility awareness based method to avoid pregnancy in relation to a couple's sexual behaviour during the fertile time: a prospective cohort study*, Human Reproduction, 22(5):1310–1319.

### Security & Privacy Considerations
- **GDPR Article 9 Compliance:** Biomarker data constitutes sensitive personal health data. All records remain encrypted at rest with SQLCipher/Android Keystore and in transit via client-side AES-256-GCM.
- **Zero Plaintext Cloud Telemetry:** Folicular sync servers receive zero plaintext temperature readings, mucus classifications, or pregnancy test logs.
