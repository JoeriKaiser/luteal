# Spec 05: Local Data Portability and Complete Local Wipe

## Problem Statement
A core pillar of Luteal is privacy, offline-first operation, and user data ownership. Users must never be locked in or dependent on cloud servers to back up their data. Users need a reliable way to export their complete tracking history (cycles, daily entries, symptom logs, user settings) into a versioned, human-readable JSON format, and to perform a GDPR-compliant complete local wipe that guarantees zero traces remain on the device.

## Solution
1. **Local JSON Backup & Export:**
   - Define a versioned JSON schema (`schema_version: 1`) containing all cycles, daily entries, symptom logs, and tracking contexts.
   - Use the Android Storage Access Framework (`ActivityResultContracts.CreateDocument("application/json")`) to allow saving directly to device storage, SD card, or user-selected cloud drives.
2. **Complete Local Wipe:**
   - Clear Room Database tables completely (`cycles`, `daily_entries`, `symptom_logs`, `sync_tombstones`).
   - Clear DataStore preferences (`user_preferences`, `sync_preferences`).
   - Clear Keystore credentials (account codes, device tokens).
   - Reset UI state cleanly to first-launch onboarding.
3. **Settings Integration:**
   - Add "Sauvegarde et exportation des données" and "Effacer toutes les données locales" sections in `SettingsScreen.kt`.

## User Stories
1. As a privacy-conscious user, I want to export my complete menstrual history to a JSON file, so that I have an independent offline backup of my health data.
2. As a user switching devices without a sync server, I want to be able to save my data file to move it manually.
3. As a user wishing to leave the app or wipe testing data, I want to completely erase all local data and credentials with a single explicit action.
4. As a TalkBack user, I want export progress and wipe confirmations to be clearly announced.
