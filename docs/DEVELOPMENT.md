# Developing Waymark

How Waymark is built, designed and tested. For what the app does, see the [README](../README.md); for cutting a release, see [RELEASING.md](../RELEASING.md).

## Design: "Editorial Dusk & Dawn"

The style reference is shared with BookShelf, Ledger, Colophon and Rosetta as
one package, [`dusk-dawn`](https://github.com/not-mehul/dusk-dawn), installed
at a tag. It holds the tokens, Fraunces, the shared component styles and
components, and `style.md`. `npm run check` runs its contrast check
(`dusk-dawn check`) and its literal-colour check (`dusk-dawn colours`). To
change the design, change the package and move its tag in `package.json`
(`style.md` §18).

Every colour in the app is a **semantic** CSS custom property (`--text-heading`,
`--panel-bg`, `--accent-amber`), defined twice, once for Dusk and once for Dawn.
No component names a colour. A token added to one theme only is a bug, exactly
as a raw hex outside the token blocks would be.

| Style reference | Implementation |
| --- | --- |
| `:root` / `[data-theme="light"]` token blocks | `dusk-dawn/tokens.css`, generated in the package from its `tokens.json` |
| Shared components | `dusk-dawn/components.css` and `TabRail`, `ThemeToggle`, `Toast` from `dusk-dawn` |
| `var(--token)` in every rule | The same, in `src/styles/app.css` and the components |
| RGB triples for alpha | `rgba(var(--cream-rgb), 0.03)`, and the same for the shadow and danger colours |
| Three-stop fixed gradient + three soft glows | The page background and its glow layers in `dusk-dawn/components.css` |
| Serif display / system sans / system mono | Fraunces (bundled, nothing downloaded) / `system-ui` / `ui-monospace` |
| All-caps tracked mono labels | `SectionHeader`, and the field labels in `Field` |
| 2px corners everywhere | `--radius: 2px`; no pills |
| Modal: amber hairline, corner brackets, fade + settle | `Modal` |
| Theme switch with sliding amber knob, `role="switch"` | `ThemeToggle`, with System / Dusk / Dawn in both menus (`ThemeSetting`) |
| Undo instead of asking, one toast at a time | `Toast`, through `ui.notify` |
| Segmented control for a real choice, quiet rail for navigation | `OptionChips` vs `TabRail` |
| Motion is responsiveness, not decoration | The `--dur-*` and `--ease-*` tokens, and nothing outside them |

### The mark

A surveyor's north needle: two long triangles meeting on a vertical axis, the
right half amber and the left half cream, with the axis running the full height
of the glyph. It is the adaptive launcher foreground, two filled halves sized to
the 66dp safe zone, with a monochrome layer that cuts the needle out of its own
field so a themed-icon tint does not flatten it into a lozenge.

It replaced a ring-and-chevron mark that was, on inspection, broken: the ring
was centred at the top of the canvas and the chevron sat below it, so the two
halves of the logo never met.

### Motion

Every transition takes its duration and easing from the tokens: content
settles in, list rows stagger down the column once, and a value animates to
its target rather than jumping. Nothing bounces, nothing spins, nothing
animates that the reader is waiting for. With reduced motion asked for in the
system's accessibility settings, every one of them is cut to the final state.

### Dates and times

Both pickers are drawn in the app's own idiom rather than taken from Material,
which arrives with its own palette, shapes and ideas about elevation. The date
picker is a Monday-first month grid; the time picker is the **whole clock laid
out and tapped** — twenty-four hours over twelve minutes, six to a line, every
value one tap away.

The time picker got there by subtraction. It began as two dragged wheels with
stepper arrows, which turned 16:20 into a drag you had to watch. Then it gained
a row of shortcut chips at three-hour intervals — 06:00, 09:00, 12:00 — which
is a guess about when people travel dressed up as a convenience, and no help at
all for the times nobody publishes on the hour. Minutes are laid out in fives,
because departures are mostly published in fives, with a row of +1 to +4 beneath
for the ones that are not — 08:31, 19:04 — and an existing time is never
rounded to fit. The running time is the modal's own title, so the body is
nothing but choices. Both pickers open in their own modal and change nothing
until Choose.

Fields that open a picker are shaped *exactly* like fields that accept typing —
same corner, same border, same 11dp of vertical padding — so a date sitting
beside a flight number is the same height as it.

Both themes share one type ramp; only colour changes. First launch follows the
system setting; the toggle overrides it, and System in the menu hands it back.
An inline script in `index.html` puts the page on the saved side before the
first paint.

### Charts: one hue, by measurement

The palette's two accents are deliberately close — that closeness is what makes
the theme calm — and that rules them out as a categorical pair. Measured with a
colour-vision validator against the Dusk surface, amber against sage separates
by **ΔE 5.8 under protanopia and 8.6 to normal vision**, both under the floor
at which two marks can be told apart.

So no chart in Waymark encodes identity with hue. Length carries magnitude,
position and a written label carry identity, and colour does one job: emphasis.
Where a mark genuinely has two parts, they are two shades of a single hue
(ΔE 35 in Dusk, 21 in Dawn) and both parts are labelled. Every bar prints its
own value; legend text wears a text token, never the series colour.



## Architecture

One page, built by Vite into a single `index.html` with the catalog files
beside it, which `cap sync` copies into the Android project:

```
src/
├── domain      Plain TypeScript, no DOM: the model and the logic (timeline,
│               connection risk, party splits, reminders, the journey under
│               way, cost splits, weather plan, analytics, exports, geometry)
├── catalog     Stations, places, airlines, country facts, coastline, the
│               worked example
├── db          The Dexie (IndexedDB) schema and the repositories
├── share       Trip files, backups, trip codes, emails, boarding passes, and
│               the handover from the Kotlin app
├── online      The only network code: weather and exchange rates, opt-in
├── state       Svelte rune modules: the open trip, settings, the screen and
│               modal in view, and what is sent to the Android shell
├── platform    The bridge to the Waymark plugin (callNative, onNativeEvent)
├── components  The screens and the component kit
└── styles      The app's stylesheet (tokens and shared styles come from dusk-dawn)
data/catalog    airports.txt, fetched once at start (places.txt comes from dusk-dawn)
android/        The Capacitor project and the Waymark plugin (android/README.md)
```

**State is Svelte runes.** Each concern is a module of `$state` and
`$derived` values with the functions that change them: the trip that is open,
the settings, which screen and which modal are showing. Lists read IndexedDB
through Dexie's `liveQuery`, so a change made anywhere (an import, a reminder
tapped, another screen) shows everywhere without a refresh. Back is the
system's back gesture, which the shell hands to the page only while it has a
modal to close or a screen to leave, so the predictive back-to-home animation
still works.

**The domain layer is plain TypeScript.** No DOM, no Svelte, no Dexie, which
is why the interesting logic is unit-tested in Node and most of the tests
below run in milliseconds. It was ported from the Kotlin app's domain module
together with its tests, which became the specification.

**Persistence.** One IndexedDB database. Bookings live in one store with a
`kind` discriminator, as they did in the Kotlin app's single table. Backups and
trip files keep the `waymark` archive format the Kotlin app wrote, so every
file it made still opens, including ones from people on a trip who have not
updated yet.

**What runs with the app closed is Kotlin.** A page cannot keep time while the
app is closed, so the page works out every reminder still worth posting, the
journeys to draw and the next dozen bookings, and sends them to the Waymark
plugin whenever a trip or a setting changes. The plugin keeps one alarm for
the next thing due, with a 15-minute WorkManager check underneath, posts the
notifications, draws the Live Update and the widget, and reads barcodes with
ZXing. Its receiver, worker, widget and channel names are the Kotlin app's, so
alarms, placed widgets and notification settings carried over the upgrade.

**Moving from the Kotlin app.** The last Kotlin versions kept a full backup in
the app's private storage whenever a trip changed. On its first launch the new
app reads that file through the plugin, imports it, checks every trip reads
back whole, and only then deletes it. If the old app's database is there but
the file is not, it says so and points to restoring a backup instead of
opening on an empty shelf.

**Nothing is encrypted, deliberately.** There was an AES-256-GCM vault under an
Android Keystore key, with a `BiometricPrompt` in front of the moment a code
became readable. It is gone. The threat it defended against was somebody
holding the unlocked phone — which is the same person the app is for — and the
cost was that a confirmation code could not be read at a desk without a free
hand for a fingerprint. Nothing here is sent anywhere, so what protects a code
is the device's own lock screen, as it does for everything else on it.

**Flight data comes from the traveler, and only from the traveler.** There is
no provider interface and no status subsystem. The one network client in the
app, `src/online`, fetches the weather and the day's exchange rates, and only
after the traveler turns on online extras; it sends a place to about a
kilometre and the dates, or a request for today's rates, and never a booking,
a name or a trip. A booking holds the times it was given, and changing them means
editing the booking — which reaches every field, not only the times.

**A connection is something one traveler makes.** The timeline walks each
traveler's own itinerary separately and merges the results. Where the party
agrees on what comes before a booking, that link carries its verdict; where
they disagree — the definition of a split — no link is drawn, because there is
no single connection to judge. Walking one chronological line over the whole
party is how the app used to pair one person's arrival with another person's
departure and announce an impossible connection on a correct itinerary.

**Deleting.** Removing a booking releases any idea that was promoted onto it,
so the idea returns to the list rather than vanishing with the segment.
Removing a traveler takes the bookings only they were on and their name off
the rest, and removes their profile if they are on no other trip. Removing a whole trip removes everything under it in one transaction, with the
reminders raised for it. A sweep on start-up repairs anything a previous
version orphaned. Trip deletion asks for the trip's name to be typed: it is the only
irreversible thing the app can do, and the only friction of its kind.

**The station directory.** `data/catalog/airports.txt` holds 8,831 IATA stations —
the supplied table, with the 259 closed fields dropped and 26 duplicate codes
resolved in favour of the larger, better-described row. **Time zones were
resolved from each station's coordinates when the file was generated**, against
real boundary data, so the app carries an exact IANA zone per airport and
applies no heuristic of its own; a wrong zone moves a flight to the wrong day,
not merely the wrong hour. `parseAirportDirectory` is a plain function over the
file's lines, so the whole file is checked by the unit tests. The hand-curated core
list survives alongside it and wins any collision, because it carries terminal
designators and the names travelers actually use ("Heathrow", not "London
Heathrow Airport").

The supplied table had lost its accented letters to an encoding fault, so
Montréal shipped as "Montral". `tools/repair_airport_names.py` restored them
for 720 stations from OurAirports. It changes a name only where the file's text
is exactly OurAirports' spelling with the non-ASCII letters taken out, so it
adds letters back and touches nothing else. Search folds accents, so "sao paulo"
still finds São Paulo.

**The basemap.** `src/catalog/coastline-data.ts` holds Natural Earth's public-domain 1:110m land
polygons, reduced by Ramer–Douglas–Peucker to 1,403 vertices across 50 rings —
roughly one point per fifty kilometres of coast. Rings that crossed the
antimeridian were clipped into separate closed pieces at generation time, which
is what stops a filled landmass from becoming a stripe across the chart at the
seam. It parses lazily, so a trip that never opens the map never pays for it.



## Building

The page needs Node 24 (`.nvmrc`); nothing else.

```bash
npm ci
npm run dev        # the app in a browser, at http://localhost:5173
npm run check      # tokens and contrast, formatting, lint, types, tests, build
npm test           # the unit suite alone
```

In a browser the app is the whole app except what Android does for it:
reminders, the widget, reading a barcode from a picture where the browser has
no `BarcodeDetector`, and shares from other apps. Data lives in the browser's
IndexedDB, so the browser build is also where to try a backup safely.

**The Android app** needs a JDK 21 and an Android SDK with API 37, found through
`android/local.properties` or `ANDROID_HOME`:

```bash
npm run sync                       # build the page and copy it into android/
cd android
./gradlew assembleDebug            # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest   # the plugin's unit tests
./gradlew :app:lintDebug           # Android lint
./gradlew assembleRelease          # the R8-minified release APK
```

or open `android/` in Android Studio after `npm run sync`.
[android/README.md](../android/README.md) describes the shell and the plugin.

The plugin's Kotlin style is checked by ktlint and detekt, run in CI as their
command-line jars against `.editorconfig` and `config/detekt.yml`; the same two
commands work locally with the jars from Maven Central (versions in
`device-checks.yml`).

**In CI.** Every push runs `npm run check`; pull requests and `main` also
build the Android shell and run its unit tests. The slower checks below are
the **Device checks** workflow, started by hand from the Actions tab before a
release that touches the shell.

**On an emulator, in Device checks.** It installs the last Kotlin version with
the worked example in it, installs the new app over it as a phone would get
the update, and checks the trip, its twelve bookings and two travelers came
across and the handover file is gone. Then it times the cold start of both,
ten launches each. On 1 November 2026 (API 34 emulator, median of 10, empty
shelf) the Kotlin app drew its shelf in 513 ms and the new app in 429 ms.

### Releasing

Waymark ships as a sideloaded APK; there is no store listing. Publishing is a
tag push:

```bash
git tag v2.0.0 && git push origin v2.0.0
```

The version comes from the tag: `v2.0.0` builds version name `2.0.0` and code
`20000`, for the APK and the page alike, so there is no number in the source
to remember to bump. `.github/workflows/release.yml` runs the checks, builds
the release APK, signs it with the keystore held in the repository's secrets
(the same key the Kotlin versions were signed with, which is what lets it
install as an update) and opens a draft GitHub release with the APK attached.
**[RELEASING.md](../RELEASING.md)** covers the signing key, the four secrets,
building signed on your own machine, and what to check on a real phone before
tagging.

Release builds run R8 with resource shrinking. Without a keystore the build
still completes and says so, and the artifact is named `-unsigned` so it cannot
be mistaken for a shippable one.



## Tests

317 unit tests in Vitest, run in Node with the time zone pinned to UTC and
IndexedDB faked in memory. The domain, catalog and share suites were ported
one for one from the Kotlin app's, which is how the TypeScript port was held
to the same behaviour. The Waymark plugin's own logic (the schedule it keeps,
the widget's choice of booking, the time spans it writes) has JVM tests under
`android/app/src/test`, and the Device checks workflow checks the upgrade
from the Kotlin app on an emulator.

- `cost-splitter` — an even split, the odd cent, a split among some, no
  more settling payments than people less one, a foreign expense converted
  once, an expense with no rate listed rather than counted, a payer who has
  left the party still owed, and amounts read the way people type them
- `weather-plan`, `weather`, `rates` — where the trip is
  on each day still to come, forecast within two weeks and last year beyond,
  both services' replies read and refused, and **nothing fetched at all while
  online extras are off**
- `itinerary-document` — every booking once under its day, every reference,
  the day's weather, and who pays whom
- `live-journey` — the journey under way: nothing long before departure, a
  countdown from three hours out, a bar that fills to landing, gone on arrival,
  quiet when flight reminders are off, and woken exactly when it next changes
- `trip-repository` — an import reads back whole and replaces rather than
  duplicates, everyone ticked is stored as everyone, deleting a trip removes
  everything on it, removing a traveler
  takes their own bookings and keeps the shared ones, a profile on another trip
  survives, an archived trip stays quiet, the widget reads the next few
  bookings soonest first
- `rows` — every kind of booking in the worked example round-trips, an
  unknown kind is skipped rather than a crash, a place without coordinates
  stays without them
- `trip-archive` — trip files and QR payloads round-trip, a code fits by
  leaving out what it says it left out, a damaged or newer file is refused
- `parsing` — flights read out of a confirmation email (dates either side
  of the flight number, 12-hour times, a European date not mistaken for a
  time, "Room 215" not mistaken for a flight) and out of an IATA boarding-pass
  barcode, one leg or several
- `segment-form` — editing a booking: a new airport moves it into that
  airport's zone, an end a little before the start runs past midnight, seats
  are kept only for the people on the flight
- `leg-times`, `lodging-nights` — an arrival's day inferred from two
  clocks, and nights counted as calendar nights in the hotel's zone
- `places`, `whereabouts` — typed places resolved exactly, nearest
  first, never by substring; where the trip is at a given moment
- `ics-export` — one event per booking, UTC times, escaping and folding
- `airlines` — the carrier table's codes well formed and unique
- `flight-designator` — parsing `BA286`, `ba 286`, `BAW286`, `3U8888`, `U2 1234`
- `geo` — great-circle distances against published figures, arc symmetry,
  Mercator round-trip, antimeridian detection
- `connection-risk` — minimum connection times, terminal changes,
  immigration, inter-carrier bag re-check, a delay eating a connection
- `timeline-builder` — ordering, one entry per segment (two for lodging),
  day breaks, traveler filtering, the now marker, and **two travelers on
  parallel flights not being read as one broken connection**
- `party-split-analyzer` — the split window and per-traveler coverage
- `reminder-planner` — that every category can raise one rather than only
  flights, that a category switched off raises nothing, the window boundaries at
  both ends, and **that the signature is stable as the clock closes in** so a
  booking is announced once rather than at every check — but changes when the
  booking moves, because the time did
- `transit-and-time` — estimates, mode selection, duration and zone-shift text
- `idea-board` — section ordering, per-traveler filtering, and promotion to
  a timeline segment
- `countries` — every row well formed and unique, and the one that decides
  whether the feature works at all: **every country in the bundled station
  directory has notes**, so there is no airport you can enter and get a blank
  screen for
- `time-at-place` — that the worked example resolves to London then Paris,
  that a two-hour connection is not a destination, and that a trip with one
  movement has nowhere to have stayed
- `trip-analytics` — distance splits that add up, day loads that cannot
  exceed a day, carbon against the published factors
- `globe-projection` — orthographic projection inside the unit disc, the
  horizon as the culling boundary, and a centroid that survives the antimeridian
- `duration-text` — how a span reads at every scale: hours and minutes under
  a day, then days, then weeks and days, then months, weeks and days; zero terms
  dropped; and a property that the leading unit only ever coarsens as the span
  grows
- `airport-directory` — the generated format, interned zones, a station with
  no municipality falling back to its own name, and a malformed line being
  skipped rather than taking the directory down with it
- `airport-asset` — the shipped file itself: every zone one the platform
  knows, every coordinate in range, no duplicate codes, the stations a traveler
  is most likely to type, the half-hour and three-quarter-hour zones that a
  lazy country-to-zone table gets wrong, and **a check that each station's UTC
  offset agrees with its longitude**, which is what catches a transposed
  latitude and longitude
- `markdown-export` — sections, chronology, checkbox states, a filename safe
  on any filesystem, and that **every booking reference in the fixture appears
  in the exported document**
- `label-placer` — no two labels overlapping, no label covering a foreign
  mark, everything inside the viewport, priority winning a contested slot, and
  forty marks in one cluster yielding some labels rather than all or none
- `coastline` — closed rings, real coordinates, no seam crossing from
  interior longitudes, and a point-in-polygon check that Paris is on land and
  the mid-Pacific is not
- `handover` — moving the Kotlin app's trips: no file means nothing to do, a
  file that cannot be read is kept for the next launch, and running it twice
  changes nothing the second time
- `files` — a backup's trips found, a file too large to be a trip turned away
  unread, and a trip code's link opened as well as its bare payload
- `shell-data` — what the page sends the Android shell: every reminder still
  to come, soonest first and worded as at the moment it falls due, nothing
  with reminders off, a journey window per flight, and each widget booking
  named with its trip, day, clock and place
- `add-flight` — an evening departure that lands the next morning, a chosen
  part of the party, and a boarding pass filling in everything it carries but
  the clock times




### In a browser

`npm run test:e2e` builds the page and runs Playwright in Chromium at phone
size, with the clock pinned and the time zone set to UTC.

- `a11y.spec.ts` — the shelf, a trip and its menu, a booking, the
  before-you-go list and finding a booking across trips each pass axe, and
  hold at 200% text without scrolling sideways
- `flows.spec.ts` — a new trip set from the app's own calendar (nothing
  changes until Choose, the last day follows the first and can't go before
  it, the trip is still there after a reload); a stay added one question at a
  time, with Escape closing the clock but not the sheet, landing under its
  check-in and check-out days with its reference; and an expense split
  between two, with the payment that squares it before and after editing
  who it was for
