# Spec 01: Expanded Evidence-Based Observation Catalog & Daily Entry UX

## Problem Statement

During daily logging in Luteal, users encounter an overly constrained list of default symptoms (currently limited to cramps, headache, fatigue, and bloating), while other common somatic symptoms are conditionally locked behind onboarding tracking contexts or absent entirely. Users experiencing common cycle-related observations — such as nausea, generalized abdominal pain (distinct from uterine cramps), muscle aches/tension, back pain, or digestive changes — find no structured way to record these events without typing them into freeform notes, which impedes quick daily check-ins and trend recognition.

## Solution

Expand the default evidence-based observation catalog with clinically validated, non-diagnostic symptoms sourced from authoritative public health registers (NHS, WHO, ACOG). Organize the observation selection in the Daily Entry Sheet into clear, legible semantic categories (e.g., Pain / *Douleurs*, Digestion & Physical / *Digestion & Corps*, Mood & Energy / *Humeur & Énergie*), while preserving privacy, E2EE sync compatibility, offline-first persistence, and full French-first localization with English parity.

## User Stories

1. As a cycle tracking user, I want to see common physical symptoms like nausea (*Nausée*) available by default, so that I can quickly record gastrointestinal discomfort without writing custom notes.
2. As a cycle tracking user, I want to distinguish between uterine cramps (*Crampes*) and general stomach/abdominal pain (*Mal de ventre / Douleur abdominale*), so that my physical records accurately reflect the type of discomfort I feel.
3. As a cycle tracking user, I want to record muscle tension, body aches, or swelling (*Courbatures / Tensions musculaires*), so that I can track musculoskeletal changes across my cycle.
4. As a cycle tracking user, I want to log digestive transit variations (*Troubles digestifs*), so that I can understand bowel habit changes linked to hormonal fluctuations.
5. As a cycle tracking user, I want to record lower back pain (*Mal de dos / Douleur lombaire*), so that I have a structured log of radiating discomfort during premenstrual or menstrual phases.
6. As a cycle tracking user, I want symptoms in the Daily Entry Sheet grouped into intuitive thematic clusters (e.g., Pain, Digestion, Physical, Mood), so that I can find what I want to log in seconds without visual clutter.
7. As a cycle tracking user, I want all available symptoms to be accessible even if I didn't select specific health contexts during onboarding, so that my daily logging is not artificially restricted.
8. As a cycle tracking user, I want to select and unselect symptoms with accessible toggle chips meeting touch-target standards, so that I can log observations comfortably with one hand.
9. As an English-language user, I want every newly added observation term to be translated accurately in `values-en`, so that my interface remains fully localized.
10. As an offline user, I want newly logged symptoms to be persisted immediately in local Room storage, so that I never lose data when disconnected.
11. As an online sync user, I want newly added symptom keys to be synchronized and end-to-end encrypted seamlessly with the backend, so that multi-device consistency is maintained.
12. As a Duo primary tracker, I want my symptom categories to respect my explicit Duo sharing grants, so that specific physical details are never shared with my partner without my granular consent.

## Implementation Decisions

- **Domain Catalog Architecture:**
  Update the domain observation catalog and default symptom definition models to include the newly sourced symptom keys (`nausea`, `abdominal_pain`, `muscle_aches`, `backache`, `digestive_changes`, `sleep_issue`).
  Assign each symptom a structured category (e.g., `PAIN`, `DIGESTIVE`, `PHYSICAL`, `MOOD`, `ENERGY`, `SLEEP`).

- **Symptom Resolution Logic:**
  Adjust the catalog query logic so that the base catalog provides a comprehensive set of daily observations, while declared tracking contexts (e.g., Endometriosis, PMS/PMDD) continue to surface context-specific additions without hiding baseline health observations.

- **UI & Presentation (`DailyEntrySheet`):**
  Refactor the observation selector in the daily entry bottom sheet into structured, labeled flow sections with chips that show an icon, text label, and selected state checkmark.
  Ensure chips maintain minimum 48dp touch targets and clear accessible semantics (`contentDescription` and selection states).

- **Wire & Sync Compatibility:**
  Ensure new symptom identifiers are clean snake_case string keys that conform to OpenAPI contract models and are safely sealed by client-side AES-256-GCM crypto before transmission.

- **Localization & Copy:**
  Add string resources across `res/values/strings.xml`, `res/values-fr/strings.xml`, and `res/values-en/strings.xml` with zero hardcoded UI strings.

## Testing Decisions

- **Domain Model Tests:**
  Verify that `ObservationCatalog` and `Symptom` definitions return deterministic, unique, and ordered symptom lists regardless of declared tracking contexts.
- **Resource Parity Tests:**
  Extend string specifier tests to ensure that every new symptom ID matches a defined string in French default and English resources.
- **Persistence & Sync Mapping Tests:**
  Validate that daily entries containing new symptom keys map correctly into Room `DailyEntryEntity` and serialize into E2EE `SyncChangeInput` payloads without schema failure.
- **Compose UI Tests:**
  Test rendering, chip toggle interactions, and accessibility semantics inside `DailyEntrySheet`.

## Out of Scope

- User creation of ad-hoc arbitrary custom symptom names via text input (tracked under custom catalog milestones).
- Automatic symptom intensity sliders (severity scale remains unified 1-5 or boolean toggle per design guidelines).
- Diagnostic inference or condition probability scoring based on logged symptoms.

## Further Notes

All new symptom terms must be formally registered in `docs/research/SOURCE_REGISTER.md` citing NHS, ACOG, or WHO literature before merging.
