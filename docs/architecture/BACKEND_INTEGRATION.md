# Backend Integration Plan

How the Android client adopts the folicular backend without compromising the
offline-first product. The client remains offline-first for display and local
writes: Room is the local cache of synced records (see
`SYNC_BOUNDARY.md`), sync DTOs and enums conform to the backend contract, and
on conflict the client adopts the server's resolved state.

**Authority note.** With end-to-end encryption shipped, content validation and
computed estimates moved to the client: a server that cannot read payloads
cannot validate or compute them. Routing metadata and conflict ordering stay
server-side. See `E2EE_DESIGN.md` §7 and `AGENTS.md` §4.

**Current state:** a multi-entity E2EE sync engine is live and was verified on
a real device (register → push → pull round-trip, tombstones, conflict
adoption, device-token reuse). Synced types: `cycle`, `bleeding_observation`
(fanned out of cycles and of daily entries), `daily_entry`, `symptom_log`.
Duo is live end to end (invite, accept-by-paste, per-field grants, revocation,
sealed support thread). Build gating, Keystore credentials, the OkHttp
transport, an on-demand `WorkManager` sync, and the account-code surface in
Settings are in place; see `AGENTS.md` §5 for how to run against a local
server. The milestones below are the remaining work.

## Foundation

### Contract pipeline

The contract is machine-readable and enforced, not hand-copied:

```
folicular/openapi/openapi.yaml   <- single source of truth
        |                                   |
        | (Go test: internal/contract)      | (Gradle: openApiGenerate)
        v                                   v
  spec must validate +              fr.luteal.core.network.contract.models
  cover every route                 (generated @Serializable DTOs, models only)
```

- The folicular repo owns the spec; a snapshot is vendored here under
  `contract/` so a plain clone builds unaided. The app generates from it via
  the `org.openapi.generator` Gradle plugin on every build (`preBuild` depends
  on `openApiGenerate`). Refresh the snapshot with `./scripts/sync-contract.sh`.
- Input path defaults to the vendored spec at `$rootDir/contract/openapi.yaml`
  (see `contract/README.md`);
  override with `-Pfolicular.spec=/abs/path/openapi.yaml` for other layouts.
- Generated output is build-only (`app/build/generated/openapi`), never
  committed, never hand-edited. Transport stays hand-written.
- Enums arrive as the exact server vocabulary (`Flow.SPOTTING` =
  `@SerialName("spotting")`), so Kotlin / SQL CHECK / Go domain drift is
  impossible for generated types.
- The vendored spec is already the E2EE shape: `SyncChangeInput` /
  `SyncPullChange` carry a base64 `ciphertext` field, and the plaintext record
  schemas remain in the contract only as documentation of the sealed payload —
  no path references them, and the client generates its record types from
  them.

### Wire serializers

Generated models mark `java.util.UUID`, `LocalDate`, and `OffsetDateTime` as
`@Contextual`. `core.network.ContractSerializers` provides the serializers,
the `ContractSerializersModule`, and a ready `ContractJson` instance
(`ignoreUnknownKeys`, `encodeDefaults`), unit-tested for wire-format parity
(snake_case keys, `2026-06-30` dates, `2026-07-01T08:00:00Z` instants). The
sync layer uses `ContractJson` for all contract (de)serialization.

The OpenAPI generator renders `format: byte` as `kotlin.ByteArray`, which
kotlinx.serialization encodes as a JSON array of signed numbers while the Go
backend follows the base64-string convention. `core.network.SyncWire` and
`core.network.DuoWire` therefore mirror the envelope models with base64
`String` fields and map onto the generated models, which stay the app-facing
types everywhere else.

## Data mapping: Room <-> API

The client's local entities and the canonical API records are **not** 1:1.
The domain-level mappers live in `core.network.mapping` (`ContractMappers`,
`SymptomCatalogAdopter`); the sync engine drives them and owns envelope
lifecycle.

| Room entity (client)          | API record (canonical)                     | Sync status | Translation note |
|-------------------------------|--------------------------------------------|-------------|------------------|
| `CycleEntity`                 | `cycle`                                    | shipped     | `startDate`/`endDate` map directly. `averageLengthDays`/`lutealPhaseLengthDays` are client display hints, not synced. |
| `CycleEntity.periodDaysJson`  | `bleeding_observation` (one per day)       | shipped     | **Structural split:** the client stores bleeding days embedded in the cycle; the backend stores one cycle-agnostic row per observed day. The mapper fans out/collapses; the canonical association rule is `associatePeriodDays` (see `SYNC_BOUNDARY.md`). |
| `DailyEntryEntity`            | `daily_entry`                              | shipped     | `painLevel`/`moodLevel`/`energyLevel` (1-5, nullable) + `notes` map directly. `symptomIdsJson` is replaced by discrete `symptom_log` rows. Bleeding recorded on the entry also fans out to `bleeding_observation`. |
| `SymptomLogEntity`            | `symptom_log`                              | shipped     | Direct; `severity` 1-5. |
| (client `Symptom.DEFAULT_SYMPTOMS`) | `symptom_definition` (server-seeded) | partial     | Client must **adopt** server-seeded built-ins by `key`, not create its own (unique live `(account_id, key)` index); adoption logic exists (`SymptomCatalogAdopter`), full catalog sync is not yet wired. |
| (none yet)                    | `biomarker_observation`                    | not built   | New: BBT / cervical fluid / cervix. Needs Room entity + UI. |
| (none yet)                    | `medication_log`                           | not built   | New: medication + contraception context. Needs Room entity + UI. |
| `UserProfileEntity`           | `account` + `device` + `account_settings`  | partial     | `role`/`syncMode` are client-local; `life_stage`/`tracking_focus` map to settings. `PATCH /v1/me` is not yet called: account settings do not round-trip yet. |
| `DisorderConfigEntity`        | `account_settings.tracking_focus`          | partial     | `pms`/`pmdd`/`endometriosis`/`pcos`/`custom` are the focus values; never diagnoses. |
| `CouplePairing` / `DuoSharingPreferences` | `duo_links` / `duo_grants`     | shipped     | Per-field grants (`cycle_day`/`period_estimate`/`mood`/`energy`/`support_requests`), enforced client-side before sealing. |

### Envelope

Every synced record carries `id` (client UUIDv7), `client_rev` (new UUID per
local edit), `created_at`/`updated_at` (RFC 3339 UTC, truncated to the
minute), and `deleted_at` (tombstone). The client generates and persists these
alongside each Room row (the `sync_state` side table). `client_rev` is the
conflict tiebreak after `updated_at`. Records derived from a parent (bleeding
fanned out of a cycle or a daily entry) get deterministic ids via
`deterministicId(...)` so re-syncing upserts rather than duplicates. The
record payload is sealed before push (`RecordCrypto`, see `E2EE_DESIGN.md` §4)
with the routing fields bound as AEAD associated data.

## Milestone 1 — Full multi-entity sync engine

Goal: a tracker on one device and the same account on another converge, with
no silent loss. Status per item:

- [x] **Core entity types:** push/pull for `cycle`, `daily_entry`,
  `symptom_log`, with conflict adoption (`updated_at`, then `client_rev`,
  adopt-server-state).
- [x] **Bleeding↔cycle association:** the canonical rule is implemented
  (`associatePeriodDays`, client-side date-range derivation) and both bleeding
  sources sync — cycle-embedded period days and daily-entry bleeding both fan
  out to `bleeding_observation` rows.
- [x] **Deletions:** local deletes push tombstones in both directions and
  incoming tombstones delete local rows (cycles, daily entries, symptom logs).
- [ ] **Periodic + connectivity-aware sync:** add a scheduled `WorkManager`
  job with a network constraint alongside the existing on-demand trigger;
  local writes still never wait on network and the UI still observes Room
  only.
- [ ] **Account settings:** `account_settings` should round-trip via
  `PATCH /v1/me`; today the client never calls it.
- [ ] **Biomarkers and medication:** `biomarker_observation` and
  `medication_log` need Room entities, UI, and sync mapping.
- [ ] **Symptom catalog adoption:** wire server-seeded `symptom_definition`
  adoption into the live catalog flow (`SymptomCatalogAdopter` exists but is
  not yet driven by sync).
- [ ] **Deterministic convergence tests at entity depth:** extend
  `CycleSyncEngineTest` beyond cycles to daily entries and symptom logs —
  independent-field edits on two devices, delete-then-stale-mutation, and
  revocation-while-offline. Field-level merging is future work.

Exit: two devices on one account converge, including deletions, with no
silent loss; offline edits sync when connectivity returns.

## Milestone 2 — Account recovery and honest connectivity UX

- [x] **Account-code surface:** the code is viewable in Settings with copy
  support and honest copy (`settings_sync_account_code_body`: it is the only
  key, there is no reset). Registration still happens silently on first sync;
  the code becomes visible afterwards rather than at registration with an
  explicit "write this down" step — that first-run moment remains to build.
- [x] **Device recovery:** a new device restores the account from the code
  alone (`addDevice`), resets the pull cursor to zero, and syncs the full
  history. Verified end to end (`E2eRoundTripTest`).
- [x] **Production sync settings:** the `SyncMode` flow (off by default),
  invite-code entry, connection status, and last-sync state ship in Settings
  in all builds; the local base-URL editor and demo-data tools stay
  debug-gated.
- [x] **Truthful copy:** `sync_transport_notice` states end-to-end
  encryption, names what the server still sees (sync dates, record counts),
  and states the account-code-is-the-only-key consequence. No copy claims
  anything the transport does not deliver.
- [ ] **First-run account-code moment:** show the code with a confirmation
  step when an account is created, not only retroactively in Settings.

## Milestone 3 — Duo client

- [x] **Pairing:** invite creation, link-key generation on device, shareable
  URL with the key in the fragment, and accept-by-paste of the full link (a
  bare pairing code is rejected by design).
- [x] **Grant management:** per-field toggles over
  `PATCH /v1/duo/links/{id}/grants`, enforced client-side: an ungranted field
  is never sealed into the projection, so the server never receives it.
- [x] **Partner home:** built on `GET /v1/duo/view` (sealed projection), not
  a clone of the tracker, with no-data and key-missing states.
- [x] **Support thread and revocation:** sealed support requests with ack,
  link revocation, and widget-cache clearing on revoke.
- [ ] **QR pairing:** scan flow (CameraX/ML Kit) as an alternative to paste.
- [ ] **Deep links:** decide https App Link vs `luteal://` scheme for the
  pairing URL (the server's `FOLICULAR_PAIRING_BASE_URL` must match); today
  the link is pasted.
- [ ] **Revocation convergence:** a partner device holding a cached
  projection after the tracker revoked must converge on next connect (see
  `SYNC_BOUNDARY.md`, open conflict items).

## Production gates

Required before widening the invite rollout:

- [x] Threat model and key lifecycle documented (`E2EE_DESIGN.md`; verified
  end to end against a live server, including a no-plaintext database check).
- [ ] Deterministic convergence tests beyond cycles (Milestone 1).
- [x] Conformance fixtures shared with folicular (`contract/conformance`,
  decoded by `ConformanceFixturesTest`; the server's `internal/contract` test
  proves the same bodies).
- [x] UI copy does not claim connectivity/encryption before runtime proof
  (the E2EE claim in `sync_transport_notice` landed only after live
  verification).
