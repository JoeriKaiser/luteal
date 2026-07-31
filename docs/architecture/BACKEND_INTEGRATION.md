# Backend Integration Plan

How the Android client adopts the folicular backend without compromising the
offline-first product. The backend is the **source of truth for data**
(see `AGENTS.md` §4); the client remains offline-first for display and local
writes, conforms to the backend contract, and accepts the backend's resolved
state on conflict.

**Current state:** a cycles-only vertical slice is implemented and verified
end-to-end on a real device against the Dockerized backend (anonymous
register → push cycle + fanned bleeding → pull/read-back, with the device
token reused across syncs). Build gating, Keystore credentials, the OkHttp
transport, and an on-demand `WorkManager` sync are in place; see `AGENTS.md` §5
for how to run against a local server. The milestones below are the remaining
work.

## Foundation

### Contract pipeline

The contract is machine-readable and enforced, not hand-copied:

```
folicular/openapi/openapi.yaml   <- single source of truth
        |                                   |
        | (Go test: internal/contract)      | (Gradle: openApiGenerate)
        v                                   v
  spec must validate +              fr.luteal.core.network.contract.models
  cover every route                 (72 @Serializable DTOs, models only)
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

### Wire serializers

Generated models mark `java.util.UUID`, `LocalDate`, and `OffsetDateTime` as
`@Contextual`. `core.network.ContractSerializers` provides the serializers,
the `ContractSerializersModule`, and a ready `ContractJson` instance
(`ignoreUnknownKeys`, `encodeDefaults`), unit-tested for wire-format parity
(snake_case keys, `2026-06-30` dates, `2026-07-01T08:00:00Z` instants). The
sync layer uses `ContractJson` for all contract (de)serialization. The
polymorphic sync envelopes (`data`/`current`, typed `Any` in the generated
models) are carried as `JsonElement` via `core.network.SyncWire` and decoded
against the concrete record type.

## Data mapping: Room <-> API

The client's local entities and the canonical API records are **not** 1:1.
The domain-level mappers live in `core.network.mapping` (`ContractMappers`,
`SymptomCatalogAdopter`); the sync engine drives them and owns envelope
lifecycle.

| Room entity (client)          | API record (canonical)                     | Translation note |
|-------------------------------|--------------------------------------------|------------------|
| `CycleEntity`                 | `cycle`                                    | `startDate`/`endDate` map directly. `averageLengthDays`/`lutealPhaseLengthDays` are client display hints, not synced. |
| `CycleEntity.periodDaysJson`  | `bleeding_observation` (one per day)       | **Structural split:** the client stores bleeding days embedded in the cycle; the backend stores one cycle-agnostic row per observed day. The mapper fans out/collapses; the canonical association rule is `associatePeriodDays` (see `SYNC_BOUNDARY.md`). |
| `DailyEntryEntity`            | `daily_entry`                              | `painLevel`/`moodLevel`/`energyLevel` (1-5, nullable) + `notes` map directly. `symptomIdsJson` is replaced by discrete `symptom_log` rows. |
| `SymptomLogEntity`            | `symptom_log`                              | Direct; `severity` 1-5. |
| (client `Symptom.DEFAULT_SYMPTOMS`) | `symptom_definition` (server-seeded) | Client must **adopt** server-seeded built-ins by `key`, not create its own (unique live `(account_id, key)` index). |
| (none yet)                    | `biomarker_observation`                    | New: BBT / cervical fluid / cervix. Add Room entity + UI. |
| (none yet)                    | `medication_log`                           | New: medication + contraception context. |
| `UserProfileEntity`           | `account` + `device` + `account_settings`  | `role`/`syncMode` are client-local; `life_stage`/`tracking_focus` map to settings. |
| `DisorderConfigEntity`        | `account_settings.tracking_focus`          | `pms`/`pmdd`/`endometriosis`/`pcos`/`custom` are the focus values; never diagnoses. |
| `CouplePairing` / `DuoSharingPreferences` | `duo_links` / `duo_grants`     | Per-field grants (`cycle_day`/`period_estimate`/`mood`/`energy`/`support_requests`). |

### Envelope

Every synced record carries `id` (client UUIDv7), `client_rev` (new UUID per
local edit), `created_at`/`updated_at` (RFC 3339 UTC), and `deleted_at`
(tombstone). The client generates and persists these alongside each Room row
(today in the `cycle_sync_state` side table for cycles). `client_rev` is the
conflict tiebreak after `updated_at`. `SyncMeta` holds these values; records
derived from a parent (bleeding fanned out of a cycle) get deterministic ids
via `deterministicId(...)` so re-syncing upserts rather than duplicates.

## Milestone 1 — Full multi-entity sync engine

Goal: a tracker on one device and the same account on another converge, with
no silent loss. The cycles-only slice (register/push/pull for cycles + fanned
bleeding, on-demand worker, Keystore credentials, debug-gated permission,
adopt-server-state on conflict) is the foundation to extend.

- **All entity types:** extend push/pull beyond cycles to `daily_entry`,
  `symptom_log` (adopting the server-seeded catalog by `key` via
  `SymptomCatalogAdopter`), `biomarker_observation`, and `medication_log`
  (the latter two need new Room entities + UI).
- **Bleeding↔cycle association:** the canonical rule is settled and
  implemented (`associatePeriodDays`, client-side date-range derivation; see
  `SYNC_BOUNDARY.md`). Remaining: make bleeding sync correctly given the app
  records it on the `DailyEntry`, not on `Cycle.periodDays` (push the
  daily-entry-derived `bleeding_observation` rows).
- **Deletions:** propagate local deletes as tombstones in both directions
  (today only incoming tombstones are applied).
- **Periodic + connectivity-aware sync:** add a scheduled `WorkManager` job
  with a network constraint alongside the on-demand trigger; local writes
  still never wait on network and the UI still observes Room only.
- **Account settings:** `account_settings` is server-authoritative; refresh
  on pull and edit via `PATCH /v1/me`.
- **Deterministic conflict/convergence tests:** entity-level LWW
  (`updated_at`, then `client_rev`) with adopt-server-state on conflict;
  cover independent-field edits, delete-then-stale-mutation, and
  revocation-while-offline. Field-level merging is future work.

Exit: two devices on one account converge, including deletions, with no
silent loss; offline edits sync when connectivity returns.

## Milestone 2 — Account recovery and honest connectivity UX

- **Account-code backup/recovery:** the account code is the only credential
  and is shown once. Surface it at registration with an explicit
  "write this down" step plus a confirmation/backup flow (today registration
  is silent and the code is never shown).
- **Production sync settings:** replace the debug-only trial card with a real
  `SyncMode` (`OFFLINE_LOCAL` / `ONLINE_CLOUD`) flow, connection status, and a
  last-synced indicator.
- **Truthful copy:** no claim of sync or encryption before runtime proof; no
  E2EE claim until the threat model and key lifecycle exist (see gates).

## Milestone 3 — Duo client

- Pairing: scan QR (CameraX/ML Kit) or open the `pairing_url` deep link ->
  `POST /v1/duo/links`. Decide https App Link vs `luteal://` scheme (the
  server's `FOLICULAR_PAIRING_BASE_URL` must match).
- Tracker grant management UI over `PATCH /v1/duo/links/{id}/grants`.
- Partner home built on `GET /v1/duo/view` (grants-respecting projection),
  not a clone of the tracker.
- Support-request thread UI + revocation states.

## Production gates

Required before production networking:

- [ ] Threat model and key lifecycle documented (informs the E2EE decision;
      see `folicular/docs/architecture.md`).
- [ ] Deterministic offline-mutation and conflict tests (see Milestone 1).
- [ ] Conformance fixtures shared with folicular (golden request/response
      pairs; `folicular/scripts/smoke.sh` is the executable seed).
- [ ] UI copy does not claim connectivity/encryption before runtime proof.
