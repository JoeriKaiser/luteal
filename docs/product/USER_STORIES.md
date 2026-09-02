# Luteal Application User Story Catalog

This inventory documents all implemented user stories within the Luteal Android application, organized across 14 core functional epics. Every story defines the target persona, operational preconditions, user actions, acceptance criteria (including domain constraints and formulas), and source file references.

---

## Epic 1: Onboarding, Persona Selection & Personalization

### US-ONB-01: First-Run Privacy Architecture Presentation
- **Persona**: Solo Tracker, Duo Partner
- **Preconditions**: App launched on a fresh install or following a local data purge (`hasCompletedOnboarding == false`).
- **Description**: The user is presented with a privacy primer introducing Luteal's zero-knowledge model (local-first storage, zero third-party analytics, explicit consent for sharing).
- **Acceptance Criteria**:
  - Displays welcome copy and privacy guarantees before requesting any physiological data.
  - Provides a forward "Next" action to proceed and a "Skip" action to fast-track setup.
- **References**: `app/src/main/java/fr/luteal/app/navigation/OnboardingScreen.kt`, `res/values/strings.xml` (`onboarding_welcome_step_title`, `onboarding_welcome_step_body`).

### US-ONB-02: User Persona & Role Configuration
- **Persona**: Solo Tracker, Duo Partner
- **Preconditions**: Onboarding Step 1.
- **Description**: The user selects their primary application role: Primary Tracker or Partner Viewer.
- **Acceptance Criteria**:
  - Tracker selection configures the full cycle logging interface as the default navigation target.
  - Partner Viewer selection configures the dedicated Duo companion view.
  - Stores selection in DataStore (`UserPreferences.userRole` = `PRIMARY_TRACKER` | `PARTNER_VIEWER`).
- **References**: `OnboardingScreen.kt`, `fr.luteal.core.model.UserRole`, `UserPreferencesDataStore.kt`.

### US-ONB-03: Physiological Tracking Context Declaration
- **Persona**: Solo Tracker
- **Preconditions**: Onboarding Step 2 or Settings screen.
- **Description**: The user selects health conditions and life stages to customize symptom catalogs and prediction uncertainty without diagnosing or screening.
- **Acceptance Criteria**:
  - Supports toggling: `PMS`, `PMDD`, `ENDOMETRIOSIS`, `PCOS`, `PERIMENOPAUSE`, `THYROID`.
  - Classifies contexts into `ContextGroup.TIMING` (PCOS, Perimenopause, Thyroid) and `ContextGroup.OBSERVATION` (PMS, PMDD, Endometriosis).
  - Timing contexts expand the baseline estimation uncertainty window floor without shifting the central predicted date.
  - Observation contexts dynamically inject relevant symptom chips into the daily logger.
- **References**: `OnboardingScreen.kt`, `SettingsScreen.kt`, `fr.luteal.core.model.TrackingContext`, `ObservationCatalog.kt`.

### US-ONB-04: Epidemiological Age Band Selection
- **Persona**: Solo Tracker
- **Preconditions**: Onboarding Step 3 or Settings screen.
- **Description**: The user selects an optional age band to calibrate Bayesian empirical priors for cycle variability.
- **Acceptance Criteria**:
  - Selectable bands: `<20`, `20-24`, `25-29`, `30-34`, `35-39`, `40-44`, `45-49`, `50+`, or undeclared (`NONE`).
  - Sets population prior standard deviation $\sigma_{\text{prior}}$ derived from empirical data (Apple Women's Health Study, Li et al. 2023): e.g., 35–39 $\to$ 3.79d, 50+ $\to$ 11.19d, default undeclared $\to$ 4.54d.
  - Selection is strictly optional and can be cleared at any time.
- **References**: `OnboardingScreen.kt`, `SettingsScreen.kt`, `fr.luteal.core.model.AgeBand`, `CycleEstimateCalculator.kt`.

### US-ONB-05: Onboarding Completion Gate
- **Persona**: Solo Tracker, Duo Partner
- **Preconditions**: Final onboarding step.
- **Description**: The user reviews the summary and commits their initial configuration.
- **Acceptance Criteria**:
  - Persists `hasCompletedOnboarding = true` in DataStore.
  - Navigates immediately to the main scaffold (`LutealMainScaffold`) without requiring app restart.
- **References**: `OnboardingScreen.kt`, `LutealMainScaffold.kt`, `LutealViewModel.kt`.

---

## Epic 2: Daily Dashboard, Cycle Status & Health Facts (Today Screen)

### US-TDY-01: Cycle Day & Status Display
- **Persona**: Solo Tracker
- **Preconditions**: At least one active cycle exists (`Cycle.endDate == null` or start date $\le$ today).
- **Description**: The user views their current cycle day and recorded cycle status via a hero ring component.
- **Acceptance Criteria**:
  - Calculates current cycle day: $\text{cycleDay} = \text{ChronoUnit.DAYS.between}(\text{cycleStartDate}, \text{today}) + 1$.
  - Displays a "Recorded" badge indicating the cycle start date.
  - If no cycle exists, displays a zero-state card with a prominent "Add cycle start" action.
- **References**: `TodayScreen.kt`, `fr.luteal.core.designsystem.component.CycleRing`, `CurrentCyclePhaseCalculator.kt`.

### US-TDY-02: Next Period Forecast & Bayesian Uncertainty Window Track
- **Persona**: Solo Tracker
- **Preconditions**: User has recorded $\ge 2$ valid cycle starts with interval lengths between 15 and 90 days.
- **Description**: Displays the calculated forecast range for the next menstruation, accompanied by a visual uncertainty track and days counter.
- **Acceptance Criteria**:
  - Predicts central date: $\text{centralDate} = \text{lastStartDate} + \text{round}(\bar{L})$, where $\bar{L}$ is the mean of the most recent $\le 6$ valid cycle lengths.
  - Calculates window half-radius $R = \lceil 1.96 \cdot \sqrt{\sigma^2_{\text{eff}}} \rceil$, clamped to $[3, 22]$ days, applying Empirical Bayes shrinkage:
    $$\sigma^2_{\text{shrunk}} = \frac{n \cdot s^2 + w_{\text{prior}} \cdot \sigma^2_{\text{prior}}}{n + w_{\text{prior}}}$$
  - Distinguishes explicit non-estimate states: `NeedsMoreHistory` ($<2$ cycle starts) and `IntervalsOutOfRange` (all intervals $<15$ or $>90$ days).
  - Renders a horizontal visual track showing lead days relative to the uncertainty interval.
  - Explains the calculation basis (number of cycles used and variability status).
- **References**: `TodayScreen.kt`, `fr.luteal.core.model.CycleEstimateCalculator`, `CycleEstimate.kt`.

### US-TDY-03: Conservative Cycle Phase State Derivation
- **Persona**: Solo Tracker, Duo Sharer
- **Preconditions**: Active cycle exists.
- **Description**: Derives the current physiological phase while maintaining conservative boundaries.
- **Acceptance Criteria**:
  - `MENSTRUAL` (Certainty: RECORDED): Returned on Cycle Day 0 or when active bleeding is logged.
  - Early cycle days 1–6 without bleeding detail: Returns `Indeterminate(EARLY_CYCLE_WITHOUT_BLEEDING_DETAIL)`.
  - Next period window: If $\text{today} \ge \text{estimate.earliestDate}$, returns `NEXT_PERIOD_WINDOW` (or `ESTIMATE_EXPIRED` if past `latestDate`).
  - `FOLLICULAR` (ESTIMATED): Returned before the calculated ovulation window.
  - `LUTEAL` (ESTIMATED): Returned after the calculated ovulation window.
  - `OVULATORY` (ESTIMATED): Only returned on the central ovulation day if cycle history is stable ($\ge 6$ cycles, variability $\le 7$ days); otherwise marked `Indeterminate(PHASE_TRANSITION)`.
- **References**: `TodayScreen.kt`, `fr.luteal.core.model.CurrentCyclePhaseCalculator`, `CurrentCyclePhase.kt`, `CyclePhase.kt`.

### US-TDY-04: Sourced Daily Clinical Tips & Physiological Facts
- **Persona**: Solo Tracker, Duo Partner
- **Preconditions**: Date is resolved.
- **Description**: Renders a daily evidence-based clinical tip tailored to phase, context, and symptoms, or a general scientific fact if phase is indeterminate.
- **Acceptance Criteria**:
  - Prioritizes tips matching active phase, adding +3 score for declared tracking contexts and +2 score for symptoms logged in the past 3 days.
  - Resolves daily item using deterministic date-scattering hashing: $\text{index} = (\text{epochDay} \times 2654435761) \pmod N$.
  - Cites authoritative sources (HAS, CNGOF, Inserm, ACOG, ESHRE, WHO) with readable clinical references.
- **References**: `TodayScreen.kt`, `fr.luteal.core.model.PhaseTips`, `CycleFacts.kt`, `ClinicalSources.kt`.

### US-TDY-05: Today Observation Summary & Quick Entry Launcher
- **Persona**: Solo Tracker
- **Preconditions**: Today screen visible.
- **Description**: Displays summary pills for observations logged today and provides instant actions to edit today's entry or record a new period start.
- **Acceptance Criteria**:
  - Renders pills for Flow, Pain, Mood, Energy, and a private note excerpt.
  - "Record period start" button launches the entry sheet with `startPeriodIntent = true`.
- **References**: `TodayScreen.kt`, `DailyEntrySheet.kt`.

---

## Epic 3: Daily Logging & Biomarker Observation

### US-LOG-01: Bleeding Flow Intensity Logging & Cycle Start Marking
- **Persona**: Solo Tracker
- **Preconditions**: Daily entry bottom sheet opened for a selected date.
- **Description**: The user logs menstrual flow intensity and optionally flags the day as the start of a new cycle.
- **Acceptance Criteria**:
  - 5 flow levels: `NONE`, `SPOTTING`, `LIGHT`, `MEDIUM`, `HEAVY`.
  - Checking "This day marks the start of my period" creates or adjusts cycle start boundaries in Room, automatically reconciling contiguous cycle intervals.
- **References**: `DailyEntrySheet.kt`, `fr.luteal.core.model.BleedingIntensity`, `CycleRepositoryImpl.kt`.

### US-LOG-02: Subjective Scale Logging (Pain, Mood, Energy)
- **Persona**: Solo Tracker
- **Preconditions**: Daily entry sheet open.
- **Description**: Logs subjective well-being on 1–5 graduated scales.
- **Acceptance Criteria**:
  - Pain: 1 (None) to 5 (Very strong).
  - Mood: 1 (Very difficult) to 5 (Very good).
  - Energy: 1 (Very low) to 5 (Very high).
  - Leaving a scale unselected preserves a `null` (unrecorded) value without assuming neutral defaults.
- **References**: `DailyEntrySheet.kt`, `fr.luteal.core.designsystem.component.ObservationScale`, `DailyEntry.kt`.

### US-LOG-03: Basal Body Temperature (BBT) & Disturbance Logging
- **Persona**: Solo Tracker, Symptothermal Tracker
- **Preconditions**: Biomarker section expanded in entry sheet.
- **Description**: Records waking temperature, measurement time, and physiological disturbance factors.
- **Acceptance Criteria**:
  - Precision stepper: 0.05°C increments (range 34.00°C–42.00°C) or 0.10°F increments (range 93.20°F–107.60°F).
  - Time picker: Stores optional `measuredTime` (`LocalTime`).
  - Disturbance multi-select chips: `FEVER`, `ALCOHOL`, `POOR_SLEEP`, `TIME_SHIFT`, `LATE_MEASUREMENT`, `STRESS`, `MEDICATION`.
  - Flags temperature as disturbed (`isDisturbed == true`) when disturbance tags are present.
- **References**: `DailyEntrySheet.kt`, `fr.luteal.core.model.BasalBodyTemperature`, `TemperatureInput.kt`.

### US-LOG-04: Cervical Mucus Observation Logging
- **Persona**: Solo Tracker, Fertility Awareness Tracker
- **Preconditions**: Biomarker section expanded in entry sheet.
- **Description**: Logs cervical fluid characteristics based on standardized fertility awareness methods.
- **Acceptance Criteria**:
  - Sensation chips: `DRY`, `DAMP`, `WET`, `SLIPPERY`.
  - Texture chips: `STICKY`, `CREAMY`, `EGG_WHITE`, `WATERY`.
- **References**: `DailyEntrySheet.kt`, `fr.luteal.core.model.BiomarkerObservation`.

### US-LOG-05: Rapid Hormonal Test Logging (LH & hCG)
- **Persona**: Solo Tracker
- **Preconditions**: Biomarker section expanded in entry sheet.
- **Description**: Records visual results of rapid diagnostic test strips.
- **Acceptance Criteria**:
  - LH ovulation test: `NEGATIVE`, `LOW`, `PEAK_POSITIVE`, `INDETERMINATE`.
  - hCG pregnancy test: `NEGATIVE`, `POSITIVE`, `FAINT_UNCERTAIN`.
  - Observations remain recorded user inputs and are never presented as clinical confirmations.
- **References**: `DailyEntrySheet.kt`, `BiomarkerObservation.kt`.

### US-LOG-06: Categorized Symptom Selection & Private Freeform Notes
- **Persona**: Solo Tracker
- **Preconditions**: Entry sheet open.
- **Description**: Selects specific physical/emotional symptoms and enters confidential notes.
- **Acceptance Criteria**:
  - Groups symptoms: Pain (cramps, headache, abdominal, backache, muscle aches, pelvic pain), Body & Digestion (bloating, nausea, digestive changes, breast tenderness, acne), Mood & Energy (fatigue, sleep issue, mood changes, anxiety).
  - Includes condition-specific symptoms when tracking contexts are enabled (e.g., pelvic pain outside period for Endometriosis).
  - Multi-line private notes field marked with a visual "Private" shield badge.
- **References**: `DailyEntrySheet.kt`, `ObservationCatalog.kt`, `Symptom.kt`.

### US-LOG-07: Unsaved Changes Interception
- **Persona**: Solo Tracker
- **Preconditions**: User modifies any value in `DailyEntrySheet` and attempts to dismiss or press back.
- **Description**: Prevents accidental data loss by prompting for confirmation.
- **Acceptance Criteria**:
  - Detects dirty state against original loaded values.
  - Displays confirmation dialog offering to discard changes or keep editing.
- **References**: `DailyEntrySheet.kt`.

---

## Epic 4: Historical Analysis, Calendar & Longitudinal Variability (Journal Screen)

### US-JRN-01: Interactive Month Calendar Navigation & Day Inspection
- **Persona**: Solo Tracker
- **Preconditions**: Journal screen $\to$ Calendar tab selected.
- **Description**: Navigates monthly calendar grids displaying bleeding intensity dots, phase color fills, and estimated period bands.
- **Acceptance Criteria**:
  - Month navigation controls: Previous, Next, and "Jump to Today".
  - Selecting any date updates the Day Inspection Card below the grid.
  - Inspection card displays recorded flow drops, cycle start status, 1–5 level bars, symptom tags, private notes, and an "Edit entry" trigger.
- **References**: `JournalScreen.kt`, `fr.luteal.core.designsystem.component.MonthCalendarGrid`, `MonthCalendarProjection.kt`.

### US-JRN-02: Chronological Timeline Log
- **Persona**: Solo Tracker
- **Preconditions**: Journal screen $\to$ Timeline tab selected.
- **Description**: Scrolls through a chronological history of all recorded days grouped by month.
- **Acceptance Criteria**:
  - Renders entries with flow indicators, level meters, symptom count badges, and note indicators.
  - Includes a date picker launcher to jump directly to any historical date.
- **References**: `JournalScreen.kt`.

### US-JRN-03: Longitudinal Cycle Variability & STRAW+10 Swing Detection
- **Persona**: Solo Tracker, Health Clinician
- **Preconditions**: Journal screen $\to$ Variability tab selected.
- **Description**: Displays historical cycle length bars and flags persistent variations matching the Stages of Reproductive Aging Workshop (STRAW+10) criteria.
- **Acceptance Criteria**:
  - Calculates total cycles count, completed cycles count, excluded cycles count, rolling mean, and rolling median.
  - Flags STRAW+10 swings when adjacent completed cycle lengths differ by $\ge 7$ days ($|\text{length}_{i+1} - \text{length}_i| \ge 7$).
  - When $\ge 2$ swings occur within the last 10 cycles, adjusts prediction prior weighting ($w_{\text{prior}} = 0.5$).
  - Tapping any cycle bar opens `CycleDetailBottomSheet` with duration details, swing status, and exclusion controls.
- **References**: `JournalScreen.kt`, `CycleVariabilityVisualizer.kt`, `fr.luteal.core.model.LongitudinalCycleStatsCalculator`.

### US-JRN-04: Historical Cycle Start Editing & Interval Reconciliation
- **Persona**: Solo Tracker
- **Preconditions**: User selects an existing cycle start in Journal or Variability view.
- **Description**: Edits the cycle start date with automatic interval boundary reconciliation.
- **Acceptance Criteria**:
  - Date picker enforces collision detection (cannot pick an existing cycle start date).
  - Updates cycle start date and recalculates adjacent cycles' `endDate` atomically.
- **References**: `CycleManagementDialogs.kt` (`EditCycleDialog`), `CycleRepositoryImpl.kt`.

### US-JRN-05: Cycle Deletion with Boundary Healing
- **Persona**: Solo Tracker
- **Preconditions**: User triggers deletion on an existing cycle start.
- **Description**: Deletes a cycle start record and restores continuity between surrounding cycles.
- **Acceptance Criteria**:
  - Deletes target `CycleEntity` from Room and creates a deletion tombstone for sync.
  - Reconciles adjacent cycles: sets the previous cycle's `endDate` to the deleted cycle's `endDate`.
- **References**: `CycleManagementDialogs.kt` (`DeleteCycleConfirmDialog`), `CycleRepositoryImpl.kt`.

### US-JRN-06: Retroactive Cycle Backfill
- **Persona**: Solo Tracker
- **Preconditions**: Journal screen $\to$ "Backfill cycle" action triggered.
- **Description**: Inserts a historical cycle start date within a 92-day retrospective window.
- **Acceptance Criteria**:
  - Enforces date picker bounds: between $\text{today} - 92\text{ days}$ and $\text{today}$.
  - Reconciles cycle intervals and prevents duplicate start date insertions.
- **References**: `BackfillCycleDialog.kt`, `LutealViewModel.kt`.

### US-JRN-07: Clinical Cycle Exclusion from Estimates
- **Persona**: Solo Tracker
- **Preconditions**: User views a cycle detail bottom sheet or exclusion dialog.
- **Description**: Excludes an anomalous cycle from predictive calculations with a recorded reason.
- **Acceptance Criteria**:
  - Selectable reasons: `ILLNESS`, `MEDICAL_TREATMENT`, `CONTRACEPTION_CHANGE`, `STRESS_OR_TRAVEL`, `OTHER`.
  - Sets `isExcludedFromEstimates = true` and persists `exclusionReason`.
  - Excluded cycles are immediately omitted from `CycleEstimateCalculator` and `LongitudinalCycleStatsCalculator`.
- **References**: `CycleManagementDialogs.kt` (`CycleExclusionDialog`), `fr.luteal.core.model.CycleExclusionReason`, `Cycle.kt`.

---

## Epic 5: Symptothermal & Thermal Shift BBT Analysis

### US-SYM-01: 3-over-6 Thermal Shift & Coverline Evaluation (Sensiplan Rule)
- **Persona**: Solo Tracker, Natural Family Planning User, Clinician
- **Preconditions**: Journal screen $\to$ Thermal tab; active cycle has $\ge 9$ non-disturbed BBT measurements.
- **Description**: Evaluates the temperature curve against the symptothermal 3-over-6 rule to confirm physiological ovulation and establish the coverline.
- **Acceptance Criteria**:
  - Filters out entries where `isDisturbed == true`.
  - Identifies a 6-low-3-high pattern:
    1. 6 consecutive low baseline temperatures followed by 3 consecutive high temperatures.
    2. All 3 high temperatures exceed $\max(\text{sixLows})$.
    3. The 3rd high temperature $\ge \max(\text{sixLows}) + 0.20^\circ\text{C}$ (`MIN_SHIFT_DELTA_CELSIUS`).
  - Evaluates coverline: $\text{coverlineCelsius} = \max(\text{sixLows}) + 0.05^\circ\text{C}$.
  - Returns `ThermalShiftResult.Confirmed(coverlineCelsius, firstHighDay)` or `ThermalShiftResult.None`.
- **References**: `ThermalShiftChart.kt`, `fr.luteal.core.model.ThermalShiftCalculator`, `ThermalShiftResult.kt`.

### US-SYM-02: Interactive Canvas BBT Chart
- **Persona**: Solo Tracker
- **Preconditions**: Journal screen $\to$ Thermal tab.
- **Description**: Renders an interactive Canvas chart plotting basal body temperatures across cycle days.
- **Acceptance Criteria**:
  - Plots temperature points and connecting curves in Celsius or Fahrenheit based on user preference.
  - Renders visual disturbance markers on disturbed readings.
  - Draws horizontal dashed coverline when thermal shift is confirmed.
  - Allows cycling through previous cycles to review historical curves.
- **References**: `ThermalShiftChart.kt`, `JournalScreen.kt`.

---

## Epic 6: Clinical Report Generation & Healthcare Consultation

### US-REP-01: Clinical Consultation Metric Aggregation
- **Persona**: Solo Tracker, Health Clinician
- **Preconditions**: User launches Clinical Report Dialog from Journal or Settings.
- **Description**: Aggregates cycle metrics, pain distributions, bleeding profiles, and symptom occurrences into a clinical consultation structure.
- **Acceptance Criteria**:
  - Cycle Overview: Computes total/completed cycles count, mean length ($\bar{x}$), median length, sample standard deviation ($s = \sqrt{\frac{\sum (x - \bar{x})^2}{n-1}}$ for $n \ge 3$), min-max range, and mean bleeding duration.
  - Bleeding Profile: Counts spotting, light, medium, and heavy bleeding days, and isolates intermenstrual bleeding ($\ge 7$ days from cycle start).
  - Pain Segregation: Strictly segregates dysmenorrhea (pain on bleeding days) from non-menstrual pelvic pain (pain on non-bleeding days); counts severe pain days ($\text{pain} \ge 4/5$).
  - Symptom Matrix: Computes occurrence counts for all logged symptoms partitioned by menstrual vs. non-menstrual/luteal phases.
- **References**: `ClinicalReportDialog.kt`, `fr.luteal.core.data.ClinicalReportAggregator`, `ClinicalReport.kt`.

### US-REP-02: Multi-Format Clinical Report Export (PDF & HTML)
- **Persona**: Solo Tracker, Health Clinician
- **Preconditions**: Clinical report configuration selected in `ClinicalReportDialog`.
- **Description**: Generates and saves a paginated vector PDF or responsive HTML clinical report file via Android Storage Access Framework (SAF).
- **Acceptance Criteria**:
  - Presets: Last 3 cycles, Last 6 cycles, Last 12 cycles, All cycles.
  - Language: French (`fr`) or English (`en`) with standardized clinical terminology mappings.
  - Notes Toggle: User explicitly chooses whether to include or omit private notes.
  - PDF: Renders vector typography, structured metric boxes, and clinical disclaimers via Android `PdfDocument`.
  - HTML: Generates self-contained semantic HTML5 with custom `@media print` stylesheets.
  - Emits file via `ActivityResultContracts.CreateDocument`.
- **References**: `ClinicalReportDialog.kt`, `fr.luteal.core.data.report.PdfReportBuilder`, `HtmlReportBuilder.kt`.

---

## Epic 7: Duo Consensual Partner Sharing & Support Requests

### US-DUO-01: Zero-Knowledge Duo Invitation Generation
- **Persona**: Duo Sharer (Primary Tracker)
- **Preconditions**: Duo screen $\to$ No active link established.
- **Description**: The primary tracker creates a cryptographic pairing invite with the encryption key embedded exclusively in the URL fragment.
- **Acceptance Criteria**:
  - Generates a 256-bit random AES-GCM link key via `SecureRandom`.
  - Constructs pairing URL: `https://.../v1/duo/pair#key=<base64url_link_key>`.
  - Registers link ID with folicular backend (`POST /v1/duo/links`); the key in the URL fragment never reaches the server.
  - Displays copy-to-clipboard button and pending invitation status with cancel/revoke action.
- **References**: `DuoScreen.kt`, `DuoViewModel.kt`, `fr.luteal.core.network.crypto.DuoCrypto`, `DuoKeyStore.kt`.

### US-DUO-02: Duo Pairing Invitation Acceptance
- **Persona**: Duo Partner
- **Preconditions**: Partner launches Duo screen in Partner Viewer mode with a pairing URL.
- **Description**: The partner accepts an invite by parsing the URL fragment to extract and store the link key locally.
- **Acceptance Criteria**:
  - Extracts 256-bit link key from URL fragment and persists it in Android KeyStore (`KeystoreSecretStore`).
  - Calls `POST /v1/duo/links/{id}/accept` to complete pairing.
  - Transitions partner UI to active paired state.
- **References**: `DuoScreen.kt`, `DuoViewModel.kt`, `DuoCrypto.kt`.

### US-DUO-03: Granular Sharing Permissions & Real-time Partner Preview
- **Persona**: Duo Sharer (Primary Tracker)
- **Preconditions**: Active Duo link established.
- **Description**: The tracker configures independent category-level sharing toggles and verifies exactly what the partner sees.
- **Acceptance Criteria**:
  - Independent permission toggles: Current Cycle Day, Next Period Estimate, Mood, Energy, Support Requests.
  - Enforced client-side before sealing: ungranted fields are stripped before encryption.
  - Sealed projection layout: `0x01 || nonce (12B) || ciphertext || tag (16B)` with AAD bound to `link_id`.
  - Renders a live preview card showing the partner's exact visible state.
  - Hard constraint: Private notes and raw symptom logs are never included in the projection schema.
- **References**: `DuoScreen.kt`, `DuoViewModel.kt`, `fr.luteal.core.model.DuoSharingPreferences`, `DuoProjection.kt`.

### US-DUO-04: Partner Dashboard & Phase-Tailored Guidance
- **Persona**: Duo Partner
- **Preconditions**: Active Duo link with shared tracker projection.
- **Description**: The partner views the tracker's shared status and receives non-prescriptive, evidence-based guidance.
- **Acceptance Criteria**:
  - Displays granted metrics: Cycle Day, Estimated Period Window, Mood, Energy, with freshness badge (Current, Aging, Stale).
  - Displays partner phase tips (comfort, listening, space, condition empathy) citing clinical sources.
  - Never makes assumptions about partner availability or prescribes behavior.
- **References**: `DuoScreen.kt`, `DuoViewModel.kt`, `fr.luteal.core.model.PartnerPhaseGuidance`, `PartnerPhaseTips.kt`.

### US-DUO-05: Bidirectional Quick Support Nudges & Acknowledgment
- **Persona**: Duo Sharer, Duo Partner
- **Preconditions**: Active Duo link with support requests permission granted.
- **Description**: Allows sending pre-composed support messages and one-tap partner support nudges.
- **Acceptance Criteria**:
  - Tracker can send support requests across 4 categories: `COMFORT`, `PRACTICAL`, `SPACE`, `GENERAL`.
  - Partner can send 6 quick support nudges: Groceries, Cook dinner, Quiet evening, Warm drink, Here if needed, Take rest.
  - Incoming messages support one-tap acknowledgment (`ack`), updating message state across devices.
- **References**: `DuoScreen.kt`, `DuoViewModel.kt`, `fr.luteal.core.model.QuickSupportNudge`.

### US-DUO-06: Instant Duo Link Revocation
- **Persona**: Duo Sharer (Primary Tracker)
- **Preconditions**: Active Duo link.
- **Description**: Instantly severs the sharing relationship from the tracker device.
- **Acceptance Criteria**:
  - Calls `DELETE /v1/duo/links/{id}` on backend.
  - Wipes local link keys from Keystore and clears cached partner projection in Room.
  - UI immediately reverts to unlinked state.
- **References**: `DuoScreen.kt`, `DuoViewModel.kt`, `DuoRepository.kt`.

### US-DUO-07: Duo Projection Freshness Tracking & Stale Cache Badging
- **Persona**: Duo Partner
- **Preconditions**: Cached Duo projection exists in local database.
- **Description**: Informs the partner of the recency of shared data to prevent acting on outdated projections.
- **Acceptance Criteria**:
  - Calculates freshness status via `WidgetFreshness.of(refreshedAt, now)`:
    * `CURRENT`: Refresh occurred within the past 24 hours ("Updated %s").
    * `AGING`: Refresh occurred between 24 and 48 hours ago ("Last updated %s").
    * `STALE`: Refresh occurred $>48$ hours ago ("Information may be outdated, last updated %s").
  - Provides manual "Refresh" button triggering `POST /v1/duo/links/{id}/pull`.
- **References**: `DuoScreen.kt`, `DuoViewModel.kt`, `fr.luteal.app.widget.WidgetModels.kt`.

---

## Epic 8: Home Screen Widgets & Glanceable Experiences

### US-WDG-01: Personal Cycle Glanceable Widget
- **Persona**: Solo Tracker
- **Preconditions**: User places "Luteal · Mon cycle" widget on Android home screen.
- **Description**: Displays glanceable cycle day, phase, and estimated period window directly on the home screen.
- **Acceptance Criteria**:
  - Implemented with Jetpack Glance (`GlanceAppWidget`).
  - Responsive layout breakpoints:
    * Compact ($\ge 110 \times 72\text{ dp}$): Primary cycle day and privacy toggle.
    * Standard ($\ge 180 \times 110\text{ dp}$): Cycle day, phase, and estimated range.
    * Wide ($\ge 250 \times 110\text{ dp}$): Two-column recorded status and estimated forecast.
    * Expanded ($\ge 250 \times 180\text{ dp}$): Full status, countdown, and quick-entry action.
  - Discreet mode toggle: Tap eye icon to conceal health data; renders neutral placeholder copy without sending physiological data to launcher `RemoteViews`.
- **References**: `app/src/main/java/fr/luteal/app/widget/personal/PersonalCycleWidget.kt`, `WidgetSnapshotFactory.kt`, `HOME_SCREEN_WIDGETS.md`.

### US-WDG-02: Duo Partner Glanceable Widget
- **Persona**: Duo Partner
- **Preconditions**: User places "Luteal · Duo" widget on Android home screen.
- **Description**: Displays the partner's shared cycle day and estimate status without leaking unshared categories.
- **Acceptance Criteria**:
  - Reads exclusively from `DuoWidgetCacheDao` (derived from encrypted `DuoProjection`); never accesses primary tracker Room tables.
  - Never advances the shared cycle day locally if no new projection is received.
  - Displays freshness indicator and discreet concealment toggle.
- **References**: `app/src/main/java/fr/luteal/app/widget/duo/DuoCycleWidget.kt`, `WidgetSnapshotFactory.kt`.

### US-WDG-03: Widget Maintenance & Midnight Refresh
- **Persona**: Solo Tracker, Duo Partner
- **Preconditions**: Widgets active on home screen.
- **Description**: Ensures widget data stays fresh across date changes and system events.
- **Acceptance Criteria**:
  - Coalesces Room and DataStore updates while app is active.
  - Schedules `WidgetMaintenanceWorker` via WorkManager to trigger refresh immediately following local midnight.
  - Listens for `ACTION_DATE_CHANGED`, `ACTION_TIMEZONE_CHANGED`, `ACTION_TIME_CHANGED`, `ACTION_LOCALE_CHANGED`.
- **References**: `WidgetMaintenanceWorker.kt`, `WidgetSystemEventReceiver.kt`, `WidgetUpdateCoordinator.kt`.

---

## Epic 9: Discrete Local Notifications & Reminders

### US-NOT-01: Daily Observation Check-in Reminder with Smart Suppression
- **Persona**: Solo Tracker
- **Preconditions**: Daily reminder enabled in Settings.
- **Description**: Sends a daily reminder to log observations at a user-configured time, intelligently suppressed if observations were already logged today.
- **Acceptance Criteria**:
  - Selectable times: 08:00, 09:00, 12:00, 20:00, 20:30, 21:00, 21:30, 22:00, 22:30.
  - Schedules exact alarms via Android `AlarmManager` with `NotificationAlarmReceiver`.
  - Smart suppression: When alarm fires, verifies if a `DailyEntry` exists for today; skips notification if already logged.
  - Non-judgmental copy: avoids guilt-based language or streaks.
- **References**: `SettingsScreen.kt`, `fr.luteal.app.notification.NotificationScheduler`, `NotificationChannelManager.kt`.

### US-NOT-02: Period Window & Late Cycle Prompts
- **Persona**: Solo Tracker
- **Preconditions**: Period window notifications enabled in Settings.
- **Description**: Alerts the user prior to an estimated period window or prompts when a cycle exceeds the expected range.
- **Acceptance Criteria**:
  - Lead time selection: 1, 2, or 3 days before earliest estimated date.
  - Late cycle prompt: triggers after estimated latest date with a configured grace period.
- **References**: `SettingsScreen.kt`, `NotificationScheduler.kt`.

### US-NOT-03: Notification Content Concealment & Privacy Modes
- **Persona**: Privacy-conscious User
- **Preconditions**: Notification settings open.
- **Description**: Controls the visibility of physiological terms in lock screen notifications.
- **Acceptance Criteria**:
  - `CONCEALED`: Neutral generic text ("Luteal • Daily check-in") containing no menstrual or cycle terminology.
  - `DESCRIPTIVE`: Clear physiological terms ("Your estimated period window begins in 2 days").
  - `CUSTOM`: User provides custom title and body text to completely disguise notifications.
- **References**: `SettingsScreen.kt`, `fr.luteal.app.notification.NotificationContentResolver`, `NotificationVisibility.kt`.

---

## Epic 10: Local Security, PIN Authentication & Screen Masking

### US-SEC-01: Hardware-Backed PBKDF2 PIN Lock
- **Persona**: Privacy-conscious User
- **Preconditions**: User enables App Lock in Settings.
- **Description**: Configures a 4 to 8-digit numeric PIN hashed with PBKDF2 and secured via Android KeyStore.
- **Acceptance Criteria**:
  - Cryptographic derivation: PBKDF2-HMAC-SHA256, 100,000 iterations, 256-bit key, 16-byte `SecureRandom` salt.
  - Salt and hash persisted in `KeystoreSecretStore`.
  - Pin verification uses constant-time byte comparison (`MessageDigest.isEqual`).
  - AppLockScreen intercepts cold starts and app resume with animated PIN dots, auto-submit on expected length, error shake animations, and haptic feedback.
- **References**: `SettingsScreen.kt`, `AppLockScreen.kt`, `fr.luteal.core.data.security.PinCryptoManager`, `PinSecretStore.kt`.

### US-SEC-02: Dual-Clock Anti-Tamper Lockout Defense
- **Persona**: Privacy-conscious User
- **Preconditions**: 5 consecutive incorrect PIN entries.
- **Description**: Enforces progressive time-based lockouts protected against device clock manipulation.
- **Acceptance Criteria**:
  - Lockout progression: 5 failures $\to$ 30s, 6 failures $\to$ 60s, 7 failures $\to$ 120s, $\ge 8$ failures $\to$ 300s.
  - Dual-clock verification: Computes remaining lockout time from both wall-clock epoch millis (`lockoutUntilEpochMillis`) and monotonic elapsed real-time (`SystemClock.elapsedRealtime`). Lockout is enforced while $\max(\Delta_{\text{wall}}, \Delta_{\text{monotonic}}) > 0$.
  - Prevents defeating lockout by changing system time or rebooting.
- **References**: `AppLockScreen.kt`, `fr.luteal.core.data.security.AppLockManager`.

### US-SEC-03: Biometric Authentication Integration
- **Persona**: Privacy-conscious User
- **Preconditions**: Device supports hardware biometrics; PIN lock configured.
- **Description**: Unlocks the application using Fingerprint or Face authentication.
- **Acceptance Criteria**:
  - Integrates AndroidX `BiometricPrompt` (`BIOMETRIC_STRONG` | `BIOMETRIC_WEAK`).
  - Automatically triggers prompt upon lock screen display when enabled.
- **References**: `SettingsScreen.kt`, `AppLockScreen.kt`, `MainActivity.kt`.

### US-SEC-04: Auto-Lock Background Timeout
- **Persona**: Privacy-conscious User
- **Preconditions**: App Lock enabled.
- **Description**: Configures the elapsed background duration before the app locks itself.
- **Acceptance Criteria**:
  - Selectable thresholds: `IMMEDIATE` (0s), `ONE_MINUTE` (60s), `FIVE_MINUTES` (300s).
  - Activity lifecycle observer ignores configuration changes (e.g. screen orientation).
- **References**: `SettingsScreen.kt`, `AutoLockTimeout.kt`, `AppLockManager.kt`.

### US-SEC-05: Screen Masking (FLAG_SECURE)
- **Persona**: Privacy-conscious User
- **Preconditions**: Settings $\to$ Screen masking toggle.
- **Description**: Prevents screen capture and obscures app preview thumbnails in the Android Recents task switcher.
- **Acceptance Criteria**:
  - Toggling setting dynamically sets or clears `WindowManager.LayoutParams.FLAG_SECURE` in `MainActivity`.
  - Blocks screenshots, screen recordings, and Recents switcher snapshot retention.
- **References**: `SettingsScreen.kt`, `MainActivity.kt`.

---

## Epic 11: Data Portability, Backup, Restore & GDPR Local Erasure

### US-DAT-01: Structured JSON Data Backup Export
- **Persona**: Privacy-conscious User, Solo Tracker
- **Preconditions**: Local data present.
- **Description**: Generates an unencrypted, versioned JSON backup containing all health records and user settings.
- **Acceptance Criteria**:
  - Schema versioning: `schema_version = 1`, ISO-8601 UTC timestamp, application version.
  - Complete payload serialization: cycles, period days, daily entries, symptom logs, BBT readings and disturbances, cervical fluid observations, rapid tests, and preferences.
  - Exports via Android SAF `CreateDocument`.
- **References**: `SettingsScreen.kt`, `fr.luteal.core.data.DataExportManager`, `LutealBackupPayload.kt`.

### US-DAT-02: Backup Inspection, Validation & Strategy-Based Restore
- **Persona**: Privacy-conscious User
- **Preconditions**: User selects a backup JSON file via SAF `OpenDocument`.
- **Description**: Inspects backup contents, displays a preview, and restores records using Merge/Upsert or Replace All.
- **Acceptance Criteria**:
  - Inspection: Generates `LutealBackupPreview` with record counts and date ranges without altering the database.
  - Validation: Rejects corrupted JSON syntax and unsupported schema versions.
  - `REPLACE_ALL`: Atomic transaction clears all tables, inserts backup records, generates deletion tombstones for deleted local records, and restores preferences.
  - `MERGE_UPSERT`: Non-destructively merges records based on `updatedAtEpochMillis` (updates only if backup is newer), preserving non-conflicting local entries.
- **References**: `SettingsScreen.kt`, `fr.luteal.core.data.DataImportManager`, `LutealBackupPayload.kt`.

### US-DAT-03: Complete Local Data Purge (GDPR Local Erasure)
- **Persona**: Privacy-conscious User
- **Preconditions**: Settings $\to$ "Delete all data" selected.
- **Description**: Irreversibly purges all health data, preferences, credentials, and encryption keys from the device.
- **Acceptance Criteria**:
  - Prompts with confirmation dialog.
  - Executes `database.clearAllTables()`.
  - Wipes `UserPreferencesDataStore` and `SyncDataStore`.
  - Clears KeyStore secrets (`SyncCredentialStore`, `DuoKeyStore`, `PinCryptoManager`).
- **References**: `SettingsScreen.kt`, `fr.luteal.core.data.LocalDataPurgeManager`.

---

## Epic 12: Zero-Knowledge E2EE Cloud Sync & Multi-Device Account Recovery

### US-SYN-01: Zero-Knowledge Account Creation & Key Derivation
- **Persona**: Solo Tracker, Multi-device User
- **Preconditions**: Settings $\to$ Online Cloud Sync enabled.
- **Description**: Registers an anonymous cloud account and derives a 256-bit AES master key from a human-readable account code.
- **Acceptance Criteria**:
  - Generates a 100-bit random Crockford Base32 account code: `LTL-XXXX-XXXX-XXXX-XXXX`.
  - Derives keys using HKDF-SHA256 (RFC 5869):
    * $\text{MasterKey} = \text{HKDF-Extract}(\text{IKM}=\text{normalizeCode}(\text{code}), \text{salt}=\text{account\_id}, \text{info}=\text{"luteal/v1/master"})$
    * $\text{RecordKey} = \text{HKDF-Expand}(\text{PRK}=\text{MasterKey}, \text{info}=\text{"luteal/v1/record"})$
  - Calls `POST /v1/auth/register` with random device label (e.g. `lilas-discret`); never sends device hardware identifiers (`Build.MODEL`).
  - The server never receives or stores the account code or encryption keys.
- **References**: `SettingsScreen.kt`, `fr.luteal.core.network.crypto.RecordCrypto`, `Hkdf.kt`, `EncryptedSyncCredentialStore.kt`, `DeviceLabel.kt`.

### US-SYN-02: Multi-Device Account Recovery via Account Code
- **Persona**: Multi-device User
- **Preconditions**: User enters existing 24-character account code on a secondary device.
- **Description**: Recovers account and derives record keys locally to pull and decrypt all historical data.
- **Acceptance Criteria**:
  - Validates Crockford Base32 format (handles case-insensitivity and hyphen normalization).
  - Derives Master and Record keys locally.
  - Calls `POST /v1/auth/devices` (`addDevice`) to register the secondary device token.
  - Initiates full delta sync pull to populate local database.
- **References**: `SettingsScreen.kt`, `SettingsViewModel.kt`, `FolicularApiClient.kt`.

### US-SYN-03: End-to-End Encrypted Delta Synchronization
- **Persona**: Solo Tracker, Multi-device User
- **Preconditions**: `SyncMode.ONLINE_CLOUD` enabled.
- **Description**: Synchronizes dirty local records with the cloud backend using authenticated delta envelopes.
- **Acceptance Criteria**:
  - Record Sealing: Sealed with AES-256-GCM with AAD bound to `<entity_type>\0<entity_id>\0<client_rev>` to prevent payload relocation attacks.
  - Timestamp Coarsening: Envelope `created_at`, `updated_at`, `deleted_at` are truncated to minute granularity (`ChronoUnit.MINUTES`) in UTC to prevent leaking user interaction timing patterns.
  - Bleeding Fan-out / Collapse: Reconciles cycle-embedded bleeding models with server row models using deterministic UUIDv7 (`UUID.nameUUIDFromBytes`).
  - Conflict Resolution: Entity-level last-write-wins (LWW) on `updated_at`, tie-broken by `client_rev` UUID. On conflict, the client adopts the server's state.
  - Tombstones: Deletions generate tombstones tracked in `SyncStateEntity`.
- **References**: `fr.luteal.core.network.sync.CycleSyncEngine`, `RecordCrypto.kt`, `RecordSealer.kt`, `SyncWire.kt`.

### US-SYN-04: Background Synchronization Scheduling
- **Persona**: Solo Tracker
- **Preconditions**: Online cloud sync active.
- **Description**: Automatically synchronizes data in the background subject to network constraints.
- **Acceptance Criteria**:
  - Implemented via WorkManager `SyncWorker`.
  - Constrained to `NetworkType.CONNECTED` with exponential backoff retry.
  - Settings screen provides a manual "Sync Now" button with immediate status feedback.
- **References**: `SyncWorker.kt`, `SyncScheduler.kt`, `SettingsScreen.kt`.

---

## Epic 13: System Preferences, Appearance & Accessibility

### US-SYS-01: Basal Temperature Unit Preference (°C / °F)
- **Persona**: Solo Tracker, Symptothermal Tracker
- **Preconditions**: Settings $\to$ Temperature unit setting.
- **Description**: Configures global temperature unit across daily logging, historical thermal charts, and exports.
- **Acceptance Criteria**:
  - Supports `CELSIUS` and `FAHRENHEIT`.
  - Converts stored native Celsius hundredths into user-selected unit on display and chart axes.
- **References**: `SettingsScreen.kt`, `fr.luteal.core.model.TemperatureInput`, `UserPreferencesDataStore.kt`.

### US-SYS-02: Device System Theme Dynamic Switching (Light / Dark)
- **Persona**: Solo Tracker, Duo Partner
- **Preconditions**: System display theme toggled (Light vs Dark mode).
- **Description**: UI adapts dynamically to device light/dark configuration without forcing dark backgrounds.
- **Acceptance Criteria**:
  - Supports dynamic theme palettes via `LutealTheme`.
  - High-contrast accessible color tokens for both light and dark themes.
- **References**: `fr.luteal.core.designsystem.theme.Color.kt`, `MainActivity.kt`.

### US-SYS-03: Bilingual Shipped Localization (French Primary / English Default Parity)
- **Persona**: French & International Users
- **Preconditions**: Device locale set to `fr` or any other language.
- **Description**: Complete parity of user-facing copy, accessibility descriptions, and clinical terms between French (`values-fr`) and English default (`values`).
- **Acceptance Criteria**:
  - Pinning to `fr` and `en` in `locales_config.xml`.
  - Zero hardcoded UI strings; 100% parity verified by tests.
  - Formats dates using `LocalizedDateFormatter` resolving UI locale dynamically.
- **References**: `res/values/strings.xml`, `res/values-fr/strings.xml`, `LocalizedDateFormatter.kt`.

### US-SYS-04: Privacy-Preserving Crash Reporting
- **Persona**: All Users
- **Preconditions**: Uncaught application exception / crash.
- **Description**: Prompts the user with an explicit consent dialog to optionally dispatch a crash report via email without transmitting health data.
- **Acceptance Criteria**:
  - ACRA (Application Crash Reports for Android) initialization in `LutealApp`.
  - Strips all SQLite databases, Room tables, and personal records.
  - Prompts with `crash_dialog_title` and `crash_dialog_text`.
  - Dispatches only stack trace and device OS version upon explicit user action via email intent.
- **References**: `app/src/main/java/fr/luteal/app/LutealApp.kt`, `R.string.crash_dialog_text`.

---

## Epic 14: Developer, QA & Diagnostic Tooling

### US-DEV-01: Local Backend Base URL Configuration (Debug Builds)
- **Persona**: Developer, Self-hoster
- **Preconditions**: `BuildConfig.DEBUG == true` or advanced settings.
- **Description**: Customizes the folicular backend base URL to target local development servers (e.g., `http://10.0.2.2:8080`).
- **Acceptance Criteria**:
  - Editable URL field in Settings $\to$ Synchronisation.
  - Persisted in `SyncDataStore` with instant client reconfiguration.
- **References**: `SettingsScreen.kt`, `SettingsViewModel.kt`, `SyncDataStore.kt`.

### US-DEV-02: Multi-Cycle Realistic Test Data Seeder
- **Persona**: Developer, QA Tester
- **Preconditions**: `BuildConfig.DEBUG == true`.
- **Description**: Populates the database with 1 year of realistic physiological cycle history with varying lengths, symptoms, and BBT curves.
- **Acceptance Criteria**:
  - "Seed test data" button generates 12 completed cycles with realistic bleeding distributions, varied symptom logs, and biphasic BBT curves.
  - "Clear test data" button wipes seeded entities cleanly.
- **References**: `SettingsScreen.kt`, `fr.luteal.core.data.seed.TestDataSeeder`.

### US-DEV-03: ADB CLI Backup Import Bridge
- **Persona**: Automated Test Runner, Power User
- **Preconditions**: Device connected via ADB; app installed.
- **Description**: Ingests JSON backup payloads directly via Android debug shell commands.
- **Acceptance Criteria**:
  - Guarded by `android.permission.DUMP` permission.
  - Handles `android.intent.action.VIEW` intents carrying backup payload strings.
  - Parses and restores payload through `DataImportManager`.
- **References**: `app/src/main/java/fr/luteal/app/AdbBackupImportActivity.kt`, `DataImportManager.kt`.
