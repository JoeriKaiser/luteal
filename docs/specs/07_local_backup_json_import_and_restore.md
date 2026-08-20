# Spec 07: Local Backup JSON Import and Restore

## Problem Statement

A foundational promise of Luteal as "The Quiet Instrument" is complete user data sovereignty, offline-first autonomy, and zero vendor lock-in. Spec 05 delivered local JSON data export (`DataExportManager`) and a complete GDPR-compliant local wipe (`LocalDataPurgeManager`). However, data export without a reciprocal restore capability leaves the data portability lifecycle incomplete.

Offline-first users—who deliberately use Luteal without creating an online account or connecting to an external synchronization server—currently have no mechanism to restore or transfer their tracking history when changing smartphones, performing a device factory reset, recovering from an operating system reinstallation, or restoring data from an offline backup. Forcing these users to register a cloud account solely to move their data directly violates Luteal's core offline-first design contract.

Furthermore, importing personal menstrual health records presents critical data integrity and security challenges:
1. **Accidental Data Loss & Blind Overwrites:** Restoring a backup without inspecting its contents risks unintentionally overwriting recent observations or corrupting valid tracking history.
2. **File Corruption & Schema Incompatibilities:** Importing malformed, truncated, or outdated JSON files could trigger runtime crashes, database constraint violations, or half-committed states where only part of a user's history is written.
3. **Absence of Conflict Reconciliation:** When a user already has records on their device and imports a backup (e.g. merging historical records from a secondary device), they need explicit, transparent control over whether to merge records (non-destructive upsert) or perform a clean replacement.
4. **End-to-End Encrypted Sync Desynchronization:** If a device is enrolled in or subsequently activates E2EE cloud sync (`SyncMode.ONLINE_CLOUD`), imported records must correctly register their synchronization states (`SyncStateEntity`, `CycleSyncStateEntity`), reset dirty flags, and clear stale tombstones to prevent sync desynchronization or unintended overwrites during background sync passes.

## Solution

Implement a secure, transparent, two-phase local JSON import and restore subsystem integrated into Luteal's settings:

1. **Storage Access Framework (SAF) File Selection:**
   - Use Android's standard `ActivityResultContracts.OpenDocument` with MIME type `application/json` (with fallback handling for general file pickers) to allow users to select backups stored on internal storage, SD cards, or user-configured file providers without requiring broad runtime storage permissions.
2. **`DataImportManager` Engine:**
   - Companion component to `DataExportManager` located in `fr.luteal.core.data`.
   - **Phase 1: Safe In-Memory Inspection & Validation (`inspectBackup`):** Safely deserializes the JSON stream into memory without disk side-effects. Validates schema version (`schema_version = 1`, with forward-compatible migration scaffolding for future v2 schemas), checks syntactic and semantic integrity (ISO-8601 dates, valid rating bounds, non-empty identifiers), and produces an immutable `LutealBackupPreview` object containing record counts and time spans.
   - **Phase 2: Atomic Transactional Restore (`restoreBackup`):** Executes all database mutations within an atomic Room transaction (`LutealDatabase.withTransaction`). If any record fails validation or an I/O error occurs, the entire transaction rolls back automatically, guaranteeing zero partial writes or database corruption.
3. **User-Controlled Conflict Strategies (`ImportStrategy`):**
   - `MERGE_UPSERT` (Recommended default): Non-destructively merges backup data with existing records. Existing records matching primary keys (cycle ID, daily entry date, symptom log ID) are updated if incoming timestamps are equal or newer; missing records are inserted; existing local records not present in the backup are preserved.
   - `REPLACE_ALL` (Clean slate): Atomically clears existing tracking tables (`cycles`, `daily_entries`, `symptom_logs`, `sync_state`, `cycle_sync_state`) and replaces them entirely with the backup payload, resetting preferences to the backup state.
4. **Sync State & Tombstone Reconciliation:**
   - Automatically registers imported entities in `SyncStateDao` and `CycleSyncStateDao`.
   - Sets `dirty = 1` and `lastPushError = null` on all imported or updated entities, ensuring subsequent E2EE sync passes accurately push restored records to paired devices.
   - Removes any active deletion tombstones associated with restored entity identifiers so restored records are not immediately deleted during the next server sync.
5. **Interactive Preview & Confirmation UI (`ImportBackupDialog`):**
   - Displays a structured preview sheet presenting backup metadata: export timestamp, origin app version, schema version, cycle count and date span, daily entry count, symptom log count, and included user preferences.
   - Allows users to select their preferred import strategy with clear explanatory copy.
   - Requires explicit user confirmation before executing the restore.
6. **Robust Error Handling & User Feedback:**
   - Distinguishes between syntax errors, unsupported schema versions, corrupted payload values, I/O errors, and transaction failures.
   - Surfaces clear, actionable French-first error messages with 100% English translation parity.
7. **Accessibility & Quiet Design:**
   - Adheres to WCAG 2.2 AA standards: 48dp minimum touch targets, dynamic font scaling support up to 200%, explicit TalkBack accessibility labels and live region announcements.
   - Uses calm, non-judgmental language adhering to "The Quiet Instrument" philosophy (neutral observation terms, zero diagnostic claims).

---

## User Stories

1. As an offline-first user changing smartphones, I want to import a JSON backup file exported from my previous phone, so that I can migrate my complete menstrual tracking history without needing a cloud account or internet connection.
2. As a user restoring data, I want to select my backup file using the standard Android system file picker, so that I can easily choose files from internal storage, an external SD card, or my personal cloud drive.
3. As a cautious user, I want to see a clear preview summary of the backup contents (number of cycles, daily observations, symptom logs, and export date) before anything is written to my database, so that I know exactly what I am restoring.
4. As a user who has logged entries since creating a backup, I want a "Merge (Upsert)" option that incorporates historical records from the backup while keeping my recent entries intact.
5. As a user restoring after a clean reinstallation or device wipe, I want a "Replace all" option that clears any placeholder data and reinstates the exact backup state.
6. As a user attempting to import an invalid or corrupted file (such as a truncated JSON or wrong file format), I want the app to reject the file with a clear, helpful error message and leave my existing local data completely untouched.
7. As a user attempting to import a backup created by a newer future version of Luteal, I want the app to detect an unsupported schema version and inform me to update the application before importing.
8. As an online E2EE sync user, I want restored records to be marked as dirty in the local sync state, so that they automatically synchronize to my paired devices during the next sync pass.
9. As an online E2EE sync user who previously deleted a cycle, I want restoring a backup containing that cycle to clear any local deletion tombstones, so that the restored cycle is not immediately deleted on the next sync pull.
10. As a primary tracker with declared tracking contexts (e.g. Endometriosis, PMS/PMDD), I want my preferences and observation settings from the backup to be restored properly without corrupting local cryptographic Keystore keys or Duo pairings.
11. As a TalkBack / screen-reader user, I want the file picker button, preview dialog, record counters, and restore results to have explicit, meaningful accessibility labels and live announcements, so that I can independently restore my data without sight.
12. As a low-vision user with dynamic font scaling set to 200%, I want the backup preview dialog to scroll and reflow cleanly without clipping record numbers, text, or confirmation buttons.
13. As a French-speaking user, I want all dialog text, counter labels, strategy descriptions, and error notifications to be phrased in natural, dignified French following "The Quiet Instrument" guidelines.
14. As an English-speaking user, I want 100% translation parity across all import-related screens, dialogs, and error messages in `values-en`.
15. As a user experiencing a database transaction failure or power loss during import, I want the database transaction to rollback atomically, ensuring zero partial or corrupted records are saved.
16. As a user with complex daily notes and multi-symptom entries, I want observation records (including bleeding intensity, pain levels, mood ratings, energy scores, and JSON-encoded symptom lists) to be accurately restored without data loss.
17. As a privacy-focused user, I want the import process to happen entirely in-memory and on-device, with zero network calls, zero telemetry, and zero temporary files left in cache.

---

## Implementation Decisions

### 1. Architecture & Component Responsibilities

```
+-------------------------------------------------------------+
|                     SettingsScreen                          |
|  - Storage Access Framework ActivityResultLauncher          |
|  - ImportBackupDialog (Preview, Strategy Picker, Confirm)   |
|  - DataManagementCard (Status, Progress, Error Alert)       |
+------------------------------+------------------------------+
                               | UI Events / StateFlow
+------------------------------v------------------------------+
|                    SettingsViewModel                        |
|  - inspectBackupUri(uri: Uri, context: Context)             |
|  - confirmRestore(payload: LutealBackupPayload, strategy)   |
|  - cancelRestore() / dismissImportState()                   |
|  - State: DataImportState (Idle, Inspecting, PreviewReady,  |
|            Restoring, Success, Error)                       |
+------------------------------+------------------------------+
                               | Coroutines (Dispatchers.IO)
+------------------------------v------------------------------+
|                    DataImportManager                        |
|  - inspectBackup(inputStream: InputStream): Result<Preview> |
|  - restoreBackup(payload, strategy): Result<ImportSummary>  |
|  - Schema Validation (v1, v2 migration hooks)               |
|  - Database Transaction Boundary (withTransaction)          |
+---------------+--------------+---------------+--------------+
                |              |               |
        +-------v------+ +-----v------+ +------v------+
        |   CycleDao   | |DailyEntryDao| | SymptomDao |
        +--------------+ +------------+ +-------------+
                |              |               |
        +-------v--------------v---------------v------+
        |         SyncStateDao / CycleSyncStateDao    |
        |  - Marks imported records dirty = 1         |
        |  - Clears stale deletion tombstones         |
        +---------------------------------------------+
```

- **`DataImportManager` (`fr.luteal.core.data`):**
  Singleton service responsible for all parsing, validation, entity mapping, and transactional database writes. Injected with:
  - `LutealDatabase` (for `withTransaction` atomicity)
  - `CycleDao`, `DailyEntryDao`, `SymptomDao`
  - `SyncStateDao`, `CycleSyncStateDao`
  - `UserPreferencesDataStore`
  - Coroutine dispatcher `CoroutineDispatcher = Dispatchers.IO`
- **`SettingsViewModel` (`fr.luteal.app.navigation`):**
  Orchestrates the UI state machine. Manages the lifecycle between file URI selection, background inspection, user preview confirmation, and progress reporting.
- **`SettingsScreen.kt` (`fr.luteal.app.navigation`):**
  Provides the SAF file picker launcher (`rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument())`), renders the `ImportBackupDialog` composable, and updates the `DataManagementCard`.

---

### 2. Domain & Schema Versioning

#### Current Schema Version (`schema_version = 1`)
The import engine consumes the JSON format generated by `DataExportManager`:

```json
{
  "schema_version": 1,
  "exported_at": "2026-08-15T10:30:00Z",
  "app_version": "1.2.0",
  "cycles": [
    {
      "id": "c-550e8400-e29b-41d4-a716-446655440000",
      "start_date": "2026-07-01",
      "end_date": "2026-07-28",
      "average_length_days": 28,
      "luteal_phase_length_days": 14,
      "period_days": [
        {
          "date": "2026-07-01",
          "bleeding_intensity": "MEDIUM",
          "notes": "",
          "symptom_ids": ["cramps", "fatigue"]
        }
      ]
    }
  ],
  "daily_entries": [
    {
      "date": "2026-07-01",
      "bleeding_intensity": "MEDIUM",
      "pain_level": 3,
      "mood_level": 2,
      "energy_level": 2,
      "symptom_ids": ["cramps", "fatigue", "nausea"],
      "notes": "First day of period",
      "updated_at": "2026-07-01T20:15:00Z"
    }
  ],
  "symptom_logs": [
    {
      "id": "s-6ba7b810-9dad-11d1-80b4-00c04fd430c8",
      "timestamp": "2026-07-01T14:30:00Z",
      "date": "2026-07-01",
      "symptom_id": "cramps",
      "severity": 3,
      "notes": "Mild cramping"
    }
  ],
  "preferences": {
    "user_role": "PRIMARY_TRACKER",
    "locale": "fr",
    "track_pmdd": true,
    "track_pms": true,
    "track_endometriosis": false,
    "track_pcos": false,
    "track_perimenopause": false,
    "track_thyroid": false,
    "age_band": "AGE_25_34"
  }
}
```

#### Preview Domain Model
```kotlin
data class LutealBackupPreview(
    val schemaVersion: Int,
    val exportedAt: Instant,
    val appVersion: String,
    val cycleCount: Int,
    val cycleDateSpan: ClosedRange<LocalDate>?,
    val dailyEntryCount: Int,
    val symptomLogCount: Int,
    val hasPreferences: Boolean,
    val preferencesSummary: UserPreferencesBackupDto
)

enum class ImportStrategy {
    MERGE_UPSERT,
    REPLACE_ALL
}

data class ImportSummary(
    val strategy: ImportStrategy,
    val cyclesRestored: Int,
    val dailyEntriesRestored: Int,
    val symptomLogsRestored: Int,
    val preferencesRestored: Boolean
)
```

#### Forward Compatibility & Migration Hooks
- If `schema_version > CURRENT_SUPPORTED_VERSION` (currently 1), the engine rejects the import with `DataImportError.UnsupportedSchemaVersion(fileVersion, maxSupportedVersion)` to protect the database from unknown structures.
- For minor schema additions (e.g. newly introduced optional fields in schema v2), `kotlinx.serialization` is configured with `ignoreUnknownKeys = true` and `isLenient = false` to safely ignore unrecognized extensions while strictly validating primitive types.

#### Field-Level Sanitization & Validation
- **Dates:** Validated against ISO-8601 `LocalDate` format (`YYYY-MM-DD`). Unparseable dates trigger `DataImportError.CorruptedPayload`.
- **Timestamps:** Validated against ISO-8601 UTC `Instant`. Converted to epoch milliseconds for Room storage.
- **Rating Scales:** Pain, mood, and energy levels are validated and clamped to `1..5` (or `null` if unrecorded).
- **Severity Scores:** Symptom log severity is clamped to `0..5`.
- **String Enums:** Bleeding intensity mapped strictly to `LIGHT`, `MEDIUM`, `HEAVY`, `SPOTTING` (or `null`). Unknown string tokens fall back safely to `null` without crashing.

---

### 3. Room Database Transactions & Conflict Strategies

All database operations run inside `LutealDatabase.withTransaction`:

```kotlin
suspend fun restoreBackup(
    payload: LutealBackupPayload,
    strategy: ImportStrategy
): Result<ImportSummary> = withContext(ioDispatcher) {
    runCatching {
        database.withTransaction {
            when (strategy) {
                ImportStrategy.REPLACE_ALL -> {
                    // 1. Wipe existing tracking tables
                    cycleDao.deleteAllCycles()
                    dailyEntryDao.deleteAllEntries()
                    symptomDao.deleteAllSymptomLogs()
                    syncStateDao.deleteAll()
                    cycleSyncStateDao.deleteAllSyncStates()
                    
                    // 2. Insert all entities from backup
                    insertCycles(payload.cycles)
                    insertDailyEntries(payload.dailyEntries)
                    insertSymptomLogs(payload.symptomLogs)
                    
                    // 3. Mark all as dirty sync records
                    reconcileSyncStatesForReplace(payload)
                }
                ImportStrategy.MERGE_UPSERT -> {
                    // 1. Merge cycles (upsert)
                    val restoredCycles = mergeCycles(payload.cycles)
                    
                    // 2. Merge daily entries (last-write-wins by updatedAt)
                    val restoredDailyEntries = mergeDailyEntries(payload.dailyEntries)
                    
                    // 3. Merge symptom logs (upsert)
                    val restoredSymptoms = mergeSymptomLogs(payload.symptomLogs)
                    
                    // 4. Mark merged entities dirty and clear stale tombstones
                    reconcileSyncStatesForMerge(payload)
                }
            }
            
            // 4. Apply preferences
            applyPreferences(payload.preferences)
            
            ImportSummary(
                strategy = strategy,
                cyclesRestored = payload.cycles.size,
                dailyEntriesRestored = payload.dailyEntries.size,
                symptomLogsRestored = payload.symptomLogs.size,
                preferencesRestored = true
            )
        }
    }
}
```

#### Entity Reconstruction Logic
- **`CycleEntity`:** Constructed from `CycleBackupDto`. `periodDaysJson` is serialized back to a JSON string from `List<PeriodDayBackupDto>` using `JSONArray` / `JSONObject` formatting matching existing DAO conventions.
- **`DailyEntryEntity`:** Constructed from `DailyEntryBackupDto`. `symptomIdsJson` is serialized to a JSON array string. `updatedAtEpochMillis` is parsed from the ISO-8601 string (falling back to current system time if absent).
- **`SymptomLogEntity`:** Constructed from `SymptomLogBackupDto`. `timestampEpochMillis` is parsed from `timestamp`.

---

### 4. Sync State & E2EE Reconciliation

To ensure offline restore plays flawlessly with online E2EE synchronization:
1. **Dirty Flag Registration:**
   - Every imported `DailyEntryEntity` is registered in `SyncStateEntity` with `entityId = date`, `entityType = "daily_entry"`, `dirty = true`, `lastPushError = null`.
   - Every imported `CycleEntity` is registered in `CycleSyncStateEntity` with `cycleId = id`, `dirty = true`, `lastPushError = null`.
2. **Tombstone Removal:**
   - If an imported entity ID was previously recorded as deleted locally (tombstone), the tombstone is purged. This ensures that a user deliberately restoring historical records does not have them wiped upon the next network sync with the server.
3. **Cryptographic Key Preservation:**
   - Keystore master encryption keys, device tokens, and Duo asymmetric keypairs are stored separately in Android Keystore / encrypted DataStores and are **never overwritten or cleared** during backup restore.

---

### 5. MVI ViewModel & State Machine

`SettingsViewModel` manages `DataImportState`:

```kotlin
sealed interface DataImportState {
    data object Idle : DataImportState
    data object Inspecting : DataImportState
    data class PreviewReady(
        val preview: LutealBackupPreview,
        val payload: LutealBackupPayload
    ) : DataImportState
    data object Restoring : DataImportState
    data class Success(val summary: ImportSummary) : DataImportState
    data class Error(val error: DataImportError) : DataImportState
}

sealed class DataImportError(val messageResId: Int) {
    data object InvalidJsonSyntax : DataImportError(R.string.settings_import_error_syntax)
    data class UnsupportedSchemaVersion(val version: Int) : DataImportError(R.string.settings_import_error_version)
    data object CorruptedPayload : DataImportError(R.string.settings_import_error_corrupt)
    data object IoReadError : DataImportError(R.string.settings_import_error_io)
    data object DatabaseTransactionError : DataImportError(R.string.settings_import_error_database)
    data object EmptyPayload : DataImportError(R.string.settings_import_error_empty)
}
```

#### UI Workflow
1. User taps **"Importer mes données (JSON)"** in Settings.
2. `ActivityResultContracts.OpenDocument()` launches the system picker filtering `application/json`.
3. Upon URI selection, `SettingsViewModel.inspectBackupUri(uri)` transitions state to `Inspecting`.
4. `DataImportManager.inspectBackup(stream)` parses the file. If valid, state transitions to `PreviewReady(preview, payload)`.
5. `ImportBackupDialog` appears displaying the record counts and strategy selector.
6. User selects strategy (`MERGE_UPSERT` or `REPLACE_ALL`) and confirms.
7. State transitions to `Restoring`.
8. `DataImportManager.restoreBackup(payload, strategy)` executes.
9. State transitions to `Success(summary)`. A polite TalkBack announcement and confirmation card appear in Settings.
10. If an error occurs at any stage, state transitions to `Error(error)` displaying a localized error message with a "Retry / Close" option.

---

### 6. UI & Compose Components

#### `ImportBackupDialog.kt`
Rendered when `DataImportState is PreviewReady`:
- **Title:** "Restaurer une sauvegarde locale" / "Restore local backup"
- **Metadata Card:**
  - Backup creation date formatted in user locale (e.g. "15 août 2026 à 10:30").
  - Application version indicator ("Version 1.2.0").
- **Record Breakdown Grid:**
  - Cycles recorded: count badge + date span (e.g., "12 cycles (janv. 2025 – août 2026)").
  - Daily observations: count badge (e.g., "342 observations quotidiennes").
  - Symptoms logged: count badge (e.g., "518 symptômes enregistrés").
  - Preferences: status badge ("Préférences incluses").
- **Strategy Radio Group:**
  - `MERGE_UPSERT`: "Fusionner avec les données existantes (Recommandé)" — Subtitle: "Met à jour les entrées existantes et ajoute les nouveaux enregistrements sans rien effacer."
  - `REPLACE_ALL`: "Remplacer toutes les données locales" — Subtitle: "Efface l'ensemble des données actuelles pour rétablir exactement la sauvegarde."
- **Action Buttons:**
  - `LutealSecondaryButton`: "Annuler"
  - `LutealPrimaryButton`: "Confirmer la restauration"

#### `DataManagementCard` Update in `SettingsScreen.kt`
Adds an import row directly adjacent to the existing export and wipe controls:
- **Title:** "Importer des données (JSON)"
- **Description:** "Restaurez vos cycles, observations quotidiennes et préférences à partir d'un fichier de sauvegarde Luteal."
- **Button:** "Sélectionner un fichier JSON" (disabled while inspecting or restoring).

---

### 7. Localization & Neutral Language

Follows "The Quiet Instrument" standards:
- Strictly neutral, non-alarmist phrasing.
- Zero diagnostic or clinical claims.
- French-first default with 100% English translation parity.

#### String Definitions
```xml
<!-- French (res/values/strings.xml & res/values-fr/strings.xml) -->
<string name="settings_import_title">Importer mes données (JSON)</string>
<string name="settings_import_body">Restaurez vos cycles, observations quotidiennes et préférences depuis un fichier de sauvegarde Luteal.</string>
<string name="settings_import_action">Sélectionner un fichier JSON</string>
<string name="settings_import_inspecting">Analyse du fichier de sauvegarde…</string>
<string name="settings_import_restoring">Restauration des données en cours…</string>
<string name="settings_import_success">Restauration terminée avec succès (%1$d cycles, %2$d observations).</string>

<string name="settings_import_dialog_title">Restaurer une sauvegarde locale</string>
<string name="settings_import_dialog_date">Sauvegarde du %1$s (version %2$s)</string>
<string name="settings_import_dialog_cycles">%1$d cycles enregistrés</string>
<string name="settings_import_dialog_cycles_span">Du %1$s au %2$s</string>
<string name="settings_import_dialog_entries">%1$d observations quotidiennes</string>
<string name="settings_import_dialog_symptoms">%1$d symptômes enregistrés</string>
<string name="settings_import_dialog_preferences">Préférences de suivi incluses</string>

<string name="settings_import_strategy_title">Mode de restauration</string>
<string name="settings_import_strategy_merge">Fusionner avec les données actuelles</string>
<string name="settings_import_strategy_merge_desc">Ajoute les nouveaux enregistrements et met à jour les entrées existantes sans effacer vos autres données.</string>
<string name="settings_import_strategy_replace">Remplacer toutes les données locales</string>
<string name="settings_import_strategy_replace_desc">Efface les données actuelles et réinstalle exactement le contenu de la sauvegarde.</string>
<string name="settings_import_confirm_action">Confirmer la restauration</string>

<string name="settings_import_error_syntax">Le fichier sélectionné n\'est pas un JSON valide ou est corrompu.</string>
<string name="settings_import_error_version">Cette sauvegarde a été créée avec une version plus récente de Luteal (schéma %1$d). Veuillez mettre à jour l\'application.</string>
<string name="settings_import_error_corrupt">Les données du fichier de sauvegarde sont invalides ou incomplètes.</string>
<string name="settings_import_error_io">Impossible de lire le fichier sélectionné.</string>
<string name="settings_import_error_database">Une erreur est survenue lors de l\'enregistrement dans la base de données. Aucune modification n\'a été appliquée.</string>
<string name="settings_import_error_empty">Le fichier sélectionné ne contient aucune donnée à restaurer.</string>

<!-- English (res/values-en/strings.xml) -->
<string name="settings_import_title">Import data (JSON)</string>
<string name="settings_import_body">Restore your cycles, daily observations, and preferences from a Luteal backup file.</string>
<string name="settings_import_action">Select JSON file</string>
<string name="settings_import_inspecting">Analyzing backup file…</string>
<string name="settings_import_restoring">Restoring data…</string>
<string name="settings_import_success">Data restored successfully (%1$d cycles, %2$d observations).</string>

<string name="settings_import_dialog_title">Restore local backup</string>
<string name="settings_import_dialog_date">Backup from %1$s (version %2$s)</string>
<string name="settings_import_dialog_cycles">%1$d recorded cycles</string>
<string name="settings_import_dialog_cycles_span">From %1$s to %2$s</string>
<string name="settings_import_dialog_entries">%1$d daily observations</string>
<string name="settings_import_dialog_symptoms">%1$d recorded symptoms</string>
<string name="settings_import_dialog_preferences">Tracking preferences included</string>

<string name="settings_import_strategy_title">Restore mode</string>
<string name="settings_import_strategy_merge">Merge with existing data</string>
<string name="settings_import_strategy_merge_desc">Adds new records and updates existing entries without erasing your other data.</string>
<string name="settings_import_strategy_replace">Replace all local data</string>
<string name="settings_import_strategy_replace_desc">Erases current data and reinstates the exact backup contents.</string>
<string name="settings_import_confirm_action">Confirm restore</string>

<string name="settings_import_error_syntax">The selected file is not a valid JSON or is corrupted.</string>
<string name="settings_import_error_version">This backup was created with a newer version of Luteal (schema %1$d). Please update the app.</string>
<string name="settings_import_error_corrupt">The backup data is invalid or incomplete.</string>
<string name="settings_import_error_io">Unable to read the selected file.</string>
<string name="settings_import_error_database">An error occurred while writing to the database. No changes were applied.</string>
<string name="settings_import_error_empty">The selected file contains no data to restore.</string>
```

---

### 8. Accessibility & WCAG 2.2 AA Compliance

- **Touch Targets:** All interactive elements (file picker trigger, radio strategy options, dialog action buttons) enforce minimum 48dp x 48dp touch bounding boxes.
- **Screen Reader Semantics:**
  - The preview dialog uses `Modifier.semantics { heading() }` on the title.
  - Radio options are grouped inside a `selectableGroup()` with explicit `selected` states and subtitles read in a single TalkBack pass.
  - Progress states (`Inspecting`, `Restoring`) and completion states (`Success`, `Error`) declare `LiveRegionMode.Polite` announcements.
- **Adaptive Font Scaling:** Layouts utilize `Modifier.verticalScroll(rememberScrollState())` inside dialog cards, preventing any text clipping at 200% system font scale.

---

## Testing Decisions

### 1. Behavioral Test Criteria

| Scenario | Given | When | Then |
| :--- | :--- | :--- | :--- |
| **Valid Schema v1 Inspection** | Valid JSON string with 2 cycles, 10 entries | `inspectBackup(stream)` | Returns `Success(preview)` with cycleCount=2, dailyEntryCount=10 |
| **Unsupported Schema Version** | JSON payload with `schema_version = 99` | `inspectBackup(stream)` | Returns `Failure(UnsupportedSchemaVersion(99))` |
| **Malformed JSON Syntax** | Truncated JSON stream (missing closing braces) | `inspectBackup(stream)` | Returns `Failure(InvalidJsonSyntax)` |
| **Merge Upsert Strategy** | Database has Cycle A; backup has Cycle A (updated) + Cycle B | `restoreBackup(payload, MERGE_UPSERT)` | Database contains Cycle A (updated) and Cycle B |
| **Replace All Strategy** | Database has 5 old cycles; backup has 2 cycles | `restoreBackup(payload, REPLACE_ALL)` | Database contains exactly 2 cycles; 5 old cycles erased |
| **Sync State Reconciliation** | Restore backup with 3 daily entries on sync-enabled device | `restoreBackup(payload, MERGE_UPSERT)` | `SyncStateDao` contains 3 records with `dirty = 1`, `lastPushError = null` |
| **Tombstone Clearing** | Local tombstone exists for `cycle-123`; backup contains `cycle-123` | `restoreBackup(payload, MERGE_UPSERT)` | Tombstone for `cycle-123` removed from database |
| **Atomic Transaction Rollback** | Simulated SQLite disk failure during symptom insertion | `restoreBackup(payload, REPLACE_ALL)` | Transaction fails; previous database state completely preserved |
| **String Parity** | French and English string resource files | Automated string resource audit | 100% key and parameter format parity across `values`, `values-fr`, `values-en` |

### 2. Unit Tests (`DataImportManagerTest.kt`)
- Located in `app/src/test/java/fr/luteal/core/data/DataImportManagerTest.kt` (Robolectric test runner).
- Tests roundtrip compatibility between `DataExportManager` and `DataImportManager`.
- Validates error categorization across invalid dates, corrupted numbers, and empty streams.

### 3. Database Integration Tests (`DataImportDatabaseTest.kt`)
- Uses in-memory Room database instance (`Room.inMemoryDatabaseBuilder`).
- Tests `withTransaction` isolation under concurrent reads and failure injections.

### 4. ViewModel State Tests (`SettingsViewModelImportTest.kt`)
- Verifies state transitions (`Idle` -> `Inspecting` -> `PreviewReady` -> `Restoring` -> `Success`).
- Verifies cancellation resets state cleanly to `Idle`.

### 5. Prior Art in Codebase
- Builds directly upon `DataExportManager` (`app/src/main/java/fr/luteal/core/data/DataExportManager.kt`) and `LocalDataPurgeManager` (`app/src/main/java/fr/luteal/core/data/LocalDataPurgeManager.kt`).
- Extends test conventions established in `DataExportManagerTest.kt`.

---

## Out of Scope

1. **Automatic Cloud Backup Polling:** Periodic automated polling or syncing from Google Drive, Nextcloud, or Dropbox (Luteal operates strictly via user-initiated Storage Access Framework pickers).
2. **Raw SQLite Binary Database Import (`.db` / `.sqlite`):** Direct binary database file replacement is prohibited because it bypasses Room migration verifications, integrity checks, and Keystore crypto invariants.
3. **Proprietary Third-Party App Backup Formats:** Importing proprietary export files from Clue, Flo, Apple Health, or Garmin (tracked under future external format converter specifications).
4. **Selective Entity Filtering:** Granular UI checkboxes to select/deselect individual cycles or individual symptoms during restore (restores are executed as whole-backup merge or replace).

---

## Further Notes

### Research & Regulatory Citations
- **GDPR Article 20 (Right to Data Portability):** Requires that data subjects have the right to receive personal data concerning them in a structured, commonly used, and machine-readable format, and have the right to transmit those data to another controller without hindrance. Providing symmetric local JSON export and import guarantees total user data sovereignty without third-party platform dependence.
- **European Health Data Space (EHDS) Guidelines (2025/2026):** Recommends transparent, patient-mediated data import mechanisms with schema validation to prevent health record fragmentation.

### Security & Privacy Notes
- **In-Memory Parsing:** The backup file stream is parsed directly in memory. No unencrypted temporary intermediate files are ever written to the application's cache directory or external storage.
- **Storage Access Framework Isolation:** By relying exclusively on `OpenDocument`, Luteal requires zero broad filesystem permissions (`READ_EXTERNAL_STORAGE` or `MANAGE_EXTERNAL_STORAGE`), adhering to Android's principle of least privilege.
- **Cryptographic Key Isolation:** Backup files contain only health observations and user preferences. Master encryption keys, device tokens, and private identity keys are never stored in or restored from JSON backup payloads.
