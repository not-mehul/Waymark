# Releasing Waymark

Waymark ships as an APK you sideload — there is no Play listing, and the app
asks for no network permission, so there is nothing for a store review to
check. Publishing is a tag push.

---

## Once, before the first release: make a signing key

An Android APK must be signed to install, and every later version must be
signed by **the same key** or the phone will refuse the upgrade. Losing this
key means every install has to be uninstalled and reinstalled from scratch, so
put it somewhere you will still have it in five years.

```bash
keytool -genkeypair -v \
  -keystore waymark.jks \
  -alias waymark \
  -keyalg RSA -keysize 4096 -validity 10000
```

It asks for a keystore password, your name and organisation (any of it can be
left blank), and a key password. Keep both passwords.

`*.jks`, `*.keystore` and `keystore.properties` are all in `.gitignore`. Do not
take them out.

---

## Building on your own machine

Put a `keystore.properties` in the repository root — beside `settings.gradle.kts`,
never committed:

```properties
storeFile=waymark.jks
storePassword=…
keyAlias=waymark
keyPassword=…
```

`storeFile` is resolved relative to the repository root, so an absolute path
works too. Then:

```bash
./gradlew testDebugUnitTest      # the unit suite
./gradlew assembleRelease        # app/build/outputs/apk/release/app-release.apk
```

Without a `keystore.properties` the release build still runs and prints a
warning, but produces an **unsigned** APK that no phone will install. That is
useful for looking at a build, and useless for shipping one.

### "keystore password was incorrect"

Nine times out of ten the keystore is fine and the properties file is not.
`Properties.load` is not a shell:

- **A trailing space survives.** `storePassword=hunter2·` is a different
  password from `hunter2`, and nothing on screen shows the difference. Values
  are trimmed for you now, but check the file if this recurs.
- **Quotes become part of the value.** `storePassword="hunter2"` used to mean a
  password with quotes in it. Also handled now — write it bare anyway.
- **A backslash is an escape.** `pa\ss` reads as `pass`. Double it (`pa\\ss`)
  or pass the password through the environment instead. This one cannot be
  fixed for you without breaking passwords that legitimately contain `\`.
- **`storePassword` and `keyPassword` are two different things.** `keytool`
  asks for both; they are often but not always the same.

To check the key itself, independent of Gradle:

```bash
keytool -list -v -keystore waymark.jks -alias waymark
```

If that prints the certificate, the keystore and the store password are right
and the problem is in `keystore.properties`. `./gradlew signingReport` shows
what the build actually resolved.

---

## Building in CI (the usual path)

Add four repository secrets under **Settings → Secrets and variables →
Actions**:

| Secret | What goes in it |
| --- | --- |
| `KEYSTORE_BASE64` | `base64 -w0 waymark.jks` — the whole keystore, one line |
| `KEYSTORE_PASSWORD` | the keystore password |
| `KEY_ALIAS` | `waymark`, or whatever `-alias` you used |
| `KEY_PASSWORD` | the key password |

Then tag and push:

```bash
git tag v1.0.0
git push origin v1.0.0
```

`.github/workflows/release.yml` runs the unit suite, builds the release APK,
signs it, uploads it as a build artifact, and opens a **draft** GitHub release
with the APK attached. Write the notes and press publish.

If the secrets are missing, the workflow still finishes — it warns, and names
the file `waymark-<version>-unsigned.apk` so an unsigned build cannot be
mistaken for a shippable one.

---

## Version numbers

Both live in `app/build.gradle.kts`:

- `versionName` is what a person reads — it appears in the About sheet and at
  the foot of the trip shelf. Keep it matching the tag.
- `versionCode` is what Android compares. It must **increase** with every
  release or the upgrade will be refused.

For `v1.1.0` that means `versionCode = 2`, `versionName = "1.1.0"`.

---

## What to check before tagging

The unit suite covers the parts of the app that can be tested without a device
— all the date arithmetic, the timeline builder, the country table, the label
placer, the markdown export, the airport and coastline assets. It does not
cover layout. Before a release, on a real phone:

- **A first launch is empty.** The shelf offers "New itinerary" and, quieter,
  "Load the worked example". Nothing is written until you choose.
- **The worked example loads** and its timeline, map and numbers all have
  something in them.
- **Adding a flight** resolves both airport codes, computes block time, and
  asks for notification permission once when you save. Then open the booking
  and check its record locator and ticket numbers read back.
- **Editing a flight** offers aircraft, cabin, terminals, seats and ticket
  numbers, and keeps them after Save.
- **Destination notes** name the place the trip actually spends its days, not
  the last airport it touched.
- **Both themes**, checked on one screen with a chart on it.
- **Delete a trip**, and confirm nothing of it is left on the shelf.
- **An upgrade over an older install** still opens: database version 3 drops
  the vault and packing tables and keeps the itinerary.
