# Spec 14: Hardening Lock, Sync Identity, Restore, and Editor Integrity

## Problem Statement

Specs 07–13 landed as a single implementation pass. A critical review found that several of those surfaces do not yet honour the product’s own privacy, integrity, and honesty rules:

1. **App lock can fail open.** Until preferences load, the lock is treated as “not configured”, so cycle data is visible and usable on a cold start. A 4-to-8-digit PIN is advertised, but the lock screen submits after four digits, so a longer PIN can never unlock the app.
2. **Locking tears down the session.** Replacing the whole Compose tree on lock destroys in-flight file pickers (backup import/export, consultation report). The default timeout is immediate, so this happens the moment the system picker opens.
3. **Local restore can resurrect or clobber data.** Replace-all clears sync bookkeeping without writing deletion markers, so a later sync can bring deleted records back. Merge overwrites symptom logs and preferences even when the backup is older.
4. **Synced biomarkers never settle.** Local bookkeeping uses a date-prefixed id; the server acknowledges a derived UUID. Clean, reject, and delete updates miss the local row, so dirty biomarkers re-push forever and multi-device deletes do not land.
5. **The daily editor and Duo companion mis-report their own state.** Fahrenheit temperature steps are clamped to the Celsius range and can wipe a reading. A new day always looks “edited”. Duo freshness is always “just updated”. Quick-support chips lose their category on send.
6. **Operational follow-through is missing.** Cycle edits do not reschedule local reminders. HTML reports are requested as PDF. Report success and error never reach Settings. The cycle-history visualizer still speaks French in English.

These are not new product ideas. They are the conditions under which 07–13 can be trusted.

## Solution

Close the integrity gaps in dependency order: lock first (privacy), then sync identity and restore (data), then the editor and Duo (honesty), then reminders, reports, and copy (follow-through).

The lock becomes fail-closed and overlay-based: the session stays mounted under a full-screen barrier, so a picker can return. Restore writes deletion markers for records that disappear in a replace-all, and merge only applies newer incoming rows. Sync bookkeeping is updated by the local id, not only by the wire UUID. The editor uses unit-aware temperature bounds and change detection that ignores bookkeeping timestamps. Duo freshness is read from the last successful cache write, and a nudge keeps the category it was composed with.

## User Stories

1. As a user who enabled the app lock, I want Luteal to stay locked until my PIN or biometric succeeds, including during the first milliseconds after launch, so that handing someone my unlocked phone never flashes my journal.
2. As a user with a 6-digit PIN, I want to enter all six digits and then confirm, so that I can open the app I locked.
3. As a user whose PIN is 4 digits, I want submit-on-complete still to work, so that the short PIN is not slower than today.
4. As a user exporting a backup or generating a consultation report, I want the system file picker to return to the same Settings or Journal screen, so that the file I just chose is not discarded because the app locked.
5. As a user who locked Luteal while a picker was open, I want to authenticate and land back in the same place, so that I do not lose the restore preview or the report I was about to save.
6. As a TalkBack user on the lock screen, I want the PIN field, submit action, and lockout countdown announced, so that I can unlock without sight.
7. As a user restoring a backup with “replace all”, I want records that existed on this device but are absent from the file to stay gone after the next sync, so that an old journal does not come back from another device.
8. As a user restoring with “merge”, I want a newer local symptom log to win against an older backup of the same log, so that last night’s note is not overwritten.
9. As a user restoring with “merge”, I want my current tracking contexts and temperature unit to stay as I set them unless I choose replace-all, so that merging history does not silently turn contexts off.
10. As an online user, I want a successful biomarker sync to clear the local dirty flag, so that the same temperature is not pushed on every pass.
11. As an online user who deleted a biomarker on another device, I want that deletion to apply here, so that the two journals converge.
12. As an online user who deleted a biomarker here while it was already gone locally, I want the deletion marker to be acknowledged and dropped, so that it does not retry forever.
13. As a user recording basal temperature in Fahrenheit, I want plus and minus to stay inside 93.20–107.60 °F, so that a tap cannot snap the value to 42 °F and drop the reading.
14. As a user who opened today’s editor without changing anything, I want Back to close the sheet, so that I am not asked to discard edits I did not make.
15. As a user who recorded 36.55 °C, I want the editor to reopen at 36.55 °C, so that a no-op save does not rewrite the value.
16. As a French-speaking user, I want the displayed temperature to use a decimal comma, so that the number matches the rest of the interface.
17. As a TalkBack user, I want the temperature stepper and quick-support chips to be 48 dp targets, so that I can hit them reliably.
18. As a Duo partner, I want the freshness line to reflect when the projection was last actually received, so that “updated today” is not a lie after a week offline.
19. As a Duo partner with no connection, I want the last cached projection and an aging or stale badge, so that the companion remains usable offline.
20. As a Duo partner sending “I did the shopping”, I want that message to keep the practical category, so that it is not stored as a generic note.
21. As a user who edits or deletes a cycle start, I want period-window and late-cycle reminders to be recalculated immediately, so that I am not woken by an alarm for a cycle that no longer exists.
22. As a user generating an HTML consultation report, I want the system picker to create an HTML file, so that the document opens as a page rather than a broken PDF.
23. As a user who just exported a report from Settings, I want success or failure to appear on that screen, so that I know whether the file was written.
24. As an English-speaking user on the cycle-history tab, I want labels, detail sheet, and TalkBack to be in English, so that variability is not described in leftover French.
25. As a user with no cycle history, I want the empty-state action to start a period or be omitted, so that a primary button never does nothing.
26. As a TalkBack user on the thermal chart, I want a summary that states whether a coverline is present and how many disturbed readings there are, so that the chart is not a silent picture.
27. As a developer, I want unit tests on lock cold-start, PIN length, restore tombstones, sync id mapping, Fahrenheit bounds, and editor dirty detection, so that these cannot regress silently.

## Implementation Decisions

### 1. Lock is fail-closed and overlays the session

Amend spec 08’s “do not instantiate the main scaffold while locked”. That rule prevents Room work in the background, but it also destroys Activity Result contracts. The barrier stays: no journal, settings, or Duo interaction while locked. The session underneath may remain composed, fully obscured, and non-interactive.

Lock state gains an explicit unresolved value used only before preferences are read. The Compose root treats unresolved and locked the same: show the lock screen (or an equivalent opaque barrier), never the journal. After load, “not configured” is the only state that reveals the app without authentication.

### 2. PIN entry matches the stored length

The lock screen does not assume four digits. It accepts 4–8 digits. Submission happens when the entered length reaches the stored PIN length, or via an explicit confirm action if the stored length cannot be read without leaking it. Failed attempts still rate-limit. A 4-digit PIN may still submit on the fourth digit.

### 3. Restore writes the same dirty/tombstone contract as a manual edit

Replace-all, after clearing tables, registers a deletion marker for every previously known synchronized id that is not present in the incoming backup, then marks imported rows dirty. Merge compares incoming timestamps to local ones for daily entries, biomarkers, and symptom logs; older incoming rows are skipped. Preferences apply on replace-all only. Merge leaves role, locale, contexts, age band, and temperature unit untouched.

### 4. Sync bookkeeping is keyed by the local id

The wire may keep using a deterministic UUID. After push apply, reject, or conflict, the engine resolves that UUID back to the local bookkeeping id (date-prefixed biomarker id, date-keyed daily entry, or UUID symptom/cycle id) before marking clean, rejected, or deleted. A server tombstone with no ciphertext deletes the local row and its bookkeeping. A local tombstone whose observation is already gone is still acknowledged and dropped.

This mapping is shared by biomarkers and daily entries so the two date-keyed types cannot drift.

### 5. Editor state ignores bookkeeping timestamps

Temperature steppers use Celsius hundredths 3400–4200 and Fahrenheit hundredths 9320–10760. Display rounding uses half-up to two decimals in the UI locale. Change detection compares observation fields, not `updatedAt`. An empty biomarker on a new day is not a change. Wake-time input is validated; invalid text is not silently dropped on save without an error.

### 6. Duo freshness is the cache’s timestamp

Freshness is computed from the last successful projection cache write (or an equivalent persisted “as of”), not from the instant the view model ran. Age under 24 hours is current, up to 7 days is aging, older is stale. A failed refresh keeps the last cached projection and updates only the badge. Quick-support chips send with the chip’s kind; prefilling the draft does not change that.

### 7. Reminders and reports follow the data

Any local write that can change the estimated window (cycle create/edit/delete/exclusion, daily entry that starts a cycle, restore, wipe) reconciles local reminder schedules in the same operation. The report file picker uses PDF or HTML according to the chosen format. Report generation status is a collected state, not a value read once inside an already-full combine.

### 8. Copy lives in resources

Cycle-history labels, detail sheet, and TalkBack descriptions move to the English default and French files with existing keys where they already exist. The thermal chart exposes a single summary description (cycle, point count, disturbed count, coverline present or not). Per-point TalkBack on a canvas is deferred.

## Testing Decisions

Test observable behaviour at existing seams: lock state as collected by the activity, restore summaries and post-restore Room/sync rows, sync engine apply/clean/tombstone outcomes, editor dirty detection and temperature bounds as pure functions where possible, Duo freshness from a stamped cache time.

Prior art: app-lock manager tests, PIN crypto tests, import/export manager tests, cycle sync engine tests with in-memory fakes, notification content tests, phase-tips source/string tests.

A good test asserts what the user or the other device would observe (locked vs open, dirty vs clean, row present vs gone, °F still in range). It does not assert Compose internals or exact SQL.

Required coverage:

- Cold start with lock enabled never emits an unlocked session before authentication.
- A 6-digit PIN verifies; a 4-digit prefix of it does not.
- Replace-all of a backup missing one existing cycle leaves a deletion marker for that cycle.
- Merge of an older symptom log does not replace a newer local log.
- Merge does not change tracking contexts.
- A pushed biomarker is marked clean under its local id.
- A biomarker delete conflict with no ciphertext removes the local observation.
- Fahrenheit increment stays inside the valid range and saves.
- Opening an empty editor and pressing Back does not show the discard dialog.
- Freshness is stale when the cache stamp is eight days old.
- A practical nudge is created with the practical kind.

## Out of Scope

- Redesigning the lock visual language or adding a duress/decoy PIN.
- BLE thermometers, strip OCR, fertile-window traffic lights.
- Periodic background sync (still spec 06).
- Changing the folicular Go runtime; routing already accepts the biomarker entity type.
- Per-point TalkBack on the thermal canvas.
- Rewriting Settings into smaller files, except where the report-state combine forces a seam.

## Further Notes

Work in this order. Later slices assume the earlier integrity guarantees.

1. Fail-closed lock overlay and PIN length (privacy; unblocks file pickers).
2. Sync local-id mapping and tombstone adopt (online integrity).
3. Restore tombstones, merge timestamps, merge-safe preferences (offline integrity).
4. Editor temperature bounds and dirty detection (prevents silent data loss).
5. Duo freshness from cache and nudge kind (companion honesty).
6. Reminder reconcile, report MIME and status, visualizer copy (follow-through).

Slices 2 and 3 can proceed in parallel after 1 if needed; 4 and 5 are independent of each other; 6 should wait until restore and cycle writes are stable so reminder reconcile has a single call site.

The OpenAPI additive biomarker fields already synced into the vendored contract stay. No further backend change is required for this spec.
