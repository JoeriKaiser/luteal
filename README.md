# Luteal

A menstrual cycle tracker for Android, built around one rule: what you recorded
and what the app calculated are never presented as the same thing.

Recorded observations are labelled as recorded. Predictions are labelled as
estimates, shown as a range rather than a confident date, and the app says what
the range was derived from. Luteal makes no diagnosis.

The app is French-first; English is also shipped. It works entirely offline by
default, with optional end-to-end encrypted sync.

## Privacy

- Tracking data lives in a local Room database. Nothing leaves the device until
  sync is explicitly enabled - `SyncMode` defaults to `OFFLINE_LOCAL`.
- Android cloud backup is disabled for the app's data (`allowBackup=false`).
- Home-screen widgets start concealed and never show private notes or detailed
  observations. Revealing a widget sends its visible content to the launcher,
  which may retain previews or screenshots outside Luteal's control.
- No advertising, no analytics, no Google Play Services, no Firebase.
- Crash reporting is ACRA with a consent dialog: nothing is sent unless the user
  reads it and chooses to send. Tracking data is not included.
- When sync is on, records are encrypted on-device before upload; the server
  stores only ciphertext. See [docs/architecture/E2EE_DESIGN.md](docs/architecture/E2EE_DESIGN.md).

## Building

Requires JDK 21 and the Android SDK (compileSdk 35, minSdk 26).

```sh
./gradlew assembleDebug          # debug build
./gradlew testDebugUnitTest      # unit tests
./gradlew assembleRelease        # release build
```

A plain clone builds with no other checkout present: the folicular API contract
is vendored under [`contract/`](contract/README.md).

Release signing reads `keystore.properties` at the repository root - copy
`keystore.properties.example` and fill it in. Without that file the release
build is left unsigned rather than falling back to the debug key.

## Layout

| Path | Contents |
| --- | --- |
| `app/src/main/java/fr/luteal/app` | Screens, navigation, view models, DI wiring |
| `app/src/main/java/fr/luteal/core` | Domain model, Room storage, sync, crypto |
| `contract/` | Vendored folicular OpenAPI spec and conformance fixtures |
| `docs/architecture` | Sync boundary, E2EE design, backend integration |
| `docs/product` | Feature research, roadmap, and widget behavior |
| `docs/research` | Cycle research notes and source register |
| `fastlane/` | F-Droid listing metadata |

## Backend

Sync talks to [folicular](https://github.com/JoeriKaiser/folicular), a Go
service. It is optional: the app is fully usable without it. The server address
is set by `BuildConfig.SYNC_BASE_URL`, and account registration is currently
invitation-only.

## Licence

GNU General Public License v3.0 or later. See [LICENSE](LICENSE).

Luteal is free software: you can redistribute it and/or modify it under the
terms of the GNU General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later
version. It is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See the GNU General Public License for more details.
