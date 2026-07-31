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

## Screenshots are still missing

`images/phoneScreenshots/` is empty in both locales. F-Droid asks for at least
two, and a listing without them looks abandoned. Capture them from a real
device or emulator running the release build:

```sh
./gradlew installRelease
adb shell screencap -p /sdcard/1.png
adb pull /sdcard/1.png fastlane/metadata/android/en-US/images/phoneScreenshots/1.png
```

Worth showing, in this order: the Today screen with a cycle under way and an
estimate range visible; the daily observation editor; the journal with a few
days filled in; the Duo sharing toggles.

Use demo data rather than real tracking history - Settings has a generator in
debug builds. Screenshots are published, and a screenshot of your own cycle is
not something to hand to a public repository.

Take the French set with the device language set to French, and the English
set with it set to English.

## Regenerating the icon

`images/icon.png` is rendered from the adaptive icon's vector sources
(`app/src/main/res/drawable/ic_launcher_{background,foreground}.xml`), cropped
to the 72x72dp safe zone so it frames the way a launcher shows it. Re-render it
if the icon ever changes.
