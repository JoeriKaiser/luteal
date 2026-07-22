# Pre-backend Delivery Roadmap

## Goal

Reach a release-quality offline product and a backend-ready Duo contract before introducing Go services or network permissions.

## Milestone 1: Trustworthy foundation

Status: in progress

- [x] Confirm product positioning and anti-references
- [x] Establish `PRODUCT.md` and `DESIGN.md`
- [x] Replace cosmic prototype palette and components
- [x] Follow system light and dark theme
- [x] Remove network permissions
- [x] Disable Android cloud backup
- [x] Add normalized daily-entry persistence
- [x] Add a range-based cycle estimate with unit tests
- [x] Connect Today, Journal, Duo, and Settings to local state
- [x] Persist granular Duo sharing preferences locally
- [x] Complete initial light and dark device inspection
- [x] Add Compose UI tests for primary Today states
- [ ] Expand accessibility inspection with screen reader, large text, and contrast tooling
- [ ] Add Compose UI tests for journal editing and Duo permission flows

## Milestone 2: Complete offline cycle workflow

- [ ] First-run onboarding for tracker and partner roles
- [ ] Historical cycle-start entry, editing, and deletion
- [ ] Full calendar with recorded and estimated legends
- [ ] Expanded observations from the feature inventory
- [ ] Custom symptoms and favorites
- [ ] Cycle-history review and transparent estimate explanation
- [ ] Undo behavior for entry deletion
- [ ] Empty, loading, persistence-error, and large-data states

Exit criterion: a tracker can use the app for daily and historical recording without a network connection or unsupported control.

## Milestone 3: Local ownership and continuity

- [ ] Local notification scheduling and privacy-safe notification copy
- [ ] Versioned JSON export with documented schema
- [ ] Import validation, preview, and conflict choices
- [ ] Complete local data deletion
- [ ] Optional app lock research and implementation
- [ ] Explicit security review of local storage and screenshots
- [ ] Database schema export and migration tests

Exit criterion: users can retain, move, and delete their data without a Luteal account.

## Milestone 4: Insight without claims

- [ ] Cycle-length range history
- [ ] Bleeding and observation timelines
- [ ] Missing-data-aware summaries
- [ ] Neutral co-occurrence views with no causal language
- [ ] User-controlled excluded cycles or dates
- [ ] Exportable summary for personal use

Exit criterion: every insight can explain which recorded data it uses and never presents a diagnosis or certainty.

## Milestone 5: Duo product completion with local fixtures

- [ ] Tracker permission-management flow
- [ ] Partner onboarding and dedicated home
- [ ] User-authored support preferences
- [ ] Permission change and revocation states
- [ ] No-data, stale-data, offline, and removed-relationship states
- [ ] Fake transport implementation restricted to tests and previews
- [ ] End-to-end UI tests against local fixtures

Exit criterion: every Duo screen and state is testable without pretending a remote partner is connected.

## Milestone 6: Backend contract readiness

- [ ] Define data ownership for every synchronized record
- [ ] Define stable identifiers, revisions, tombstones, and conflicts
- [ ] Define permission-grant versioning and revocation behavior
- [ ] Produce a threat model
- [ ] Decide key creation, recovery, rotation, and device addition
- [ ] Write protocol conformance fixtures shared with the future Go service
- [ ] Review conditional network-permission build strategy

Exit criterion: Go implementation can follow an approved contract rather than forcing Android domain changes.

## Backend start gate

Do not begin production backend work until:

1. Local models and migrations are stable enough to version.
2. Duo permissions and revocation semantics are approved.
3. The threat model and key lifecycle are documented.
4. Offline mutation and conflict behavior have deterministic tests.
5. UI copy does not claim connectivity, encryption, or synchronization before runtime proof exists.
