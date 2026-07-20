# AGENTS.md — Technical Guidelines & Project Constraints

## 📌 Project Overview
**Luteal** is a modern Android menstrual cycle tracking app designed for individuals and couples. It features offline-first local tracking and optional encrypted synchronization designed to pair with a modern Golang backend.

---

## 🇫🇷 Constraints & Requirements

### 1. French Language First
- **Default Locale:** French (`fr`) is the primary and mandatory default language for all strings, date formatters, and UI copy.
- **Resource Placement:** All user-facing text must reside in `res/values/strings.xml` and `res/values-fr/strings.xml` with zero hardcoded UI strings.
- **DateFormatting:** Use `java.time` with `Locale.FRENCH` via `fr.luteal.core.common.FrenchDateFormatter`.

### 2. Offline-First Architecture & Networking
- **Default Operation:** 100% offline-first. The app operates fully without requiring network permissions or connectivity.
- **Backend Sync:** Optional Online Cloud Sync mode designed to interface with a modern Golang backend. Network permissions (`INTERNET`, `ACCESS_NETWORK_STATE`) are declared conditionally for online mode.
- **Persistence:** Local persistence is managed by Room Database (`LutealDatabase`) and DataStore Preferences (`UserPreferencesDataStore`).

### 3. Cycle & Disorder Domain Context
- **Cycle Phases:** Menstrual (*Menstruelle / Nouvelle Lune*), Follicular (*Folliculaire / Premier Croissant*), Ovulatory (*Ovulatoire / Pleine Lune*), Luteal (*Lutéale / Dernier Quartier*).
- **Premenstrual Disorders & Symptoms:** Native support for tracking PMDD (*Trouble Dysphorique Prémenstruel / TDPM*), PMS (*Syndrome Prémenstruel / SPM*), Endometriosis (*Endométriose*), PCOS (*SOPK*), and customizable symptoms (pain, mood, energy, physical).
- **Couple Sync / Duo Mode:** Supports pairing between primary tracker (*Utilisatrice Principale*) and partner viewer (*Partenaire*).

---

## 🔬 Research & Domain Explorations

The app is designed to be highly cycle-aware, medical-literate, and partner-supportive. The following domain areas require ongoing research and refinement before feature implementations:

### 1. Premenstrual Disorders & Gynecological Research
- **PMDD / TDPM (*Trouble Dysphorique Prémenstruel*):** Detailed symptom tracking across luteal phase days, monitoring severe emotional distress, anxiety, and mood swings. Alignment with clinical tracking patterns.
- **PMS / SPM (*Syndrome Prémenstruel*):** Physical & emotional symptom clusters occurring prior to menstruation.
- **Endometriosis (*Endométriose*) & PCOS (*SOPK*):** Pelvic pain tracking, flare-up logs, irregular cycle prediction adjustments, and symptom intensity correlation.
- **Perimenopause & Cycle Irregularity:** Tracking protocols for non-standard or missing cycles.

### 2. Duo Sync & Partner UX Research
- **Privacy Boundaries & Granularity:** Granular sharing permissions allowing the primary tracker to share phase status, mood indicators, or support tips with their partner without exposing private notes or raw bleeding details.
- **Empathetic Partner Guidance:** Phase-specific contextual advice for partners to foster mutual support and awareness (especially during luteal/PMDD phases).

### 3. Encrypted Backend Sync Research
- **Golang E2EE Sync Protocol:** Researching end-to-end encrypted delta-sync protocols for offline-first Room database synchronization with the modern Golang backend server.
---

## 🎨 Design System: Astronomy & Celestial Theme

### Palette & Aesthetics
- **Theme Concept:** Cosmic / Celestial / Astronomy palette (avoiding gendered clichés or traditional pink tropes).
- **Primary Colors:** Starlight Gold (`#FFD166`), Lunar Silver (`#E2E8F0`), Celestial Cyan (`#4CC9F0`), Orbit Lavender (`#9D4EDD`), Nebula Blue (`#3A86EF`).
- **Backgrounds:** Midnight Cosmos (`#090B15`), Nebula Indigo (`#0E1225`), Starlight Dark Surface (`#14182E`).
- **Moon Phase Metaphors:**
  - Menstrual Phase: Eclipse Crimson (`#EF476F`)
  - Follicular Phase: Aurora Cyan (`#06D6A0`)
  - Ovulatory Phase: Solar Amber (`#FFD166`)
  - Luteal Phase: Galaxy Indigo (`#9D4EDD`)
- **UI Components:**
  - `FloatyBackground`: Animated celestial nebulae with twinkling stardust fields.
  - `FloatyCard`: Glassmorphic cosmic card with glowing border stroke and soft ambient elevation.
  - `WhimsicalButton` & `WhimsicalChip`: Tactile UI controls with gradient fills.
  - `CyclePhaseBadge`: Badge mapping cycle phases to astronomical moon phases.
- **Edge-to-Edge:** `enableEdgeToEdge()` enabled in `MainActivity` with transparent status and gesture navigation bars.

---

## 🛠️ Technology Stack & Dependencies

- **Language & Runtime:** Kotlin 2.0.0, Java 21 toolchain.
- **Build Tooling:** Android Gradle Plugin 8.5.0+, Gradle 8.7, Version Catalog (`gradle/libs.versions.toml`).
- **UI Framework:** Jetpack Compose (BOM `2024.06.00`), Material 3, Navigation Compose `2.8.0-beta03`.
- **Dependency Injection:** Hilt 2.51.1 (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@Module`).
- **Database & Storage:** Room 2.6.1 (with KSP annotation processing), DataStore Preferences 1.1.1.
- **Asynchrony & Data Streams:** KotlinX Coroutines 1.8.1, `StateFlow`/`SharedFlow`, KotlinX Serialization 1.6.3.

---

## 📂 Package Structure

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
│   │   ├── component/      # FloatyCard, FloatyBackground, WhimsicalButton, etc.
│   │   └── theme/          # Color, Type, Shape, Theme tokens
│   └── model/              # Domain models (Cycle, CyclePhase, PremenstrualDisorder, etc.)
```

---

## ⚡ Developer & Subagent Rules
1. **Verification Gate:** Run `./gradlew installDebug --no-daemon` or `./gradlew assembleDebug --no-daemon` before yielding changes.
2. **French Strings:** Maintain French as default in both `res/values/strings.xml` and `res/values-fr/strings.xml`.
3. **Architecture:** Keep business logic in `core/model` and `core/data/repository`. UI components in `core/designsystem` must remain decoupled from specific database entities.
