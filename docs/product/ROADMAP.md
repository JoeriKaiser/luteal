# Delivery Roadmap

Status snapshot, 2026-08.

The backend shipped ahead of this plan: end-to-end encrypted sync and Duo are
live against folicular while several offline-product milestones are still
open. The milestones below are annotated with what actually landed.
`BACKEND_INTEGRATION.md` carries the remaining sync work; this file tracks the
offline product.

## Milestone 1: Trustworthy foundation

Status: done, with two verification items open.

- [x] Confirm product positioning and anti-references
- [x] Establish `PRODUCT.md` and `DESIGN.md`
- [x] Replace cosmic prototype palette and components
- [x] Follow system light and dark theme
- [x] Network permissions (removed pre-backend, then reintroduced with the
  shipped sync; see `AGENTS.md` §5)
- [x] Disable Android cloud backup
- [x] Add normalized daily-entry persistence
- [x] Add a range-based cycle estimate with unit tests
- [x] Connect Today, Journal, Duo, and Settings to local state
- [x] Persist granular Duo sharing preferences locally
- [x] Complete initial light and dark device inspection
- [x] Add Compose UI tests for primary Today states
- [ ] Expand accessibility inspection with screen reader and contrast tooling
  (large-text Compose tests exist; TalkBack and contrast audits remain)
- [~] Compose UI tests for journal editing and Duo permission flows: journal
  history tests exist; the Duo screen test was removed when the screen moved
  to `hiltViewModel()` and needs Hilt integration test setup to return

## Milestone 2: Complete offline cycle workflow

Status: mostly shipped.

- [x] First-run onboarding for tracker and partner roles
- [x] Historical cycle-start entry, editing, and deletion (backfill entry,
  editing start date, and deletion with interval reconciliation all ship)
- [x] Full calendar with recorded and estimated legends (interactive month
  calendar grid in Journal with observation dots and estimate ranges)
- [x] Expanded observations: bleeding, pain, mood, energy, symptoms, notes,
  basal body temperature, cervical fluid, and rapid tests
- [ ] Custom symptoms and favorites (the sync-side catalog adopter exists;
  no UI creates custom symptoms)
- [x] Cycle-history review and transparent estimate explanation: history,
  variability visualizer, and estimate inputs (cycle count, variability)
- [ ] Undo behavior for entry deletion
- [~] Empty, loading, persistence-error, and large-data states: empty and
  error states are tested; loading and large-data coverage is partial

Exit criterion: a tracker can use the app for daily and historical recording
without a network connection or unsupported control.

## Milestone 3: Local ownership and continuity

Status: mostly shipped. Room currently sets `exportSchema = false`, which blocks the
schema-export item.

- [x] Local notification scheduling and privacy-safe notification copy
- [x] Versioned JSON export with documented schema
- [x] Import validation, preview, and conflict choices (MERGE_UPSERT & REPLACE_ALL)
- [x] Complete local data deletion (LocalDataPurgeManager)
- [x] Optional app lock research and implementation (PIN with PBKDF2 + Biometric)
- [x] Explicit security review of local storage and screenshots (FLAG_SECURE screen masking)
- [ ] Database schema export and migration tests

Exit criterion: users can retain, move, and delete their data without a Luteal
account.

## Milestone 4: Insight without claims

Status: done.

- [x] Cycle-length range history (LongitudinalCycleStatsCalculator & CycleVariabilityVisualizer)
- [x] Bleeding and observation timelines (Journal calendar & ClinicalReportAggregator)
- [x] Missing-data-aware summaries
- [x] Neutral co-occurrence views with no causal language
- [x] User-controlled excluded cycles or dates (Cycle exclusion with reasons)
- [x] Exportable summary for personal use (Clinical consultation report PDF & HTML)

Exit criterion: every insight can explain which recorded data it uses and
never presents a diagnosis or certainty.

## Milestone 5: Duo product completion

Status: done.

- [x] Tracker permission-management flow (per-field grants, enforced
  client-side before sealing)
- [x] Partner onboarding and dedicated home
- [x] User-authored support preferences (sealed support requests with ack)
- [x] Permission change and revocation states
- [x] No-data, stale-data, offline, and removed-relationship states (no-data,
  key-missing, and cached projection with freshness badge)
- [x] Transport: the planned fake transport was overtaken — the real
  end-to-end encrypted transport shipped (`BACKEND_INTEGRATION.md`)
- [ ] UI tests for the Duo flows (the local test was removed with the
  `hiltViewModel()` migration; `E2eRoundTripTest` covers the protocol, not
  the UI)
Exit criterion: every Duo screen and state is testable, including against the
real encrypted transport.
## Milestone 6: Backend contract readiness

Status: done.

- [x] Define data ownership for every synchronized record
- [x] Define stable identifiers, revisions, tombstones, and conflicts
- [x] Define permission-grant versioning and revocation behavior
- [x] Produce a threat model (`E2EE_DESIGN.md` §2)
- [x] Decide key creation, recovery, rotation, and device addition
- [x] Write protocol conformance fixtures shared with the Go service
  (`contract/conformance`, mirrored server-side)
- [x] Review the network-permission build strategy (unconditional manifest
  permissions + `SyncMode` gating; see `AGENTS.md` §5)

Exit criterion: the Go implementation follows an approved contract rather
than forcing Android domain changes.

## Remaining gates

The original "backend start gate" is obsolete: the backend runs in
production. What still gates a wider rollout lives in
`BACKEND_INTEGRATION.md` (Production gates), and the offline-product exit
criteria above for Milestones 2–4 still stand.
