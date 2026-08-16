# Waymark

An Android travel-logistics app: every flight, stay, transfer and booking for a
trip — for one traveler or a party that splits up — on one timeline, one vector
chart, and one encrypted vault. It works with the radio off.

Kotlin, Jetpack Compose, Room, WorkManager. Single module, no third-party
runtime dependencies beyond AndroidX.

---

## What it does

**Enter a flight.** A designator, a date, two airport codes, two times off a
picker. Waymark calls no schedule service and no tracking API — it asks for no
network permission at all — so what it contributes is only what an airport code
already implies: coordinates and a time zone, from a bundled directory of
**8,831 IATA stations**. That is enough to put the leg on the map, to work out
that a 16:20 out of San Francisco lands the following morning, and to show the
block time back as you set it, which is the quickest way to catch a time
entered against the wrong airport.

**Add things one question at a time.** A plan or an idea is entered through a
short flow rather than a form: what kind of thing, then what it is, then when,
then the reference numbers somebody has to go and find in an email. Any earlier
step can be tapped to go back and corrected, and closing the sheet at any point
saves nothing at all.

**And then leave it alone.** There is no live tracking and no "what the board
says" to keep current, because nobody is going to update an app at a gate. A
booking is a record of something already arranged, so it opens **read only**;
Edit turns the screen into a form with Save and Cancel, and nothing is written
until Save. The one thing the app volunteers is a reminder that a flight you
booked leaves in a few hours, which it knows from the itinerary and the clock.

**Keep the codes.** Confirmation codes, record locators, e-ticket numbers and
boarding-pass payloads are sealed with AES-256-GCM under a key held in the
Android Keystore. They are masked in the UI until the device authenticates the
reader.

**Carry the passes.** Boarding passes are stored locally with their IATA BCBP
payload, rendered on a screen that holds itself bright and awake.

**Travel as a group.** Travelers are profiles attached to a trip and assigned
per segment. When two people are on different bookings — one on the morning
train, one on the evening one — the app names the window in which the party is
split, and flags anyone with nothing booked.

**Read the trip.** The timeline is a single column of days with the time in the
left gutter and, between the bookings, the part that actually goes wrong: the
connection with a minimum-connection-time verdict, the transfer with a mode and
a duration estimate, the four hours that belong to nobody.

**See the geography.** One offline map, two projections. The flat chart reads a
region; the globe shows what a long haul actually is, with the far side of the
world culled rather than flattened. Both draw real coastlines — Natural Earth's
1:110m land polygons, simplified to about 1,400 points and shipped inside the
binary — over water, under a graticule, with the trip's great-circle legs on
top. Pinch to zoom either one, drag to pan the chart or turn the globe, tap a
mark to open it. Labels are placed by a collision solver rather than pinned
beside their marks, so a cluster of hotels in one city does not print on top of
itself; a label with nowhere to go is dropped and its mark stays.

**Keep a list.** Not everything on a trip has a time on it. The Ideas board
holds four kinds — **See, Eat, Do, Shop** — added a question at a time, with
no date attached —
saved, scheduled, or ticked off — readable three ways: by kind, by place, or by
the day it is pencilled in for. A day is a date without a clock time, which is
how planning actually happens; the board totals each day's estimates and says
so when a day is overfull. Each traveler can mark what they want, so a
party of four can see where their interests actually overlap. One tap promotes
an idea onto the timeline with a date and a duration, where it becomes an
ordinary booking; removing that booking puts it back on the list rather than
losing it.

**Keep the documents straight.** Passports, visas and insurance live in the
vault with their numbers sealed and their dates in the clear — because a
warning that needs a fingerprint before it can fire is a warning that arrives
at the airport. Waymark checks each one against the trip, including the rule a
plain expiry date hides: most borders want a passport valid six months beyond
arrival, so a passport that outlives the return flight by four months is still
a problem.

**Pack from the itinerary.** A first draft the app writes itself: counts scaled
to the nights, an adaptor only where the sockets differ from home, a swimsuit
only where something on the trip involves water, a power bank only on long
haul. Per traveler plus a shared list, with meters for who is ready.

**Count it.** A numbers screen: distance by mode, the shape of each day, where
the hours go, nights per city, and a carbon estimate that shows its factors
rather than asserting a figure, above the same globe you can turn with a finger.

**Take it with you.** One tap exports the whole trip as markdown — days as
headings, every booking as a line, the idea list and the packing list as
checkboxes, the documents and bookings as an index — and hands it to the system
share sheet. **No secret is ever exported.** Confirmation codes, e-ticket
numbers and passport numbers stay sealed on the device; what leaves is the
shape of the record, so a reader can see that the hotel is booked and under
whose name without the code travelling with it.

**Land informed.** Bundled destination notes — currency, plug, emergency
number, airport transfer, transit, tipping, seasons, neighbourhoods — and a
bundled guide per city: what to see, where to eat, what to order. Suggestions
appear on the Ideas board for the cities this trip actually visits, and
anything already on the list stops being suggested.

---

## Design: "Editorial Dusk & Dawn"

The app implements the supplied style reference as a Compose token system.

Every colour in the app comes from `WaymarkColors` — one data class of
**semantic** tokens (`textHeading`, `panel`, `accentAmber`), defined twice, once
for Dusk and once for Dawn. No composable names a colour. A token added to one
theme only is a bug, exactly as a raw hex outside the `:root` block would be in
the original CSS.

| Style reference | Implementation |
| --- | --- |
| `:root` / `[data-theme="light"]` token blocks | `DuskColors` / `DawnColors` in `ui/theme/Tokens.kt` |
| `var(--token)` in every rule | `Waymark.colors.*` via `CompositionLocal` |
| RGB triples for composable alpha | `colors.amber(0.25f)`, `colors.sage(…)`, `colors.cream(…)` |
| Three-stop fixed gradient + three blurred glows | `WaymarkBackdrop` (`ui/components/Atmosphere.kt`) |
| Serif display / system sans / system mono | `FontFamily.Serif` / `SansSerif` / `Monospace` — no web fonts, nothing downloaded |
| All-caps tracked mono labels | `SectionLabel` (11sp / 0.25em), `FieldLabel` (10sp / 0.2em) |
| 2px corners everywhere | `WaymarkShapes.corner = 2.dp`; no pills |
| Translucent panels, four surface weights | `Panel(faint =)`, `input`, `modal` |
| Bright-accent active fill, never `textStrong` as a background | `SegmentedToggle` |
| Modal: amber hairline, corner brackets, fade + settle | `WaymarkModal`, `Modifier.cornerBrackets` |
| Theme switch with sliding amber knob, `role="switch"` | `ThemeToggle`, 220 ms eased |
| Warm-brown shadows in Dawn, softened | `shadow` + `shadowStrength = 0.4f` |
| Voice: restrained, no marketing, italic asides | `Footnote`, and every string in the app |
| Segmented control for a real choice, quiet rail for navigation | `SegmentedToggle` vs `TabRail` |
| Motion is responsiveness, not decoration — 150–260 ms, eased out | `Motion`, and nothing outside it |

### The mark

A surveyor's north needle: two long triangles meeting on a vertical axis, the
right half amber and the left half cream, with the axis running the full height
of the glyph. It is the same shape in three places — `WaymarkIcons.Waymark` as
a single stroked outline, the adaptive launcher foreground as two filled halves
sized to the 66dp safe zone, and a monochrome layer that cuts the needle out of
its own field so a themed-icon tint does not flatten it into a lozenge.

It replaced a ring-and-chevron mark that was, on inspection, broken: the ring
was centred at the top of the canvas and the chevron sat below it, so the two
halves of the logo never met.

### Motion

Every animation in the app comes from one file, `ui/components/Motion.kt`, and
there are four of them: content settles in on a screen's first composition,
list rows stagger down the column, a value animates to its target rather than
jumping, and the packing tick draws itself along its own two strokes. Buttons
give 3% under a finger. Nothing bounces, nothing spins, nothing animates that
the reader is waiting for.

All of it is scaled by `Settings.Global.ANIMATOR_DURATION_SCALE`, so a device
with animations turned off in accessibility settings — or in battery saver —
gets the final state immediately, with no separate code path to forget about.

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
all for the times nobody publishes on the hour. Minutes still step by five,
because departures are published in fives; the running time is the modal's own
title, so the body is nothing but choices.

Fields that open a picker are shaped *exactly* like fields that accept typing —
same corner, same border, same 11dp of vertical padding — so a date sitting
beside a flight number is the same height as it.

### Two deliberate departures

Both documented at the call site:

- **Backdrop blur.** Compose has no cheap backdrop blur below API 31, so panel
  translucency carries the recession instead of a per-frame render effect. The
  reading-zone effect is preserved; the cost is not.
- **Glows.** Drawn as wide radial gradients with long transparent tails rather
  than blurred layers — already soft, one draw call, and works on every
  supported API level.

Both themes share one type ramp; only colour changes. First launch follows the
system setting; the toggle overrides it.

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

---

## Architecture

```
com.waymark
├── domain/
│   ├── model/      Trip, Traveler, Segment (sealed), Reservation, FlightStatus
│   └── logic/      TimelineBuilder, ConnectionRisk, TransitEstimator,
│                   PartySplitAnalyzer, IdeaBoard, DocumentWatch, PackingPlanner,
│                   TripAnalytics, FlightUpdate, MarkdownExport, LabelPlacer,
│                   Geo (incl. orthographic globe), Bcbp, Code39
├── data/
│   ├── catalog/    Station core list + world directory, coastline,
│   │               destination notes and guide, sample trip
│   ├── local/      Room entities, DAOs, codecs, SecretCipher (Keystore AES-GCM)
│   └── repo/       TripRepository, VaultRepository, FlightRepository,
│                   IdeaRepository, PreparationRepository
├── alerts/         DepartureWatchWorker (WorkManager) + notification channels
├── di/             AppContainer — the whole graph, readable top to bottom
└── ui/
    ├── theme/      Tokens, type ramp, shapes, spacing
    ├── components/ Panel, buttons, chips, toggles, modal, icons, backdrop
    ├── charts/     BarSeries, DayLoadChart, Meter, RingFigure, SplitBar
    ├── map/        WorldMap — one component, flat and orthographic projections
    ├── export/     Markdown export and the share intent
    ├── components/ …including Pickers (date, time), ChoiceCards, TabRail
    ├── trips/ trip/ add/ segment/ pass/ insights/ packing/ analytics/ vault/
```

**The domain layer is plain Kotlin.** No Android imports, no Compose, no Room —
which is why the interesting logic is unit-testable on the JVM and why the
tests below run in a third of a second.

**Persistence.** One Room database. Segments live in a single table with a
`kind` discriminator; the alternative — a table per kind — buys nothing and
costs four joins on the busiest read in the app. Composite values (places,
traveler sets, secrets) go through explicit codecs rather than a JSON
dependency.

**Encryption.** `SecretCipher` seals with AES-256-GCM under a Keystore key. The
key is *not* bound to user authentication: the departure reminder and the
boarding-pass screen must work on a phone nobody is holding. What is gated —
by `BiometricPrompt`, with graceful fallback where nothing is enrolled — is the
moment a code becomes readable on screen.

**Flight data comes from the traveler, and only from the traveler.** There is
no provider interface, no network client, and no status subsystem; the manifest
declares no `INTERNET` permission, so the app *cannot* call anything even by
mistake. A booking holds the times it was given, and changing them means
editing the booking.

**Deleting.** Removing a booking also removes the reservation and boarding
passes that only existed because of it — they carry a foreign key to the trip
but only a plain column to the segment, so nothing cascades and leaving them
behind put deleted flights' codes back in the vault. Removing a whole trip
cascades through the database and sweeps the alert table, which sits outside
the graph on purpose. A sweep on start-up repairs anything a previous version
orphaned. Trip deletion asks for the trip's name to be typed: it is the only
irreversible thing the app can do, and the only friction of its kind.

**The station directory.** `assets/airports.txt` holds 8,831 IATA stations —
the supplied table, with the 259 closed fields dropped and 26 duplicate codes
resolved in favour of the larger, better-described row. **Time zones were
resolved from each station's coordinates when the file was generated**, against
real boundary data, so the app carries an exact IANA zone per airport and
applies no heuristic of its own; a wrong zone moves a flight to the wrong day,
not merely the wrong hour. `AirportDirectory.parse` is pure Kotlin over a line
sequence, so the whole file is checked by the JVM tests. The hand-curated core
list survives alongside it and wins any collision, because it carries terminal
designators and the names travelers actually use ("Heathrow", not "London
Heathrow Airport").

**The basemap.** `Coastline` holds Natural Earth's public-domain 1:110m land
polygons, reduced by Ramer–Douglas–Peucker to 1,403 vertices across 50 rings —
roughly one point per fifty kilometres of coast. Rings that crossed the
antimeridian were clipped into separate closed pieces at generation time, which
is what stops a filled landmass from becoming a stripe across the chart at the
seam. It parses lazily, so a trip that never opens the map never pays for it.

---

## Building

Needs a JDK 17 and an Android SDK with API 35. Gradle finds the SDK through
`local.properties` or `ANDROID_HOME` — neither is in the repository, because
both are specific to the machine.

**With Android Studio installed** (the SDK usually sits in `~/Android/Sdk` on
Linux, `~/Library/Android/sdk` on macOS):

```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
```

**Without Android Studio**, install the command-line tools once:

```bash
# Linux; see developer.android.com/studio#command-line-tools-only for the
# current archive and the macOS equivalent.
mkdir -p ~/Android/Sdk/cmdline-tools && cd ~/Android/Sdk/cmdline-tools
curl -O https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip commandlinetools-linux-*.zip && mv cmdline-tools latest

export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin

sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

Then:

```bash
./gradlew :app:testDebugUnitTest    # the 181 unit tests
./gradlew :app:assembleDebug        # the APK
```

The unit tests need no emulator and no device. There is nothing else to
configure: no API key, no account, no service.

---

## Tests

181 JVM unit tests over the domain and catalog layers:

- `FlightDesignatorTest` — parsing `BA286`, `ba 286`, `BAW286`, `3U8888`, `U2 1234`
- `Code39Test` — symbology invariants (nine elements, three wide, two wide bars
  and one wide space per alphanumeric), which is how a typo in the pattern table
  gets caught
- `GeoTest` — great-circle distances against published figures, arc symmetry,
  Mercator round-trip, antimeridian detection
- `ConnectionRiskTest` — minimum connection times, terminal changes,
  immigration, inter-carrier bag re-check, a delay eating a connection
- `TimelineBuilderTest` — ordering, one entry per segment (two for lodging),
  day breaks, traveler filtering, the now marker
- `PartySplitAnalyzerTest` — the split window, coverage, warnings
- `BcbpTest` — 60-character mandatory section, build/parse round-trip
- `TransitAndTimeTest` — estimates, mode selection, duration and zone-shift text
- `FlightCatalogTest` — the station table's consistency (real time zones, no
  duplicate codes, coordinates in range) and the worked-example schedule that
  seeds the sample trip, including a westbound date-line crossing that lands the
  previous day
- `IdeaBoardTest` — section ordering, per-traveler filtering, suggestions that
  exclude what is already on the list, and promotion to a timeline segment
- `DestinationGuideTest` — guide integrity, including a check that every
  coordinate lands within 120 km of its city's airport, which is what catches a
  transposed latitude and longitude
- `DocumentWatchTest` — expiry, the six-month passport margin, severity
  ordering, travelers with no passport recorded
- `PackingPlannerTest` — counts that scale with the nights and **never fall as
  a trip lengthens**, adaptors only where sockets differ, climate by latitude
  and hemisphere
- `TripAnalyticsTest` — distance splits that add up, day loads that cannot
  exceed a day, carbon against the published factors
- `GlobeProjectionTest` — orthographic projection inside the unit disc, the
  horizon as the culling boundary, and a centroid that survives the antimeridian
- `DurationTextTest` — how a span reads at every scale: hours and minutes under
  a day, then days, then weeks and days, then months, weeks and days; zero terms
  dropped; and a property that the leading unit only ever coarsens as the span
  grows
- `AirportDirectoryTest` — the generated format, interned zones, a station with
  no municipality falling back to its own name, and a malformed line being
  skipped rather than taking the directory down with it
- `AirportAssetTest` — the shipped file itself: every zone one the platform
  knows, every coordinate in range, no duplicate codes, the stations a traveler
  is most likely to type, the half-hour and three-quarter-hour zones that a
  lazy country-to-zone table gets wrong, and **a check that each station's UTC
  offset agrees with its longitude**, which is what catches a transposed
  latitude and longitude
- `MarkdownExportTest` — including the one that matters: **no secret value from
  the fixture appears anywhere in the exported document**
- `LabelPlacerTest` — no two labels overlapping, no label covering a foreign
  mark, everything inside the viewport, priority winning a contested slot, and
  forty marks in one cluster yielding some labels rather than all or none
- `CoastlineTest` — closed rings, real coordinates, no seam crossing from
  interior longitudes, and a point-in-polygon check that Paris is on land and
  the mid-Pacific is not

---

## Notes on honesty

Places where the app says less than it could:

- **Nothing here is live, and the app says so.** Every flight time, delay, gate
  and belt was typed in by a traveler, and every status carries the moment it
  was reported. Waymark holds no network permission, so there is no version of
  it that quietly starts calling a service.
- **The coastline is 1:110m and coarse.** Italy is a boot and Florida is a
  peninsula; it is not a navigational chart and no place is drawn to a
  resolution finer than about fifty kilometres. It is a basemap for reading a
  trip against, not for finding anything by.
- **Six hundred station names lost their accents before the file reached us.**
  The supplied CSV contains literal replacement characters where accented
  letters used to be — "Kōchi Ryōma" arrived as `K����chi Ry����ma` — so the
  runs are stripped rather than guessed at. Codes, coordinates and time zones
  are unaffected, and the municipality is usually intact.
- **The barcode is Code 39 of the short reference**, not the full BCBP payload:
  sixty characters in a one-dimensional symbology is too dense to scan off a
  phone. Airlines use a 2D symbol for that, and an imported pass image is shown
  in preference to the rendered one.
- **The carbon figure is a model, and says so on screen**, with its per-mode
  factors printed beside it. Lodging and meals are excluded rather than guessed.
- **The guide is a briefing, not a guidebook.** Nine cities, a handful of
  entries each, bundled and offline. Where a place is listed, its coordinates
  are accurate to the block; where a dish is listed, it carries no coordinates,
  because a dish is not a place.

---

## Data

Coastlines are derived from [Natural Earth](https://www.naturalearthdata.com)
1:110m land polygons, which are in the public domain. The station directory is
the IATA table supplied with the project, deduplicated and cleaned, with time
zones resolved from coordinates at generation time. Destination notes and the
city guide are hand-compiled. Everything else in the app was entered by
whoever is using it.
