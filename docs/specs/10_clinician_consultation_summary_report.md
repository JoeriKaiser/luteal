# Spec 10: Clinician Consultation Summary Report Export

## Problem Statement

When consulting a gynecologist, midwife (*sage-femme*), endocrinologist, or general practitioner (*médecin traitant*)—particularly for investigations into chronic conditions such as endometriosis, polycystic ovary syndrome (PCOS / *SOPK*), premenstrual dysphoric disorder (PMDD / *TDPM*), abnormal uterine bleeding (AUB), or fertility investigations—patients need to present a clear, factual history of their cycles and symptoms. 

In clinical practice, routine outpatient consultations are strictly time-constrained (often lasting only 15 to 20 minutes). When a patient attempts to communicate their menstrual history by scrolling through interactive daily calendar views or journal entry lists on a smartphone screen, the process is inefficient, stressful, and overwhelming for both the patient and the clinician. Important patterns—such as cycle length variability, the relationship between bleeding episodes and pain onset, the prevalence of non-menstrual pelvic pain, or premenstrual symptom clusters—are obscured by fragmented day-by-day navigation.

Furthermore, existing third-party menstrual tracking applications frequently produce exports that fail clinical needs:
1. They export raw, unformatted CSV or JSON dumps that require manual data manipulation and cannot be quickly scanned during a consultation.
2. They generate proprietary, visual marketing infographics cluttered with ungrounded algorithmic predictions, fertility scores, or presumptive diagnostic labels (e.g., "High risk of PCOS"), which violate clinical boundaries and distract from factual observation.
3. They rely on remote cloud servers to render documents, transmitting unencrypted, intimate health logs to third-party endpoints.

In alignment with Luteal's "Quiet Instrument" philosophy, users require a native, 100% on-device export engine that synthesizes recorded cycles, bleeding profiles, pain scores, symptom occurrences, and medication notes into a structured, printable, medical-grade summary document (PDF and HTML) formatted to French and international clinical standards.

---

## Solution

Luteal will provide a dedicated **Clinician Consultation Summary Report** (*Récapitulatif de consultation médicale*) feature that aggregates historical tracking data into a standardized, scannable, and printable document generated entirely on-device.

### 1. Document Architecture & Content Sections

The generated document (PDF and HTML) is organized into six structured clinical sections:

```
+-------------------------------------------------------------------------+
| LUTEAL - RÉCAPITULATIF DE CONSULTATION / CLINICAL SUMMARY REPORT        |
| Période : 15 janv. 2026 - 15 août 2026 (6 cycles) | Émis le : 15/08/2026|
+-------------------------------------------------------------------------+
| 1. SYNTHÈSE DES CYCLES / CYCLE METRICS OVERVIEW                          |
|    - Nombre de cycles : 6           - Durée moyenne : 29.2 j (± 2.4 j)  |
|    - Médiane : 29.0 j               - Étendue (Min - Max) : 26 j - 33 j |
|    - Durée moy. des règles : 4.8 j  - Saignements intermenstruels : 2 j |
|                                                                         |
|    [Tableau chronologique des cycles : Date début, Durée, Règles, Flux] |
+-------------------------------------------------------------------------+
| 2. PROFIL DES SAIGNEMENTS / BLEEDING PROFILE                            |
|    - Répartition des intensités (Spotting, Léger, Moyen, Abondant)      |
|    - Épisodes de saignements hors règles (métrorragies / spotting)      |
+-------------------------------------------------------------------------+
| 3. ÉVALUATION DES DOULEURS / PAIN DYNAMICS                              |
|    - Douleurs menstruelles (dysménorrhée) vs Douleurs hors règles       |
|    - Échelle d'intensité (1-5) & Jours avec retentissement quotidien    |
+-------------------------------------------------------------------------+
| 4. MATRICE DE FRÉQUENCE DES SYMPTÔMES / SYMPTOM MATRIX                  |
|    - Fréquence des observations somatiques et émotionnelles             |
|    - Répartition par phase : Menstruelle vs Non-menstruelle / Lutéale   |
+-------------------------------------------------------------------------+
| 5. TRAITEMENTS ET NOTES DU PATIENT / MEDICATIONS & NOTES                |
|    - Chronologie des antalgiques, anti-inflammatoires et notes clés     |
+-------------------------------------------------------------------------+
| 6. MENTION LÉGALE & DÉCHARGE / CLINICAL DISCLAIMER                      |
|    "Document purement descriptif établi à partir des observations       |
|     saisies par la personne. Ne constitue pas un diagnostic médical."   |
+-------------------------------------------------------------------------+
```

1. **Cycle Statistics Table (*Synthèse des cycles*)**:
   - Total number of completed cycles within the selected window.
   - Cycle length metrics: mean length ($\bar{x}$), sample standard deviation ($s$), median, minimum, and maximum cycle lengths.
   - Menstrual bleeding metrics: mean bleeding duration, range (min–max days), and frequency of heavy bleeding days.
   - Individual cycle history table listing: Cycle index, Start date, End date, Cycle length (days), Bleeding duration (days), Peak flow intensity, and Number of pain days recorded.
2. **Bleeding & Flow Profile (*Profil des saignements*)**:
   - Breakdown of bleeding days by intensity level: *Spotting* (*Spotting / Traces*), *Light* (*Léger*), *Medium* (*Moyen*), *Heavy* (*Abondant*).
   - Dedicated counter and date list for intermenstrual bleeding (*saignements intermenstruels / métrorragies*), defined as bleeding/spotting recorded outside the primary menstrual bleeding window.
3. **Pain Dynamics & Severity (*Dynamique des douleurs*)**:
   - Frequency and severity distribution of pain scores recorded across cycles.
   - Clear clinical distinction between **menstrual pain** (dysmenorrhea occurring during bleeding days) and **non-menstrual pelvic pain** (pelvic pain, cramping, or backache occurring during follicular, ovulatory, or luteal phases).
   - Tally of days with severe or activity-limiting pain.
4. **Symptom Frequency Matrix (*Matrice des symptômes*)**:
   - Tabulation of somatic and neurovegetative symptoms (nausea, abdominal pain, migraines/headaches, digestive changes, muscle aches, fatigue, sleep disturbances, mood changes) sourced from clinical registers.
   - Segregation of symptom occurrence by cycle timing (Menstrual phase vs. Non-menstrual / Premenstrual phase) to facilitate evaluation of cyclic versus continuous symptom patterns.
5. **Medications, Interventions & Patient Notes (*Notes et traitements*)**:
   - Optional, user-selected inclusion of chronological notes log focusing on medication usage (analgesics, NSAIDs, antispasmodics, hormonal treatments) and contextual events.
6. **Factual Clinical Disclaimer (*Avertissement clinique*)**:
   - Neutral, unambiguous statement affirming that the document is a factual synthesis of self-reported observations and does not constitute a clinical interpretation or diagnostic evaluation.

### 2. Dual-Format Generation (PDF & HTML)
- **Native Android `PdfDocument`:** Generates high-resolution, vector-drawn, paginated PDF documents directly in device memory using Android's native graphics engine (`Canvas`, `Paint`, `StaticLayout`). Follows standard A4 (and US Letter) paper dimensions with 15 mm print-safe margins, clean monochrome/grayscale data tables, and running headers/footers with dynamic page numbering ("Page X sur Y").
- **Standalone HTML Report:** Generates an offline, self-contained HTML5 file with embedded responsive styles and print media queries (`@media print`), enabling seamless viewing or printing from desktop browsers, hospital terminals, or email attachments.

### 3. Privacy, Offline-First & Storage Access Framework (SAF)
- 100% on-device computation: No network requests, zero telemetry, zero third-party PDF cloud rendering services.
- Direct file export via Android's `ActivityResultContracts.CreateDocument` (Storage Access Framework), allowing the user to select the destination directory (internal storage, SD card, or local encrypted folders) with standard MIME types (`application/pdf` and `text/html`).

### 4. Bilingual Support with Independent Language Selector
- French-first default with complete English parity.
- Explicit report language toggle in the export dialog: A user can generate a French report (aligned with *Haute Autorité de Santé (HAS)* and *CNGOF* terminology) or an English report (aligned with *ACOG* and *FIGO* terminology) independently of their device's system locale.

---

## User Stories

### Patient & Consultation Preparation
1. As a cycle tracking user preparing for an appointment with my gynecologist, I want to export a printable PDF summary of my last 6 cycles, so that my doctor can quickly review my cycle history during our 15-minute consultation.
2. As a user consulting a midwife (*sage-femme*) regarding cycle irregularity, I want the report to present my mean cycle length, shortest cycle, longest cycle, and variation range in a clear summary table, so that we can evaluate cycle regularity without manual calculations.
3. As a user being evaluated for suspected endometriosis, I want the summary to clearly distinguish between menstrual cramps (dysmenorrhea) and non-menstrual pelvic pain across my cycle, so that my specialist has an objective timeline of pain occurrence outside of menses.
4. As a user experiencing heavy menstrual bleeding (*ménorragies*), I want the report to quantify the number of heavy and medium bleeding days per cycle, so that my physician can assess bleeding volume according to clinical guidelines.
5. As a user experiencing spotting between periods (*métrorragies / saignements intermenstruels*), I want intermenstrual bleeding episodes explicitly highlighted in the report, so that my doctor can investigate potential cervical, uterine, or hormonal causes.
6. As a user experiencing severe premenstrual symptoms (e.g., migraines, nausea, intense fatigue, mood shifts), I want a symptom frequency matrix comparing symptom occurrence during the premenstrual/luteal phase versus the menstrual phase, so that my clinician can evaluate PMS or PMDD patterns.
7. As a user tracking pain medication usage (e.g., ibuprofen, paracetamol, antispasmodics), I want my medication notes included chronologically in the summary, so that my doctor can review which treatments I took and on which cycle days.
8. As a user, I want to select the time window for the report (Last 3 Cycles, Last 6 Cycles, Last 12 Cycles, or Custom Date Range), so that I only share the period relevant to my specific medical inquiry.
9. As a user, I want the option to include or exclude my private freeform notes from the exported document, so that I can protect intimate diary entries while sharing objective cycle metrics.

### Clinician & Presentation Experience
10. As a clinician reviewing the printed report, I want a clean, high-contrast, black-and-white print layout with standard margins, so that the document is immediately legible whether printed on a standard monochrome office printer or viewed on a computer screen.
11. As a clinician, I want standard menstrual metrics (mean, median, standard deviation, min, max) presented using recognized clinical terminology (FIGO, HAS, ACOG), so that the data integrates smoothly into my clinical assessment.
12. As a clinician, I want a clear disclaimer stating that the document is a factual log of user-recorded observations and not an automated algorithmic diagnosis, so that diagnostic responsibilities remain well-defined.
13. As a clinician, I want multi-page reports to include running headers, document generation dates, and consistent page numbering ("Page 1 sur 3"), so that physical paper sheets do not get mixed up or lost in a medical file.

### Localization & Export Formats
14. As a French user consulting an English-speaking specialist abroad, I want to select English as the report language directly in the export dialog without altering my phone's system language, so that my physician receives the report in their language.
15. As an English user consulting a French physician in France, I want to export the report in French with standard French medical terminology (*dysménorrhée, ménorragies, métrorragies*), so that my local doctor can read it effortlessly.
16. As a user who prefers viewing documents in a web browser or attaching files to a telemedicine portal, I want the option to export as a standalone HTML file in addition to PDF, so that I have a lightweight, universally viewable format.
17. As a user saving the file, I want the default filename to be clear and informative (e.g., `luteal-recapitulatif-consultation-2026-08-15.pdf`), so that I can easily locate the file in my downloads folder.

### Privacy, Performance & System Integration
18. As a privacy-conscious user, I want the entire report generation process to execute locally on my device without any internet connectivity, so that my sensitive health history is never exposed to external cloud servers.
19. As an offline user, I want the report generation to be instantaneous and available without a network connection, so that I can generate or re-export a summary directly in the doctor's waiting room.
20. As a user with sparse tracking history (e.g., only 1 or 2 recorded cycles), I want the report generator to handle limited data gracefully, presenting available facts with an explicit note that variability metrics require additional cycles, without crashing or rendering broken tables.
21. As a user with a large historical dataset (e.g., 36+ cycles), I want the report generation to run on a background thread without freezing the app UI, so that the application remains responsive during export.
22. As a user canceling the file save dialog or encountering a storage permission issue, I want a clear, non-intrusive notification explaining that the export was cancelled, so that I can retry when ready.

### Accessibility
23. As a TalkBack user, I want all controls in the report export dialog (date range selectors, format toggles, language chips, export buttons) to provide clear semantic labels and state announcements, so that I can configure and generate a report independently.
24. As a user with large system font settings (up to 200%), I want the export configuration dialog and preview sheet to reflow gracefully without truncated text or overlapping controls.

---

## Implementation Decisions

### 1. Architectural Overview & Domain Layer

The feature is organized into three decoupled layers: Domain Aggregation, Document Generation, and UI/Presentation.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              UI / Presentation                              │
│  ClinicalReportDialog (Compose) ──> LutealViewModel / JournalViewModel      │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ Config (date range, format, lang)
┌──────────────────────────────────────▼──────────────────────────────────────┐
│                            Domain / Data Layer                              │
│  ClinicalReportAggregator (computes metrics from CycleDao, DailyEntryDao)   │
│  ──> Emits immutable ClinicalReportData model                               │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ Pure Domain Data
┌──────────────────────────────────────▼──────────────────────────────────────┐
│                           Document Export Layer                             │
│  ┌─────────────────────────────────┐   ┌─────────────────────────────────┐  │
│  │ PdfReportBuilder                │   │ HtmlReportBuilder               │  │
│  │ (Android PdfDocument + Canvas)  │   │ (HTML5 + CSS print stylesheet)  │  │
│  └────────────────┬────────────────┘   └────────────────┬────────────────┘  │
└───────────────────┼─────────────────────────────────────┼───────────────────┘
                    ▼                                     ▼
      Storage Access Framework (ContentResolver -> OutputStream)
```

#### Core Domain Models (`fr.luteal.core.model`)

```kotlin
package fr.luteal.core.model

import java.time.LocalDate

enum class ReportLanguage {
    FRENCH,
    ENGLISH
}

enum class ReportFormat {
    PDF,
    HTML
}

enum class ReportDateRangePreset {
    LAST_3_CYCLES,
    LAST_6_CYCLES,
    LAST_12_CYCLES,
    CUSTOM
}

data class ClinicalReportConfig(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val includeNotes: Boolean = false,
    val language: ReportLanguage = ReportLanguage.FRENCH,
    val format: ReportFormat = ReportFormat.PDF
)

data class CycleReportRow(
    val cycleIndex: Int,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val lengthDays: Int?,
    val bleedingDaysCount: Int,
    val peakBleedingIntensity: String?,
    val painDaysCount: Int,
    val hasIntermenstrualBleeding: Boolean
)

data class CycleStatistics(
    val completedCycleCount: Int,
    val meanLengthDays: Double?,
    val standardDeviationDays: Double?,
    val medianLengthDays: Int?,
    val minLengthDays: Int?,
    val maxLengthDays: Int?,
    val shortestCycleRange: Pair<LocalDate, LocalDate>?,
    val longestCycleRange: Pair<LocalDate, LocalDate>?,
    val meanBleedingDurationDays: Double?,
    val minBleedingDurationDays: Int?,
    val maxBleedingDurationDays: Int?
)

data class BleedingDistribution(
    val spottingDays: Int,
    val lightDays: Int,
    val mediumDays: Int,
    val heavyDays: Int,
    val intermenstrualBleedingDays: Int,
    val intermenstrualDates: List<LocalDate>
)

data class PainDistribution(
    val totalPainDays: Int,
    val dysmenorrheaDays: Int, // Pain on bleeding days
    val nonMenstrualPelvicPainDays: Int, // Pain on non-bleeding days
    val mildPainDays: Int,
    val moderatePainDays: Int,
    val severePainDays: Int,
    val activityLimitingDays: Int
)

data class SymptomFrequencyEntry(
    val symptomId: String,
    val totalOccurrences: Int,
    val menstrualOccurrences: Int,
    val nonMenstrualOccurrences: Int,
    val percentageOfLoggedDays: Double
)

data class ReportNoteEntry(
    val date: LocalDate,
    val cycleDay: Int?,
    val noteText: String,
    val associatedBleeding: String?,
    val associatedPainScore: Int?
)

data class DataCompletenessMetrics(
    val totalDaysInRange: Int,
    val loggedDaysCount: Int,
    val completenessPercentage: Double
)

data class ClinicalReportData(
    val config: ClinicalReportConfig,
    val generatedAt: LocalDate,
    val appVersion: String,
    val statistics: CycleStatistics,
    val cycleRows: List<CycleReportRow>,
    val bleeding: BleedingDistribution,
    val pain: PainDistribution,
    val symptoms: List<SymptomFrequencyEntry>,
    val notes: List<ReportNoteEntry>,
    val completeness: DataCompletenessMetrics
)
```

---

### 2. Aggregation Engine (`ClinicalReportAggregator`)

The `ClinicalReportAggregator` class in `fr.luteal.core.data` is responsible for querying Room DAOs, aggregating cycle and daily logs, and computing deterministic clinical metrics without any UI dependencies:

1. **Cycle Filtering & Metrics:**
   - Queries `CycleDao.getAllCyclesOnce()` and filters cycles overlapping with `[config.startDate, config.endDate]`.
   - Calculates sample standard deviation using Bessel's correction ($n-1$):
     $$s = \sqrt{\frac{1}{n-1} \sum_{i=1}^n (x_i - \bar{x})^2}$$
   - When $n < 3$, standard deviation is marked as indeterminate (`null`) and the report displays a factual note (*"Données insuffisantes pour établir la variabilité type (< 3 cycles)"* / *"Insufficient data to compute standard deviation (< 3 cycles)"*).
2. **Bleeding & Intermenstrual Classification:**
   - Days with recorded bleeding are grouped into contiguous bleeding episodes.
   - An episode beginning on or within 1 day of a recorded cycle start date is classified as **Menstruation** (*Règles*).
   - Any bleeding or spotting recorded $\ge 3$ days after the cessation of menses and $\ge 3$ days before the next recorded cycle start is classified as **Intermenstrual Bleeding** (*Saignements intermenstruels / Métrorragies*).
3. **Pain Classification:**
   - Daily entries containing pain observations or pain intensity ratings are correlated with bleeding dates.
   - Pain recorded on active bleeding days is tallied under **Dysmenorrhea / Menstrual Pain** (*Dysménorrhée / Douleurs menstruelles*).
   - Pain recorded on non-bleeding days is tallied under **Non-Menstrual Pelvic Pain** (*Douleurs pelviennes non menstruelles*).
4. **Symptom Phase Matrix:**
   - Iterates through the active `ObservationCatalog` symptoms and tallies occurrences across cycle days.
   - Evaluates phase distribution: Menstrual (Cycle Day 1 to end of menses) vs. Non-Menstrual / Premenstrual (all other cycle days, with specific premenstrual tallies for the 7 days preceding cycle end).

---

### 3. PDF Layout & Rendering (`PdfReportBuilder`)

The PDF export utilizes Android's native `android.graphics.pdf.PdfDocument`.

#### Typography & Geometry Standards
- **Page Format:** ISO A4 ($595 \times 842$ pt / $210 \times 297$ mm at 72 DPI).
- **Margins:** Top 40 pt, Bottom 45 pt, Left 38 pt, Right 38 pt (usable width = 519 pt, usable height = 757 pt).
- **Color Palette (Print-Optimized Grayscale):**
  - Text Primary: `#1A1C1E` (90% Black, maximum contrast).
  - Text Secondary: `#44474E` (70% Gray).
  - Table Header Fill: `#E2E2E6` (Light Gray fill, crisp legibility).
  - Alternating Table Row: `#F4F4F6` (Subtle 5% zebra striping).
  - Dividing Lines: `#C4C6D0` (0.75 pt hairline stroke).
  - Accent / Flag: `#2D3135` (Neutral Charcoal).
- **Font Stack:** Android system `Typeface.SANS_SERIF` (Roboto) with distinct weights:
  - Document Title: Bold 16 pt.
  - Section Headers: Bold 11 pt (with uppercase tracking and underline hairline).
  - Table Headers: Bold 9 pt.
  - Body Text & Table Cells: Regular 8.5 pt.
  - Footnotes & Metadata: Regular 7.5 pt.

#### Dynamic Pagination & Flow Engine
- The PDF builder implements a sequential `PdfPageLayoutManager`:
  1. Computes the bounding box height of each content block (Summary cards, Cycle table rows, Pain breakdown, Symptom matrix, Notes).
  2. Before drawing a block, checks `currentY + blockHeight > usableHeight`.
  3. If exceeding page bounds, closes the current page, starts a new `PdfDocument.Page`, renders the running header, and resets `currentY`.
  4. Table headers are automatically re-drawn at the top of subsequent pages when a table is split across page breaks.
  5. Renders running footers at `page.canvas` with the generation date, document version, page index ("Page X sur Y"), and clinical disclaimer.

---

### 4. HTML Report Generation (`HtmlReportBuilder`)

The HTML report generates a single self-contained document with embedded CSS:
- Zero external stylesheet dependencies or remote web fonts.
- Built-in `@media print` rules:
  ```css
  @media print {
    body { font-size: 10pt; color: #000; background: #fff; }
    .no-print { display: none !important; }
    .page-break { page-break-before: always; }
    table { page-break-inside: auto; }
    tr { page-break-inside: avoid; page-break-after: auto; }
    thead { display: table-header-group; }
  }
  ```
- All user notes and string inputs are strictly HTML-escaped using a robust character-encoding routine to prevent cross-site scripting (XSS) when viewing the HTML document in desktop web browsers.

---

### 5. UI Integration & Storage Access Framework

#### User Interface Flow

```
Journal Screen / Settings Screen
       │
       ▼ [Tap: "Exporter un récapitulatif de consultation" / "Clinical Report"]
┌─────────────────────────────────────────────────────────────────────────┐
│ Modal Bottom Sheet: Exportation du récapitulatif consultation           │
│                                                                         │
│ Période d'analyse :                                                     │
│ (•) 3 derniers cycles   ( ) 6 derniers cycles   ( ) 12 derniers cycles  │
│ ( ) Historique complet  ( ) Période personnalisée [Dates...]            │
│                                                                         │
│ Options du rapport :                                                    │
│ [x] Inclure les notes et traitements enregistrés                        │
│                                                                         │
│ Langue du document :                                                    │
│ [ Français (par défaut) ]  [ English ]                                  │
│                                                                         │
│ Format d'export :                                                       │
│ [ PDF (.pdf) - Imprimable ]  [ HTML (.html) - Page web ]                │
│                                                                         │
│ [ Annuler ]                         [ Générer et enregistrer le fichier ]│
└─────────────────────────────────────────────────────────────────────────┘
       │
       ▼ [Tap: "Générer et enregistrer"]
Storage Access Framework (CreateDocument) -> System File Picker
       │
       ▼ [Destination selected]
Background Coroutine writes bytes directly to ContentResolver OutputStream
       │
       ▼
Success Snackbar with "Ouvrir le fichier" / "Open file" action
```

- **Entry Points:**
  - `JournalScreen.kt`: Action menu icon in top bar (*"Rapport consultation"* / *"Consultation Report"*).
  - `SettingsScreen.kt`: Under section *"Données et confidentialité"* (*"Exporter un rapport de consultation médicale"*).
- **Compose State Handling:**
  - `ClinicalReportState`: Holds selected preset, custom dates, note inclusion flag, target language, target format, and generation status (`Idle`, `Generating`, `Success(uri)`, `Error(message)`).
- **File Writing via SAF:**
  - Invokes `rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mimeType))`.
  - Default filename suggestions:
    - French PDF: `luteal-recapitulatif-consultation-YYYY-MM-DD.pdf`
    - French HTML: `luteal-recapitulatif-consultation-YYYY-MM-DD.html`
    - English PDF: `luteal-consultation-summary-YYYY-MM-DD.pdf`
    - English HTML: `luteal-consultation-summary-YYYY-MM-DD.html`
  - Streams bytes directly to the resolved `Uri` using `context.contentResolver.openOutputStream(uri)`.

---

### 6. Localization & Clinical Copy

All strings are strictly localized across `values/strings.xml`, `values-fr/strings.xml`, and `values-en/strings.xml`.

#### French Clinical Glossary (HAS / CNGOF Standard)
- *Récapitulatif de consultation médicale*
- *Synthèse des cycles et de la variabilité*
- *Durée moyenne de cycle*
- *Écart-type / Variabilité type*
- *Dysménorrhée (douleurs menstruelles)*
- *Douleurs pelviennes non menstruelles*
- *Saignements intermenstruels (métrorragies / spotting)*
- *Ménorragies / Flux abondant*
- *Fréquence des manifestations somatiques*

#### English Clinical Glossary (ACOG / FIGO Standard)
- *Clinician Consultation Summary Report*
- *Cycle Overview & Length Variability*
- *Mean cycle length*
- *Standard deviation / Typical variation*
- *Dysmenorrhea (menstrual pain)*
- *Non-menstrual pelvic pain*
- *Intermenstrual bleeding (spotting)*
- *Heavy menstrual bleeding*
- *Somatic & emotional observation frequency*

---

## Testing Decisions

### 1. Domain Aggregation Unit Tests (`ClinicalReportAggregatorTest`)
- **Metric Verification:**
  - Validate mean, median, min, max, and sample standard deviation calculations against known reference datasets.
  - Verify that standard deviation returns `null` when cycle count $< 3$.
- **Boundary & Sparse Data Tests:**
  - Empty dataset: 0 cycles, 0 entries $\rightarrow$ returns empty structure without exceptions.
  - 1 cycle with only bleeding logs $\rightarrow$ computes bleeding duration, cycle length `null`.
  - Ongoing unclosed cycle $\rightarrow$ calculates current duration without skewing closed cycle statistics.
  - Overlapping date ranges $\rightarrow$ correctly clips cycles and daily entries to requested window.
- **Classification Tests:**
  - Verify spotting occurring $>3$ days post-menses is correctly tallied as `intermenstrualBleeding`.
  - Verify pain logged on bleeding days is classified as `dysmenorrhea` and pain on non-bleeding days as `nonMenstrualPelvicPain`.

### 2. PDF Document Generation Tests (`PdfReportBuilderTest`)
- **Document Integrity:**
  - Verify `PdfReportBuilder.generate(data)` outputs a valid, non-empty `ByteArray`.
  - Verify PDF magic bytes header (`%PDF-1.`).
- **Pagination Invariants:**
  - Small dataset (3 cycles): Generates exactly 1 or 2 pages.
  - Large dataset (24 cycles with extensive notes): Automatically creates multiple pages with valid page counts and non-overlapping content.
  - Verify `PdfDocument.close()` is guaranteed in `finally` blocks to prevent native memory leaks.

### 3. HTML Document Generation Tests (`HtmlReportBuilderTest`)
- **Markup & Formatting:**
  - Verify valid HTML5 doctype and well-formed XML/HTML tags.
  - Verify presence of embedded print stylesheet (`@media print`).
- **Security & Sanitization:**
  - Inject malicious strings into user notes (`<script>alert('xss')</script>`, `<img src=x onerror=alert(1)>`).
  - Verify output encodes characters as `&lt;script&gt;` and does not contain raw executable HTML tags.
- **Language Parity:**
  - Verify that setting `ReportLanguage.FRENCH` produces 100% French headers, and `ReportLanguage.ENGLISH` produces 100% English headers.

### 4. Compose UI Tests (`ClinicalReportDialogTest`)
- Verify chip selections update the configuration state.
- Verify TalkBack semantic descriptions on range selectors, checkboxes, and format chips.
- Verify layout behavior at 200% font scaling.

---

## Out of Scope

1. **Direct In-App Emailing or Cloud Uploads:** Luteal does not integrate SMTP clients, cloud storage SDKs (Google Drive, Dropbox), or third-party transfer tools. Export is strictly local via Android Storage Access Framework.
2. **Algorithmic Diagnosis & Risk Scoring:** The report will never suggest conditions, calculate probability indices (e.g., "75% likelihood of Endometriosis"), or assign clinical risk stages. All summaries are strictly factual aggregations of user logs.
3. **EHR / FHIR / HL7 Direct Interoperability:** Direct real-time sync with hospital electronic health record systems is out of scope for this standalone client release.
4. **Proprietary Paid Export Formats:** The clinical report is a standard feature included without paywalls or subscriptions.

---

## Further Notes

### Research Register & Clinical Guidelines Citations

1. **Haute Autorité de Santé (HAS) / Collège National des Gynécologues et Obstétriciens Français (CNGOF)**
   - *Prise en charge de l'endométriose : Recommandations de bonne pratique (2017).*
   - *Prise en charge des ménorragies / saignements utérins anormaux (2021).*
   - Informs the distinction between cyclic dysmenorrhea and intermenstrual pelvic pain, and the clinical requirement to report duration and intensity of bleeding episodes.
2. **American College of Obstetricians and Gynecologists (ACOG)**
   - *Committee Opinion No. 651: Menstruation in Girls and Adolescents: Using the Menstrual Cycle as a Vital Sign (2015, Reaffirmed 2022).*
   - Highlights cycle length variation and bleeding duration as critical clinical indicators.
3. **International Federation of Gynecology and Obstetrics (FIGO)**
   - *FIGO Menstrual Disorders Working Group (2018).*
   - Informs standard cycle parameter definitions: Normal cycle frequency (24–38 days), Normal variation ($\le 7–9$ days), Normal bleeding duration ($\le 8$ days).

### Security & Privacy Considerations

- **Volatile In-Memory Construction:** Document byte generation occurs strictly in volatile memory (`ByteArrayOutputStream`). No intermediate plaintext PDF or HTML files are written to the app cache directory or shared temporary storage.
- **Storage Access Framework (SAF):** Writing to the destination chosen by the user ensures that the app does not request or require broad storage read/write permissions (`READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE`).
- **Data Minimization:** The user has explicit control over the date range and whether private qualitative text notes are included in the generated document.
