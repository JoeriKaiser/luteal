# End-to-End Encryption Design

Status: **implemented and verified end to end.** Key hierarchy, record sealing,
the folicular schema and handler migration, client sync integration, Duo key
agreement, and account-code recovery are all in place and covered by tests.

Verified against a live server on 2026-07-25 (`E2eRoundTripTest`, opt-in via
`-Pfolicular.e2e.url`): register, seal, push, pull and decrypt round-trips; a
second device recovers the account from the code alone and reads the first
device's record. Inspecting the resulting SQLite file directly found **no
plaintext at all** — no notes, dates, or enum values, only the `0x01` version
byte and ciphertext.

Remaining before an invite rollout: run the app itself against a real server on
a device or emulator. The trial above exercises the protocol and crypto from the
JVM, not the Compose UI or Keystore-backed storage.

This document is the single design reference for both repositories. The Android
client is `luteal`; the Go backend is
[`folicular`](https://github.com/JoeriKaiser/folicular).

---

## 1. Terminology, and one thing this design does not claim

Under GDPR, **anonymisation** (Recital 26) means data irreversibly stripped of
identifiers, placing it outside the regulation. This product cannot achieve
that, and no amount of engineering will change it: a personal cycle history is
inherently identifying, and synchronisation exists precisely to return it to the
person it belongs to. A server that truly could not re-associate the data could
not sync it.

What this design achieves is **pseudonymisation** (Art. 4(5)) plus data
protection by design (Art. 25), implemented as end-to-end encryption. That is
the stronger engineering position and the honest legal one:

- Cycle and observation data is Art. 9 special-category health data.
- Under E2EE the server holds ciphertext plus routing metadata, which
  materially reduces Art. 32 obligations.
- A breach of ciphertext is generally not notifiable to data subjects under
  Art. 34, because the data is unintelligible without keys the operator
  never holds.

**Copy rule:** never use "anonyme" in user-facing text. Once record sealing ships
end to end, "chiffré de bout en bout" becomes accurate and is the stronger claim
anyway. Until then, `sync_transport_notice` states plainly that transport is
HTTPS but not end-to-end encrypted.

---

## 2. Threat model

### Trusted

- The user's own device, and the Android Keystore on it.
- The account code, which exists only in the user's possession and in
  device-local Keystore-backed storage.

### Not trusted

- The server, its operator, its host, and its database backups. The design
  assumes an honest-but-curious operator and aims to make a malicious one
  unable to read content.
- The network. Mitigated by HTTPS independently of E2EE.

### What the server can still see, by design

This list is deliberately exhaustive. It is what an operator or a database
seizure yields even with E2EE fully deployed:

| Visible | Why it must stay visible |
|---|---|
| Account existence and creation time | Identity and invite gating |
| Device count and per-device labels | Device management and revocation |
| `entity_id`, `entity_type` | Routing and upsert targeting |
| `seq`, `deleted` flag | Delta pull ordering and tombstones |
| `updated_at`, truncated to the minute | Last-write-wins ordering |
| Ciphertext length | Unavoidable; padding is a later option |
| Duo link graph: who is paired with whom | Relationship routing |
| Request timing, volume, and source IP | Inherent to serving HTTP |

Traffic volume alone still reveals roughly *when* someone logs, even when it
cannot reveal *what*. Batching and padding reduce this and never eliminate it.
Say so plainly rather than overclaiming.

### Residual risks accepted for v1

- **Duo key exchange is no longer server-mediated**, so the MITM risk that a
  server-brokered X25519 exchange would have carried does not apply: the link
  key travels in the pairing URL fragment and folicular never sees it (see
  section 5). The residual exposure is that anyone who can read the shared link
  can join the Duo — the same property the bare pairing code always had.
- **Losing a Duo link key means re-pairing.** It is held only on the two paired
  devices. A reinstall loses it, and the UI says so (`duo_key_missing`) rather
  than failing silently.
- **Account code loss is unrecoverable.** It is the root of the key hierarchy.
  There is no reset, by construction. The UI must say this before the user
  depends on sync.
- **No forward secrecy for records.** Compromise of the account code decrypts
  all past records. Acceptable for a personal data store; revisit if the threat
  model changes.

---

## 3. Key hierarchy

Implemented in `core/network/crypto/RecordCrypto.kt`, verified by
`RecordCryptoTest`. HKDF-SHA256 is implemented in-tree
(`core/network/crypto/Hkdf.kt`) and validated against all three RFC 5869
Appendix A published vectors.

```
account code  (100 bits, Crockford base32, shown once, never re-transmitted)
  |
  +-- SHA-256(normalised)                     -> auth hash, stored server-side
  |
  +-- HKDF(ikm = normalised code,
  |        salt = account_id,
  |        info = "luteal/v1/master")         -> master key, never leaves device
        |
        +-- HKDF-Expand(info="luteal/v1/record") -> record content key
        +-- HKDF-Expand(info="luteal/v1/duo")    -> Duo root key material
```

**Why a plain KDF and not Argon2id.** Slow password-hardening KDFs exist to
compensate for low-entropy human-chosen secrets. The account code is a uniformly
random 100-bit value, so stretching buys nothing against brute force and only
costs battery.

**Domain separation.** The server already stores only
`SHA-256(normalised_code)` for authentication. The master key is derived through
HKDF with a distinct `info` label, so a full database leak of auth hashes yields
nothing usable for decryption. `RecordCryptoTest` asserts this directly.

**Normalisation must match exactly.** `RecordCrypto.normalizeAccountCode` mirrors
the server's `auth.NormalizeCode` (trim, uppercase, strip `LTL-` prefix, strip
dashes and spaces), in that order. Any divergence silently produces keys that
never match. Both are covered by tests.

---

## 4. Record sealing

AES-256-GCM, fresh random 96-bit nonce per record, 128-bit tag.

```
wire format:  0x01 || nonce(12) || ciphertext || tag(16)
associated data:  "<entity_type> <entity_id> <client_rev>"
```

Binding the routing metadata as AEAD associated data means a server that swaps
one record's payload onto a different entity, or relabels its type, produces an
authentication failure rather than silently corrupted data. Both substitutions
are covered by tests.

Nonce reuse is the failure mode that breaks GCM. With random 96-bit nonces under
a single key, collision probability stays negligible far beyond any plausible
per-account record count. If record volume ever changes by orders of magnitude,
revisit with a deterministic nonce derived from `client_rev`.

---

## 5. Duo key agreement (implemented)

The 50-bit pairing code **cannot** be the encryption key. It is short-lived,
single-use, and low entropy — adequate as a bearer secret on a rate-limited
endpoint, brute-forceable by a server holding the ciphertext.

**This section changed during implementation.** The original design was
server-mediated X25519. On building it, that turned out to have a worse property
than it first appears: the server introduces the two devices' public keys, so a
malicious operator can substitute its own and read everything. Closing that
requires an out-of-band safety-number comparison — which is *exactly* the
out-of-band channel the pairing link already is. The extra machinery bought
nothing the link did not already provide.

Implemented instead (`core/network/crypto/DuoCrypto.kt`):

1. The tracker generates a random 256-bit link key on device.
2. It is placed in the **fragment** of the pairing URL: `…/accept?code=<code>#k=<key>`.
   Fragments are never transmitted to a server, and this one is constructed
   entirely client-side, so folicular never sees it.
3. The partner pastes the whole link; the client splits the code (for the API
   call) from the key (kept local).
4. Both devices store the key in Keystore-backed storage
   (`core/network/crypto/DuoKeyStore.kt`), keyed by link id.
5. Payloads are sealed with AES-256-GCM under `HKDF(link_key, salt=link_id,
   info="luteal/v1/duo/payload")`, with the link id bound as associated data so
   a payload cannot be replayed onto another link.

The pairing code authenticates the *link*; the fragment carries the *key*.
`DuoCryptoTest` asserts the key never appears before the fragment, and that a
bare pairing code is rejected rather than silently downgrading to no encryption.

**Trust model:** unchanged from the pairing link itself — whoever can read the
link can join the Duo. That was already true of the bare pairing code. No
BouncyCastle dependency was needed.

---

## 6. Duo payload composition moves to the client

Today `DuoView` is composed server-side from plaintext (`internal/api/duo.go`).
Under E2EE the server cannot do this. The tracker's device composes the shared
payload, applies grants locally, encrypts under the Duo link key, and pushes it;
the server relays ciphertext.

This is a **stronger** privacy model than the current one, not merely an
equivalent one: grants stop being a server-side filter over data the server
already holds, and become a client-side decision about what is ever encrypted
and transmitted. The server cannot leak what it never received.

Consequence: `patchGrants` becomes advisory metadata for UI display. Revocation
must be enforced by the tracker's device ceasing to publish, not solely by a
server flag.

---

## 7. Backend authority is inverted

`luteal/AGENTS.md` §4 currently makes the backend canonical for validation,
enum vocabularies, and computed estimates. **A server that cannot read payloads
cannot validate or compute them.** This is the central architectural
consequence and must be recorded in both `AGENTS.md` files when the migration
lands.

| Concern | Before | After |
|---|---|---|
| Content validation | `internal/domain` | Client, before sealing |
| Computed estimates | `internal/cyclecalc`, `GET /v1/predictions/current` | Client `CycleEstimateCalculator` |
| Conflict resolution | Server LWW on `updated_at` | Unchanged: routing metadata stays plaintext |
| Schema authority | Server migrations | Client record schema, versioned inside the sealed payload |

Retiring `internal/cyclecalc` also resolves two live defects found during the
audit:

- It carries `minRangeRadiusDays = 2`, the same overconfidence already fixed on
  the client, citing the same Bull 2019 source. The two estimators currently
  disagree.
- It computes ovulation and fertile windows (`lutealConstantDays`,
  `spermSurvivalDays`, `eggSurvivalDays`), which conflicts with the client's
  explicit non-goal of presenting a fertile window derived from calendar data
  (`docs/product/CYCLE_TRACKER_FEATURE_RESEARCH.md`, "Explicit non-goals").

Because payloads become opaque, the sealed plaintext must carry its own
`schema_version` so the client can migrate its own records.

---

## 8. Remaining migration sequence

Ordered so both repositories build and stay green at each step.

1. **Contract first.** `folicular/openapi/openapi.yaml` is the single source of
   truth; the Kotlin client is generated from it at build time
   (`app/build.gradle.kts` reads it directly). Replace the typed `data` field on
   `SyncChangeInput` / `SyncPullChange` with a base64 `ciphertext` field, remove
   `/v1/predictions/current`, and add the device public key to registration.
2. **Server schema.** Additive migration `000002`: create a `records` table
   (`account_id`, `entity_id`, `entity_type`, `ciphertext`, `client_rev`,
   `updated_at`, `deleted_at`) and drop the seven typed record tables.
   `sync_changes.payload` carries ciphertext. Regenerate with `sqlc generate`
   (sqlc 1.31.1 and Go 1.25.5 are both installed).
3. **Server handlers.** The seven-case `dispatchChange` switch in
   `internal/api/sync.go` collapses to one opaque path — this deletes more code
   than it adds. Envelope and routing validation stays; content validation goes.
   Remove `internal/cyclecalc`, `internal/api/predictions.go`, and the typed
   read endpoints `/v1/cycles` and `/v1/days`.
4. **Client sync.** `SyncWire` carries the sealed envelope; `CycleSyncEngine`
   seals before push and opens after pull. Master key is stored alongside the
   device token in the existing Keystore-backed `EncryptedSyncCredentialStore`.
5. **Duo.** X25519 identity keys, ECDH at accept time, client-composed encrypted
   `DuoView`, device-enforced grants.
6. **Docs and copy.** Both `AGENTS.md` files, `SYNC_BOUNDARY.md`,
   `BACKEND_INTEGRATION.md`. Only after step 4 lands may
   `sync_transport_notice` be replaced with an accurate end-to-end encryption
   statement — and only after the safety-number screen for Duo.

**Data loss note.** Step 2 drops the plaintext record tables. This is safe only
because there is no production deployment yet. `folicular` currently has **no
git commits at all** — every file is untracked, so there is no undo. Make a
baseline commit there before starting step 2.

---

## 9. Already shipped

Independent of the migration, and live now:

- **Device label** (`core/network/auth/DeviceLabel.kt`). Registration no longer
  sends `Build.MODEL`, which was a fingerprinting signal with no sync function.
  A stable random label is generated once and persisted.
- **Timestamp coarsening** (`CycleSyncEngine.toCoarseUtc`). Envelope timestamps
  are UTC-normalised and truncated to the minute. Millisecond precision let the
  server reconstruct a minute-by-minute timeline of when each observation was
  entered — meaningful precisely because an offline-first client pushes batches
  long after the fact.
- **Rate-limit key hashing** (`internal/server/ratelimit.go`). Client addresses
  are HMAC'd under a per-process random pepper before use as bucket keys, so raw
  IPs are not held in memory and keys are unlinkable across restarts. Request
  logging already recorded no IPs, headers, tokens, or bodies.
