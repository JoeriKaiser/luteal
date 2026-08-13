# Spec 04: Historical Cycle-Start Editing and Deletion

## Problem Statement
Currently, users can record a cycle start for today or backfill a past start date using `BackfillCycleDialog`. However, if a user made a typo, accidentally tapped "Period start", or wants to adjust past cycle boundaries, there is no way to edit the start date or delete a recorded cycle without wiping the entire local database. Furthermore, cycle lengths, estimates, and E2EE sync tombstones must remain consistent when a cycle start is edited or removed.

## Solution
Provide a seamless, non-destructive cycle management interface in the Journal:
1. **Cycle Start Edit Dialog (`EditCycleDialog`):** Allows modifying the start date of an existing cycle. Validates date plausibility (cannot overlap with another cycle's start date, cannot be in the future).
2. **Cycle Start Deletion (`DeleteCycleDialog`):** Allows removing a recorded cycle start. Deleting a cycle start recalculates prior/subsequent cycle lengths and recalculates `CycleEstimate`.
3. **Room & Sync Consistency:** Updates `CycleEntity` in Room and queues an E2EE tombstone/upsert so changes propagate cleanly to synchronized devices under last-write-wins ordering.
4. **Localization:** French-default strings with full English parity.

## User Stories
1. As a cycle tracking user, I want to edit the start date of a past cycle if I entered the wrong day, so that my cycle history is accurate.
2. As a cycle tracking user, I want to delete an accidentally recorded cycle start, so that false entries do not distort my cycle statistics or estimates.
3. As a user with multiple devices, I want cycle edits and deletions to synchronize seamlessly via E2EE delta-sync with proper tombstone records.
4. As a TalkBack user, I want dialogs to provide clear accessibility descriptions for date editing and deletion confirmation.

## Implementation Decisions
- Add `updateCycle(cycleId: String, newStartDate: LocalDate)` and `deleteCycle(cycleId: String)` to `CycleRepository` and `CycleDao`.
- Update `LutealViewModel` to expose `editCycle(cycleId, newDate)` and `deleteCycle(cycleId)`.
- In `JournalScreen.kt`, add an overflow menu or edit icon on cycle headers in timeline mode and on cycle-start days in calendar mode to launch the edit/delete dialog.
- Localized strings in `strings.xml`, `values-fr/strings.xml`, and `values-en/strings.xml`.
