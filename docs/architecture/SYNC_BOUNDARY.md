# Backend-neutral Sync Boundary

## Current state

The app has no production sync transport and declares no network permissions. Duo sharing choices are stored locally and the partner surface is an explicit local preview.

## Domain principles

- Local writes succeed without a server.
- UI models never depend on HTTP, Go DTOs, or a particular database implementation.
- Every synchronized record has one owner and an explicit visibility policy.
- Permission grants are separate records, not booleans embedded into private observations.
- Revocation is a protocol event that must reach all authorized devices.
- Deletion needs tombstone semantics before multi-device synchronization exists.
- Dates use ISO `LocalDate` values for user calendar observations.
- Mutation times use UTC `Instant` values and are not used as the sole conflict authority.
- Server sequence or revision values remain transport metadata, not domain truth.

## Contracts to define before networking

### Record envelope

A future transport envelope should specify:

- Stable UUID
- Record type and schema version
- Owner identifier
- Local revision identifier
- Created and modified instants
- Optional tombstone
- Encrypted payload
- Permission or relationship scope where applicable

Exact field names and cryptographic formats are deliberately undecided.

### Conflict behavior

Define deterministic policies for:

- Independent fields edited on separate devices
- Daily entry edited by the same owner on two devices
- Cycle-start correction after an estimate was generated
- Deletion followed by stale-device mutation
- Sharing revocation while another device is offline
- Relationship deletion and retained private history

A last-write-wins timestamp alone is not sufficient because device clocks are not authoritative and silent loss is unacceptable.

### Bleeding↔cycle association (settled)

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

Wiring bleeding recorded on the `DailyEntry` (the second bleeding source) into
sync is tracked under Backend Integration Milestone 1.

### Encryption boundary

Do not describe the system as end-to-end encrypted until the following are reviewed and implemented:

- Threat model and attacker capabilities
- Key generation and storage
- Pairing and device authorization
- Key rotation and device removal
- Recovery policy
- Metadata exposure
- Replay and rollback protection
- Protocol versioning
- Independent security review strategy

## Android architecture target

The future sync layer should implement an interface below repositories or through a dedicated coordinator. Room remains the local source of truth. UI state continues to observe local repositories and never waits on a remote response to display existing data.

Network permissions should be introduced only in the online build path that contains a functioning transport and truthful user controls.
