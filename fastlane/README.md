# F-Droid listing metadata

F-Droid builds its app listing from this tree. It is read straight from the
repository at the tagged release commit, so a change here only reaches the
store on the next tagged version.

```
fastlane/metadata/android/<locale>/
    title.txt                     app name
    short_description.txt         under 80 characters, no trailing period
    full_description.txt          the listing body
    images/icon.png               512x512
    images/phoneScreenshots/*.png at least two, ordered by filename
    changelogs/<versionCode>.txt  under 500 characters
```

`en-US` and `fr-FR` are both present. French is the app's own default
language; English is carried so the listing is readable on the default
F-Droid index.

## Screenshots

`images/phoneScreenshots/` contains phone screenshots (`1_today.png` through
`4_duo.png`) for both `en-US` and `fr-FR`, captured in portrait (1080x2410).

To refresh them from a real device or emulator running the release build:

```sh
./gradlew installRelease
adb shell screencap -p /sdcard/1_today.png
adb pull /sdcard/1_today.png fastlane/metadata/android/en-US/images/phoneScreenshots/1_today.png
```

The current set shows:
1. The Today screen with a cycle under way and an estimate range visible (`1_today.png`)
2. The daily observation editor (`2_observations.png`)
3. The journal month calendar grid (`3_journal.png`)
4. The Duo sharing toggles (`4_duo.png`)

Always use demo data rather than real tracking history when capturing screenshots.
Take the French set with the device language set to French, and the English set
with it set to English.

## Regenerating the icon

`images/icon.png` is rendered from the adaptive icon's vector sources
(`app/src/main/res/drawable/ic_launcher_{background,foreground}.xml`), cropped
to the 72x72dp safe zone so it frames the way a launcher shows it. Re-render it
if the icon ever changes.
