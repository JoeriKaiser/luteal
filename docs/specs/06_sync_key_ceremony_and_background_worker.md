# Spec 06: First-Run Sync Key Ceremony and Periodic Background Worker

## Problem Statement
1. **Account Key Ceremony:** Under Luteal's E2EE architecture, the 100-bit account code is the master encryption key derived with HKDF. If a user creates a sync account, loses their device, and didn't write down their code, their data is mathematically unrecoverable. Currently, the code is generated behind the scenes and only viewable retroactively in Settings. An explicit first-run "Write this down" ceremony is essential upon account creation.
2. **Periodic Background Sync:** Currently, sync only triggers on explicit user actions (e.g. tapping "Synchroniser", opening app, switching views). For multi-device pairings or Duo links to stay up to date without manual intervention, a background `WorkManager` periodic worker with network constraints (unmetered/connected) must sync changes automatically.

## Solution
1. **First-Run Account Key Ceremony:**
   - When a user registers a new sync account in `SettingsScreen` or onboarding, display a dedicated modal dialog presenting the high-entropy account code.
   - Provide "Copier le code" action.
   - Require an explicit confirmation checkbox ("J'ai noté ou sauvegardé mon code de compte") before dismissing the dialog.
2. **Periodic Sync via WorkManager:**
   - Define `PeriodicSyncWorker` using AndroidX `CoroutineWorker`.
   - Configure a `PeriodicWorkRequestBuilder` (e.g. 1-hour interval, `NetworkType.CONNECTED`, battery not low).
   - Enqueue unique periodic work `PeriodicSyncWorker.enqueue(context)` whenever `SyncMode.ONLINE_CLOUD` is enabled, and cancel work when disabled.
3. **Verification & Testing:**
   - Tests validating worker constraint configurations, enrollment lifecycle, and key ceremony dialog confirmations.
