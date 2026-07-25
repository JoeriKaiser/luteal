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
- **Planned inversion (E2EE):** this authority model is scheduled to invert. A server that cannot read payloads cannot validate them or compute estimates, so content validation and estimates move to the client while routing metadata and conflict resolution stay server-side. Do not treat this section as final; see `docs/architecture/E2EE_DESIGN.md` §7 for the migration and its consequences.
- **Research register:** physiology and terminology sources backing the data model live in `folicular/docs/research/SOURCES.md`; product and content sources remain in `docs/research/SOURCE_REGISTER.md`.

### 5. Online Sync & Real-Device Trial
- **Permission gating:** `INTERNET`/`ACCESS_NETWORK_STATE` are declared in the main manifest, so both debug and release builds can sync. Cleartext HTTP is debug-only (`app/src/debug/AndroidManifest.xml` + `app/src/debug/res/xml/network_security_config.xml`) for the local trial server; the release build syncs over HTTPS only (the platform default blocks cleartext). Sync runs only when `SyncMode.ONLINE_CLOUD` is enabled. The sync UI is available in all builds; the local base-URL editor and the demo-data tools remain gated behind `BuildConfig.DEBUG`. The folicular base URL defaults per build type via `BuildConfig.SYNC_BASE_URL` (debug: emulator loopback `http://10.0.2.2:8080`; release: `https://luteal-api.waldemar.site`).
- **Install gotcha:** updating an already-installed offline build with the online/debug build does NOT auto-grant the newly added `INTERNET` permission (symptom: `socket failed: EPERM`). Do a clean install (`adb uninstall fr.luteal.app`) or run `adb shell pm grant fr.luteal.app android.permission.INTERNET`.
- **Backend:** run folicular via Docker Compose (`cd ~/Projects/folicular && docker compose up -d --build`, logs via `docker compose logs -f folicular`). If host port 8080 is taken, set `FOLICULAR_HOST_PORT=<port>`.
- **Device targeting:** the emulator uses `http://10.0.2.2:8080`; a real device uses `adb reverse tcp:8080 tcp:<host port>` then `http://127.0.0.1:8080`, or the host's LAN IP. The base URL is configurable in Settings → Synchronisation in the debug build (local trial); release uses the production default.
- **Credentials:** the account code and device token live only in Keystore-backed `EncryptedSharedPreferences` (`core/network/auth`); never in Room, DataStore, or logs. The one-time account code is not yet surfaced in the UI (account-code backup/recovery UX is a later phase).
- **Data minimisation on the wire:** registration sends a stable random label (`core/network/auth/DeviceLabel.kt`), never `Build.MODEL`, which fingerprints the device without serving sync. Envelope timestamps are UTC-normalised and truncated to the minute (`CycleSyncEngine.toCoarseUtc`) so a pushed batch does not carry a minute-by-minute timeline of when each observation was entered. Server-side, client addresses are HMAC'd under a per-process pepper before use as rate-limit keys and are never logged or persisted.
- **Encryption status:** record content **is** end-to-end encrypted. Records are sealed on device with AES-256-GCM under a key derived from the account code (`core/network/crypto`, HKDF validated against RFC 5869 vectors); the server stores ciphertext plus routing metadata only and holds no key. Duo payloads are sealed under a link key that travels in the pairing URL fragment and never reaches the server. Verified end to end against a live server, including a direct check that the database file contains no plaintext. Never use "anonyme" in user-facing copy: the product achieves GDPR pseudonymisation, not anonymisation. See `docs/architecture/E2EE_DESIGN.md`.
- **Do not reintroduce `androidx.security:security-crypto`.** Google deprecated it in April 2025 at `1.1.0-alpha07`, and it pulled in Google Tink: 1416 classes, about a fifth of the app, to protect a handful of short strings. Secrets use `core/network/auth/KeystoreSecretStore`, which wraps an AndroidKeyStore AES-GCM key directly. Any new secret storage goes through that.
- **Ship French only.** `defaultConfig.resourceConfigurations` is pinned to `fr` to match `res/xml/locales_config`; without it AndroidX and Material ship 84 locales, which was most of `resources.arsc`. Update both together if a language is ever added.
- **The account code is the encryption key**, not just a login credential. There is no reset and no server-side recovery, by construction. Any change that risks the user losing it — or that claims something stronger than the crypto actually delivers — needs the copy in `settings_sync_account_code_body` and `sync_transport_notice` revisited first.
- **Scope:** the implemented slice is cycles-only (register → push cycles + fanned-out bleeding → pull → apply to Room). Daily entries, symptom logs, biomarkers, medication, delete propagation, and multi-device convergence are later phases (see `docs/architecture/BACKEND_INTEGRATION.md`).

### 6. Distribution & Production Deployment
- **Distribution channel:** Luteal is distributed **F-Droid only**; there is no Google Play release. The build must remain free of proprietary Google libraries (no Play Services). Hilt, Compose, Room, and OkHttp are acceptable; anything requiring Google Play Services is not.
- **Backend deployment:** the folicular backend runs on a small VPS under **Coolify** (container built from the repo, Let's Encrypt TLS via the Coolify proxy, SQLite on a persistent volume). See `folicular/docs/deployment.md` for the runbook.
- **No client attestation:** Play Integrity / SafetyNet are unavailable on F-Droid builds, so "only the app may call the API" is not an enforceable boundary. The security boundary is per-device bearer-token authentication; do not add embedded app secrets (F-Droid builds from public source, so any static key would be published).
- **Certificate pinning (if added):** Coolify auto-renews Let's Encrypt certificates, so pin a stable key (Let's Encrypt root/intermediate SPKI, or a reused private key) — never the leaf certificate — and provide a remote kill-switch so a bad pin cannot permanently brick the app.
- **Release network access for sync (enabled):** the main manifest declares `INTERNET`/`ACCESS_NETWORK_STATE` and the sync UI is available in release builds, so F-Droid releases can sync online. Cleartext HTTP stays debug-only; release syncs over HTTPS against `BuildConfig.SYNC_BASE_URL` (`https://luteal-api.waldemar.site`). The local base-URL editor and demo-data tools remain `BuildConfig.DEBUG`-gated (see §5).
- **Invite-only registration (closed rollout):** the backend gates `POST /v1/auth/register` behind invite codes configured server-side (`FOLICULAR_INVITE_CODES`, matched by SHA-256 hash, never shipped in the app). The client collects the code in Settings → Synchronisation (stored in `SyncDataStore`, not the Keystore credential store) and sends it on first registration. Distribute codes out of band; the current codes are reusable, with single-use DB-backed codes a documented later hardening step.

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
