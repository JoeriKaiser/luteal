# AGENTS.md: Technical Guidelines and Project Constraints

## Project Overview
**Luteal** is a French-first, private Android menstrual cycle tracker and consensual Duo companion. Solo tracking and Duo are equally central product experiences. The app is offline-first, with optional encrypted synchronization planned for a modern Golang backend.

Luteal records and presents user-entered observations and clearly identified estimates. It does not diagnose conditions, make medical claims, or present predictions as clinical conclusions. The product personality is reassuring, discreet, and precise. See `PRODUCT.md` for the strategic product and design context.

---

## Constraints & Requirements

### 1. French Language First
- **Default Locale:** French (`fr`) is the primary and mandatory default language for all strings, date formatters, and UI copy.
- **Resource Placement:** All user-facing text must reside in `res/values/strings.xml` and `res/values-fr/strings.xml` with zero hardcoded UI strings.
- **DateFormatting:** Use `java.time` with `Locale.FRENCH` via `fr.luteal.core.common.FrenchDateFormatter`.

### 2. Offline-First Architecture & Networking
- **Default Operation:** 100% offline-first. The app operates fully without requiring network permissions or connectivity.
- **Backend Sync:** Optional Online Cloud Sync mode designed to interface with a modern Golang backend. Network permissions (`INTERNET`, `ACCESS_NETWORK_STATE`) are declared conditionally for online mode.
- **Persistence:** Local persistence is managed by Room Database (`LutealDatabase`) and DataStore Preferences (`UserPreferencesDataStore`).

### 3. Cycle and Observation Domain Context
- **Cycle Phases:** Menstrual (*Menstruelle*), Follicular (*Folliculaire*), Ovulatory (*Ovulatoire*), Luteal (*Lutéale*). Optional moon-phase names are decorative metaphors only and must never imply a biological relationship with lunar events.
- **Conditions and Symptoms:** Support user-configured tracking related to TDPM, SPM, endometriosis, SOPK, and customizable observations such as pain, mood, energy, sleep, and physical symptoms. Do not infer, screen for, or announce a condition.
- **Predictions:** Clearly distinguish recorded facts from calculated estimates. Use ranges and uncertainty language; never assume a regular 28-day cycle or present menstruation, ovulation, or fertility estimates as certainties.
- **Duo Mode:** Treat the tracker and partner experiences as equally central. Sharing is private by default, explicit, granular, visible, and reversible. A partner never receives private notes or observations without specific permission.
- **Language:** Use inclusive copy that does not assume gender identity, sexual activity, fertility goals, pregnancy intention, cycle regularity, or a gendered partner role.

### 4. Backend Source of Truth
- **Canonical authority:** The Go backend (`folicular`, `~/Projects/folicular`) is the source of truth when it comes to data: canonical schema, validation rules, enum vocabularies, conflict resolution, and computed estimates are defined there.
- **Client role:** The Android client remains offline-first for display and local writes (Room is the local cache, see `docs/architecture/SYNC_BOUNDARY.md`), but its sync DTOs, enums, and validation must conform to the backend contract (`folicular/docs/api.md`, `folicular/docs/data-model.md`).
- **Conflicts:** When local and synchronized versions disagree, the client accepts the backend's resolved state and re-derives its local cache from it. Server sequence numbers remain transport metadata, not domain truth.
- **Schema changes start in the backend:** adapt the client to backend migrations, never the reverse.
- **Research register:** physiology and terminology sources backing the data model live in `folicular/docs/research/SOURCES.md`; product and content sources remain in `docs/research/SOURCE_REGISTER.md`.

### 5. Online Sync (Dev/Online Build) & Real-Device Trial
- **Permission gating:** `INTERNET`/`ACCESS_NETWORK_STATE` and cleartext HTTP are declared only in the debug source set (`app/src/debug/AndroidManifest.xml` + `app/src/debug/res/xml/network_security_config.xml`). The release manifest declares no network permission; WorkManager's transitive `ACCESS_NETWORK_STATE` is stripped in `app/src/release/AndroidManifest.xml`. Sync runs only when `SyncMode.ONLINE_CLOUD` is enabled, and the trial UI is gated behind `BuildConfig.DEBUG`.
- **Install gotcha:** updating an already-installed offline build with the online/debug build does NOT auto-grant the newly added `INTERNET` permission (symptom: `socket failed: EPERM`). Do a clean install (`adb uninstall fr.luteal.app`) or run `adb shell pm grant fr.luteal.app android.permission.INTERNET`.
- **Backend:** run folicular via Docker Compose (`cd ~/Projects/folicular && docker compose up -d --build`, logs via `docker compose logs -f folicular`). If host port 8080 is taken, set `FOLICULAR_HOST_PORT=<port>`.
- **Device targeting:** the emulator uses `http://10.0.2.2:8080`; a real device uses `adb reverse tcp:8080 tcp:<host port>` then `http://127.0.0.1:8080`, or the host's LAN IP. The base URL is configurable in Settings → Synchronisation (essai local).
- **Credentials:** the account code and device token live only in Keystore-backed `EncryptedSharedPreferences` (`core/network/auth`); never in Room, DataStore, or logs. The one-time account code is not yet surfaced in the UI (account-code backup/recovery UX is a later phase).
- **Scope:** the implemented slice is cycles-only (register → push cycles + fanned-out bleeding → pull → apply to Room). Daily entries, symptom logs, biomarkers, medication, delete propagation, and multi-device convergence are later phases (see `docs/architecture/BACKEND_INTEGRATION.md`).

---

## Research & Domain Explorations

The app is designed to be cycle-aware, health-literate, and partner-supportive without diagnosing or making medical claims. Research must inform safe data structures, neutral educational copy, and inclusive workflows. Every research-derived implementation must preserve the distinction between self-reported observations and non-clinical estimates.

### 1. Menstrual Health and Observation Research
- **TDPM and SPM:** Support detailed prospective self-observation across cycle days without labeling a user, asserting a diagnosis, or treating an app pattern as clinical evidence.
- **Endometriosis and SOPK:** Support configurable pain, bleeding, energy, medication, and irregular-cycle observations without condition detection or causal claims.
- **Perimenopause and Cycle Irregularity:** Support non-standard, changing, and missing cycles without forcing a canonical cycle model.
- **Safety and Sources:** Prefer authoritative French and international public-health sources. Record sources, review dates, jurisdiction, and the exact product decision each source informs. Educational content must include appropriate scope and uncertainty language.

### 2. Duo Sync and Partner UX Research
- **Privacy Boundaries and Granularity:** Granular sharing permissions allow the primary tracker to share selected status, observations, or requested support without exposing private notes or raw details by default.
- **Equal Product Quality:** Build a purpose-designed partner workflow, not a read-only clone of the tracker experience.
- **Support Without Prescription:** Guidance must be user-controlled, non-stereotyped, and based on explicitly shared preferences rather than assumptions about a phase or condition.

### 3. Encrypted Backend Sync Research
- **Golang E2EE Sync Protocol:** Researching end-to-end encrypted delta-sync protocols for offline-first Room database synchronization with the modern Golang backend server.

---

## Design System: Private Health Utility

### Product Aesthetic
- **Creative Direction:** Reassuring, discreet, and precise. Utility leads identity. The interface should feel calm and approachable without becoming cute, mystical, clinical, or alarmist.
- **Color Strategy:** Use restrained tinted neutrals and one controlled product accent. Cycle phases may use a separate accessible semantic set, but phase meaning must also be communicated through text and iconography.
- **Celestial Identity:** Celestial references are limited to subtle atmosphere, illustration, or optional phase symbolism. Do not use permanent star fields, neon cosmic effects, or imply that lunar events affect the menstrual cycle.
- **Prohibited Patterns:** No decorative glassmorphism, full-spectrum gradients, gender-stereotyped pink branding, astrology styling, gamified streaks, or ornamental motion.
- **Components:** Replace prototype `Floaty*` and `Whimsical*` vocabulary with semantic production components and complete default, pressed, focused, disabled, loading, error, and selected states where relevant.
- **Themes:** Support implementation-ready light and dark themes. Do not default to dark mode solely because of the former cosmic direction.
- **Accessibility:** WCAG 2.2 AA minimum, scalable text, screen-reader semantics, logical focus order, reduced motion, minimum 48 dp targets, and no state communicated by color alone.
- **Edge-to-Edge:** Keep `enableEdgeToEdge()` in `MainActivity` with correct status and navigation bar inset handling.
- **Source of Truth:** `DESIGN.md` defines the visual tokens and component rules once approved.

---

## Technology Stack & Dependencies

- **Language & Runtime:** Kotlin 2.1.0, Java 21 toolchain.
- **Build Tooling:** Android Gradle Plugin 8.7.3+, Gradle 8.9, Version Catalog (`gradle/libs.versions.toml`). compileSdk/targetSdk 35 (AGP 9.x's new-DSL migration is deliberately deferred).
- **UI Framework:** Jetpack Compose (BOM `2025.03.00`), Material 3, Navigation Compose `2.8.5`.
- **Dependency Injection:** Hilt 2.53.1 (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@Module`).
- **Database & Storage:** Room 2.7.1 (with KSP annotation processing), DataStore Preferences 1.1.4.
- **Asynchrony & Data Streams:** KotlinX Coroutines 1.8.1, `StateFlow`/`SharedFlow`, KotlinX Serialization 1.6.3.

---

## Package Structure

```
fr.luteal
├── app/
│   ├── di/                 # Hilt Modules (DatabaseModule, RepositoryModule)
│   ├── navigation/         # Navigation Scaffold & Tab views
│   ├── LutealApp.kt        # @HiltAndroidApp Application entry point
│   └── MainActivity.kt     # @AndroidEntryPoint Activity with edge-to-edge
├── core/
│   ├── common/             # Date formatters, LutealResult, Network observers
│   ├── data/
│   │   ├── datastore/      # DataStore Preferences (UserPreferencesDataStore)
│   │   ├── entity/         # Room Entities (CycleEntity, SymptomLogEntity, etc.)
│   │   ├── local/          # Room DAOs, Converters, LutealDatabase
│   │   └── repository/     # Repository Interfaces & Impls
│   ├── designsystem/
│   │   ├── component/      # Reusable semantic UI components and states
│   │   └── theme/          # Color, Type, Shape, Theme tokens
│   └── model/              # Domain models (Cycle, CyclePhase, PremenstrualDisorder, etc.)
```

---

## Developer & Subagent Rules
1. **Verification Gate:** Run `./gradlew installDebug` or `./gradlew assembleDebug` before yielding changes (do not use `--no-daemon` to maintain a warm Gradle daemon for fast builds).
2. **French Strings:** Maintain French as default in both `res/values/strings.xml` and `res/values-fr/strings.xml`.
3. **Architecture:** Keep business logic in `core/model` and `core/data/repository`. UI components in `core/designsystem` must remain decoupled from specific database entities.
4. **No Medical Claims:** UI, calculations, tests, and documentation must distinguish observations from estimates and must not diagnose, screen for, or imply a condition.
5. **Duo Equality:** Treat tracker and partner experiences as equal product surfaces with explicit privacy boundaries.
6. **Accessibility:** Meet WCAG 2.2 AA and Android accessibility requirements for all new or changed UI.
7. **No Emojis:** Do not include emojis in code, documentation, headers, or UI strings unless explicitly requested.
