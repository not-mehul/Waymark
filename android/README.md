# Waymark — Android shell (Capacitor)

The Android app the Svelte rewrite ships as. It is a [Capacitor 8](https://capacitorjs.com)
project set up the same way as BookShelf's and Ledger's: Capacitor's
`BridgeActivity` hosts the web build in a `WebView`, and everything that has to
run with the app closed lives in one plugin,
[`WaymarkPlugin.kt`](app/src/main/java/com/waymark/WaymarkPlugin.kt), whose web
side is `src/platform/native.ts` and `src/state/shell.svelte.ts`.

It has the Kotlin app's id (`com.waymark`) and, for a release, its key and its
version numbering, so it installs over the Kotlin app as an update. Until the
switch-over (phase 6 of the rewrite) the Kotlin app in `app/`, `core/` and
`feature/` stays the one that ships.

## What the shell does

| | Where |
|---|---|
| Moves the Kotlin app's trips across on the first launch: hands the page the backup the Kotlin app kept (`HandoverSnapshot.kt`), and removes it once the page has read every trip back | `Handover.kt` |
| Posts reminders on time and draws the journey under way (a Live Update on Android 16+). The page works both out and sends them whenever a trip or a reminder setting changes; the shell keeps one alarm for the next, with a 15-minute WorkManager check underneath | `alerts/` |
| The Next up widget, from the next dozen bookings the page sends | `widget/` |
| Reads a boarding pass or a trip code from a picture, with ZXing | `share/Barcodes.kt` |
| Shares files out through the share sheet, and prints the page's itinerary through Android's print dialog (which also saves a PDF) | `share/Exports.kt` |
| Brings in an email, a trip file, a backup or a trip code's link shared from another app, and a tapped reminder or widget | `WaymarkPlugin.kt` |

The receiver, worker, widget and notification channel names are the Kotlin
app's, so an alarm it set, the check it enqueued, a widget already on the home
screen and the traveler's notification settings all carry over the upgrade.

## Open & run

From the repository root, build the web app and copy it into this project:

```bash
npm ci
npm run sync            # vite build, then cap sync android
```

Then open this folder (`android/`) in Android Studio, or from the command line
(JDK 21):

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest # JVM unit tests
./gradlew :app:lintDebug         # Android Lint, of the app module
./gradlew assembleRelease        # R8-minified release APK
```

`./gradlew :app:assemblePreview` builds **Waymark preview**: the release build
under its own id (`com.waymark.preview`), signed with the debug key, so it
installs beside the Kotlin app on a phone rather than over it. It starts empty;
restore a backup into it to compare the two. CI uploads one with every run (the
`waymark-preview` artifact); each run's debug key is new, so uninstall the
previous preview before installing the next.

A release is signed with the Kotlin app's key, read from the same places:
`keystore.properties` at the repository root, or the `WAYMARK_KEYSTORE…`
environment variables (see `RELEASING.md`). Without them the release APK is
unsigned.

> **App crashes on launch with `ClassNotFoundException: …MainActivity`?** The
> APK was built without the app's Kotlin code, which happens when
> `gradle.properties` sets `android.builtInKotlin=false` (Android Studio's AGP
> Upgrade Assistant adds it). The build stops with an explanation instead;
> remove the line and don't run the Upgrade Assistant's "built-in Kotlin" step.
