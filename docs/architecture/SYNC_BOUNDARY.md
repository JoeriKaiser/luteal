# Sync Boundary

Status: **live.** Luteal ships an end-to-end encrypted sync transport against
the folicular backend, gated behind `SyncMode.ONLINE_CLOUD` (off by default).
The offline app is the default path and never touches the network.

This document records the boundary between the offline-first local domain and
the transport: what is settled, what the shipped envelope looks like, and which
conflict questions remain open.

## Current state

- The sync engine (`core/network/sync/CycleSyncEngine`) registers on first
  use (invite-code gated), pushes dirty records, pulls deltas since a stored
  cursor, and applies them to Room. Room stays the local source of truth; UI
  state observes Room only and never waits on a network response.
- Synced entity types: `cycle`, `bleeding_observation` (fanned out of cycles
  and of daily entries), `daily_entry`, `symptom_log`. Deletions propagate as
  tombstones in both directions.
- Sync runs through an on-demand `WorkManager` worker (`app/sync`). There is
  no periodic schedule yet; see `BACKEND_INTEGRATION.md` Milestone 1.
- Duo is live: invite, accept-by-paste of the full pairing link, per-field
  grants, revocation, and sealed support messages. The tracker's device
  composes and seals the projection; the server relays ciphertext only.
- Local writes succeed without a server. `SyncMode.OFFLINE_LOCAL` is the
  default and the worker is a no-op in it.

## Domain principles

- Local writes succeed without a server.
- UI models never depend on HTTP, Go DTOs, or a particular database
  implementation.
- Every synchronized record has one owner and an explicit visibility policy.
- Permission grants are separate records, not booleans embedded into private
  observations. Under E2EE they are enforced on the tracker's device: an
  ungranted field is never encrypted, so the server never receives it.
  Revocation is enforced by the tracker ceasing to publish; the server's grant
  list is advisory metadata for display.
- Deletion uses tombstones in both directions: local deletes push tombstones,
  incoming tombstones delete local rows.
- Dates use ISO `LocalDate` values for user calendar observations.
- Mutation times use UTC `Instant` values and are not used as the sole
  conflict authority.
- Server sequence or revision values remain transport metadata, not domain
  truth.

## Record envelope (shipped)

The transport envelope is defined in `core/network/SyncWire.kt` and generated
contract models. Per change:

- `entity_type` and `entity_id` (client UUIDv7) for routing
- `client_rev` (new UUID per local edit), the conflict tiebreak after
  `updated_at`
- `updated_at` (and `deleted_at` for tombstones): UTC, truncated to the
  minute before sending, so a pushed batch does not leak an entry timeline
- `deleted`: tombstone marker; `ciphertext` is null when true
- `ciphertext`: the sealed record, `0x01 || nonce(12) || ciphertext || tag(16)`
  under the account-code-derived record key (see `E2EE_DESIGN.md` §4)

Routing metadata is plaintext by necessity, the server must route upserts it
cannot read, and is bound into the AEAD associated data
(`"<entity_type> <entity_id> <client_rev>"`), so the server cannot move a
payload onto another record or relabel its type without an authentication
failure. Records derived from a parent (bleeding fanned out of a cycle or a
daily entry) get deterministic ids via `deterministicId(...)` so re-syncing
upserts rather than duplicates.

## Conflict behavior (settled for v1)

Policy: entity-level last-write-wins ordered by `updated_at`, then
`client_rev`. On a push conflict the client adopts the server's current record
and re-derives its local cache from it. Field-level merging is future work.

Covered by deterministic tests (`CycleSyncEngineTest`): first-run
register/push/pull, token reuse, conflict adoption, incoming tombstones,
paginated pulls, auth failure clearing credentials.

Open items, tracked under `BACKEND_INTEGRATION.md` Milestone 1:

- Daily-entry and symptom-log convergence tests at the same depth as cycles
  (independent-field edits on two devices, delete-then-stale-mutation).
- Revocation-while-offline: a partner's device holding a cached projection
  after the tracker revoked must converge when it next connects.
- Stale-device mutation after relationship deletion.

## Bleeding↔cycle association (settled)

The backend stores bleeding as cycle-agnostic per-day observations (one per
date, unique live `(account_id, observed_date)`; bleeding is a neutral
observation, see `folicular/docs/data-model.md`). The client embeds period
days inside a cycle. The canonical rule for rebuilding a cycle's period days
from bleeding observations is implemented in
`core.network.mapping.associatePeriodDays` and is deterministic so every
device converges:

- An observation belongs to the cycle when its date falls within
  `[start_date, end_date]`; an open cycle (`end_date == null`) takes every
  observation from `start_date` onward.
- `flow == none` and `intermenstrual == true` observations are not period days.
- One period day per date; duplicates collapse to the heaviest flow
  (order-independent).
- Result sorted by date; when nothing matches, the cycle's existing local
  period days are preserved (an incremental pull of just a cycle record never
  wipes them).

Both bleeding sources sync: cycle-embedded period days and daily-entry
bleeding are fanned out to `bleeding_observation` records on push.

## Encryption boundary (implemented)

Record content is end-to-end encrypted; the full design, threat model, and
verification record live in `E2EE_DESIGN.md`. Summary of what the server can
still see, by design: account and device existence, `entity_id`/`entity_type`,
`seq`, the `deleted` flag, minute-truncated `updated_at`, ciphertext length,
the Duo link graph, and request timing/volume/IP. Honest copy about this lives
in `sync_transport_notice`.

Remaining crypto-adjacent product work: Duo link-key loss requires re-pairing
(reinstall loses the key), account-code loss is unrecoverable by construction,
and there is no forward secrecy for records. All three are stated in the UI
and accepted for v1; see `E2EE_DESIGN.md` §2.

## Android architecture

The sync layer sits below repositories in a dedicated coordinator
(`CycleSyncEngine`); `SyncWorker` drives it and writes outcome state to
`SyncDataStore` for the UI to observe. Room remains the local source of
truth. UI state observes local repositories and never waits on a remote
response to display existing data.

Network permissions are declared in the main manifest (both build types can
sync); cleartext HTTP is debug-only, and sync runs only when
`SyncMode.ONLINE_CLOUD` is enabled. The sync UI is available in all builds;
the local base-URL editor and demo-data tools are debug-gated.
