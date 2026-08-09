# Cycle Tracker Product Research Inventory

## Purpose

This inventory defines the product capabilities commonly expected from mature cycle trackers and adapts them to Luteal's constraints. It is a product-planning document, not medical guidance. Inclusion here does not authorize diagnostic language, fertility claims, or condition inference.

## Product rules applied to every capability

- Recorded facts and calculated estimates are different data types and receive different copy and visual treatment.
- Every field is optional unless it is technically required to save the user's explicit action.
- Missing days are unknown, not negative observations.
- Estimates use ranges and explain the history used to calculate them.
- No feature assumes a 28-day cycle, ovulation, sexual activity, pregnancy intention, gender identity, or partner gender.
- Sensitive categories remain private unless the tracker explicitly grants Duo access.
- No streaks, scores, rewards, or completion pressure.

## Capability inventory

### 1. First run and personal setup

| Capability | Product expectation | Luteal decision | Backend need |
|---|---|---|---|
| Role selection | Tracker and partner enter relevant flows | Both roles receive dedicated onboarding and navigation | Pairing only |
| Existing cycle history | Users can enter recent period starts | Support manual backfill and correction | No |
| Typical cycle questions | Some products request average duration | Prefer recorded dates; optional user-provided values are labeled as user estimates | No |
| Tracking goals | Logging, understanding patterns, Duo support | Optional personalization without medical promises | No |
| Units and locale | Temperature, dates, language | French dates, configurable Celsius where temperature is added | No |
| Privacy introduction | Explain storage and sharing before collection | Required before first sensitive entry or Duo authorization | No |

### 2. Daily observations

#### Menstruation

- Bleeding: none, traces, light, medium, heavy
- Explicit first day of a new cycle
- Ability to correct or remove a cycle start
- Bleeding products and quantities are optional future fields, not required for basic tracking

#### Subjective scales

- Pain
- Mood
- Energy
- Sleep quality
- Stress
- Libido, only when intentionally enabled

Scales must define their direction in copy. A blank value means not recorded.

#### Symptoms and context

- Cramps, headache, migraine, bloating, fatigue, breast tenderness, acne, digestive changes
- User-defined symptoms and favorites
- Pain location and type as optional structured fields
- Medication and contraception notes without dosage advice
- Exercise, sleep, and general context
- Private free-text notes

#### Optional body observations

- Cervical fluid observations
- Basal body temperature
- Ovulation-test result as a user-recorded test result
- Pregnancy-test result as a user-recorded test result
- Sexual activity and protection, disabled unless explicitly enabled

These observations must never be converted into a pregnancy, fertility, or ovulation certainty. Any interpretation feature requires a separate evidence and language review.

### 3. Calendar and history

- Month calendar with visually distinct recorded days and estimated ranges
- Daily timeline and searchable history
- Add, edit, and delete historical entries
- Correct a cycle start without recreating unrelated observations
- Support overlapping edits and incomplete cycles
- Import and export dates in a stable, documented format
- Conservative phase labels may combine recorded bleeding with the user's next-period estimate. Menstruation remains recorded; calculated phases are labeled estimated; overlapping transition ranges remain indeterminate; calendar data never confirms ovulation.

### 4. Estimates and cycle summaries

#### Pre-backend baseline

- Estimate a future period range only after at least two valid recorded intervals
- Use recent recorded intervals and show how many informed the range
- Widen the range when recent intervals vary
- Suppress the estimate when history is insufficient or invalid
- Never show a single predicted date as certain

#### Current phase display

- Menstrual phase is shown as recorded only from a cycle start or compatible bleeding observation
- Follicular and luteal labels are estimates shown only where plausible phase ranges do not overlap
- Ovulatory transition remains low confidence and appears only on the central estimate after a strict history-stability gate
- Missing early-cycle bleeding detail, transition windows, unsupported history, and expired estimates produce an explicit indeterminate state
- Phase-aware tips cite reviewed sources and never assume symptoms, energy, behavior, or fertility intentions

#### Later research before implementation

- Handling of long gaps, postpartum changes, perimenopause, and deliberately excluded intervals
- User-controlled exclusion of anomalous or incorrect cycle starts
- Transparent explanation of calculation changes
- Biomarker-informed retrospective phase interpretation, if it can remain probabilistic and avoid contraceptive claims

### 5. Trends and review

- Cycle-length history with ranges, not only averages
- Bleeding duration and intensity history
- Symptom frequency by time period
- Mood, pain, energy, and sleep timelines
- Missing-data visibility
- Comparisons that use neutral association language and never imply cause
- Export suitable for the user's own records

A chart may describe co-occurrence in recorded data. It must not diagnose a condition or tell the user why something happened.

### 6. Reminders

- Local reminder to record an observation
- Optional reminder around an estimated range
- Notification text privacy controls
- Quiet hours, schedule, snooze, and complete disablement
- No guilt-based reminder language

Notifications can be built locally and do not require the backend. Android notification permission and exact-alarm requirements must be reviewed at implementation time.

### 7. Duo as a first-class workflow

#### Tracker controls

- Category-level sharing grants
- Preview exactly what the partner can see
- Private-by-default notes and raw observations
- Revoke one category or the whole relationship
- Audit-friendly summary of active permissions
- User-authored support requests and preferences

#### Partner experience

- Dedicated partner home, not a cloned tracker dashboard
- Clear distinction between shared facts, shared estimates, and hidden information
- Support preferences written by the tracker
- No stereotyped phase advice or assumptions about mood, behavior, or availability
- Graceful states for no data, revoked access, delayed sync, and relationship removal

#### Backend-dependent capabilities

- Invitation, acceptance, identity, and device linking
- Encrypted transport and synchronization
- Permission version propagation
- Revocation confirmation across devices
- Conflict handling and relationship deletion

The local configuration and partner preview can be completed before those capabilities exist.

### 8. Privacy, security, and user control

- No network permission before online features exist
- Android cloud backup disabled for sensitive app data unless a reviewed encrypted backup design replaces it
- Honest storage language that does not claim encryption without an implemented design
- Optional app lock and sensitive-notification hiding
- Local export, import, and complete deletion
- Data retention and tombstone policy before synchronization
- Threat model before selecting end-to-end encryption primitives
- No advertising identifiers or third-party analytics by default

### 9. Accessibility and inclusion

- WCAG 2.2 AA and Android accessibility semantics
- 48dp minimum touch targets
- Scalable text and reflow
- Reduced motion
- State never communicated by color alone
- Plain French and translation-safe complete sentences
- Inclusive language for bodies, relationships, and goals
- Usable without recording sex, fertility intentions, gender, or a condition

## Priorities before the Go backend

### P0: Product foundation

- Semantic design system and light/dark themes
- Real local Room and DataStore state
- Daily bleeding, pain, mood, energy, selected symptoms, and notes
- Cycle-start recording and a correction path
- Recorded versus estimated range presentation
- Duo permission configuration and exact local preview
- No network permissions, fake pairing, fake encryption, or fake synchronization
- Unit tests for estimate boundaries and privacy defaults

### P1: Complete offline tracker

- Onboarding and role-specific first run
- Full month calendar and historical editing
- Expanded optional observation catalog
- Local reminders with private notification text
- Custom symptoms and favorites
- Local export, import, and complete deletion
- App lock and security review
- Trend charts with missing-data handling
- Tablet and landscape adaptation

### P2: Backend-ready preparation

- Stable transport-neutral identifiers and serialization contract
- Data ownership and sharing-grant model
- Revision, tombstone, and conflict semantics
- Threat model and key-management design
- Sync simulation tests using an in-memory fake transport
- Partner navigation and states tested against local fixtures

## Explicit non-goals

- Diagnosing, screening for, or confirming any condition
- Claiming contraception or pregnancy-prevention reliability
- Presenting a fertile window or ovulation date as known from calendar data
- Prescriptive phase-based partner advice
- Social feeds, advertising, engagement streaks, or sale of sensitive data
