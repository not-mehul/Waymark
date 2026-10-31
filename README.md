# Waymark

An Android travel-logistics app: every flight, stay, transfer and booking for a
trip — for one traveler or a party that splits up — on one timeline, one vector
chart. It works with the radio off.

Kotlin 2.4, Jetpack Compose, Navigation 3, Hilt, Room, WorkManager, DataStore
and Glance, built with Android Gradle Plugin 9.5 on Gradle 9.8 and targeting
Android 17. Beyond AndroidX and Hilt the only runtime libraries are
kotlinx.serialization, for trip files and the saved back stack, and ZXing's
core, for reading and drawing barcodes.

---

## What it does

**Enter a flight.** A designator, a date, two airport codes, two times off a
picker. Waymark calls no schedule service and no tracking API, so what it
contributes is only what an airport code
already implies: coordinates and a time zone, from a bundled directory of
**8,831 IATA stations**. That is enough to put the leg on the map, to work out
that a 16:20 out of San Francisco lands the following morning, and to show the
block time back as you set it, which is the quickest way to catch a time
entered against the wrong airport.

**Or don't type it.** Share a confirmation email to Waymark, or paste one into
Add flight, and it reads the flight numbers, airports, dates and times out of
it — on the phone, with nothing sent anywhere — and shows them to be checked
before anything is saved. Pick a screenshot or a photo of a boarding pass and
the barcode fills in the flight, the airports, the date, the cabin and the
seat; a pass does not carry clock times, so those are left to you.

**Add things one question at a time.** A plan or an idea is entered through a
short flow rather than a form: what kind of thing, then what it is, then when,
then the reference numbers somebody has to go and find in an email. Any earlier
step can be tapped to go back and corrected, and closing the sheet at any point
saves nothing at all.

**And then leave it alone.** There is no live tracking and no "what the board
says" to keep current, because nobody is going to update an app at a gate. A
booking is a record of something already arranged, so it opens **read only**;
Edit turns the screen into a form with Save and Cancel — every field, from the
flight number and the airports to a hotel's room and a train's pick-up point —
and nothing is written until Save.

**Be told before it starts.** The one thing the app volunteers is that
something you entered is about to happen, which it knows from the itinerary and
the clock. **Every category has its own lead time** and any of them can be
switched off: flights three hours out, trains and transfers an hour, a booked
table an hour, a hotel check-in not at all — until you say otherwise. Two more
are about flights: **online check-in**, a day ahead, and **time to leave for
the airport**, worked out from where the trip is before the flight and how
long the journey to the airport takes. A single alarm is set for the next
reminder anywhere in the app, so they arrive on time rather than whenever
Android next lets a background job run; a reminder that fell due while
notifications were blocked is still shown once they are allowed. Tapping one
opens the booking. One notification channel, asked for once, when you save
your first flight.

**See what is next without opening anything.** A home-screen widget shows the
next booking across every trip, counting down, and opens it. Small, it is the
booking and the time left; wide, its day, time and place; tall, the two
bookings after it as well. The widget picker shows it drawn, with a booking in
it, rather than as a blank tile.

**The journey under way, on the lock screen.** From three hours before a
flight — half an hour before a train or a transfer — until it arrives, one
ongoing notification counts down to departure and then fills a bar to
landing. On Android 16 and later it is a Live Update: pinned at the top of the
shade, on the lock screen, and as a chip in the status bar carrying the time
left. It follows the departure-reminder setting for its kind.

**Split the costs.** A trip's Costs tab takes each expense as it happens —
what it was, how much and in which currency, who paid and who it was for —
and says who owes whom: the total, each person's share, and the fewest
payments that square it. Expenses in another currency are converted once, at
a rate the traveler types or, with online extras on, the European Central
Bank's rate for the day. Costs travel with the trip in files and backups.

**The weather where you are going.** With online extras on, the "On arrival"
screen shows each day still to come at the place the trip will be: a forecast
for the next two weeks, and the same dates last year beyond that. What is
fetched is kept, so it is there abroad without a signal.

**Print it.** The trip menu writes the itinerary as an A4 PDF — every booking
under its day, in local time, with who is on it, its reference, the day's
weather and who owes whom — to share, or sends the same pages to a printer.

**Nothing on first launch.** The shelf opens empty, with the worked example
offered rather than installed. Somebody planning a real trip does not begin by
deleting a stranger's holiday, and somebody who wants to see what a full
itinerary looks like is one tap away from a week in London and Paris.

**Keep the codes where you can read them.** A confirmation code, a record
locator and a ticket number are fields on the booking that carries them, shown
in the open on the booking screen. They were a separate encrypted table behind
a fingerprint for a while, which is a ceremony rather than a protection on a
device that holds nothing else encrypted and never sends an itinerary
anywhere — and a code you cannot read without authenticating is a code you cannot
read at a check-in desk with a bag in one hand.

**Travel as a group.** Travelers are profiles — seat and meal preference,
loyalty numbers, a phone number — that belong to the person, not the trip: the
same person on two trips is one profile, edited once, from either trip or from
People on the shelf. They are assigned per segment. When two people are on different bookings — one on the morning
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
mark to open it. The list button reads the same trip out leg by leg, with the
distance each covers, for a screen reader or anyone who would rather read. Labels are placed by a collision solver rather than pinned
beside their marks, so a cluster of hotels in one city does not print on top of
itself; a label with nowhere to go is dropped and its mark stays.

**Keep a list.** Not everything on a trip has a time on it. The Ideas board
holds four kinds — **See, Eat, Do, Shop** — added a question at a time and
saved without a date, then read three ways: by kind, by place, or by the day
they are pencilled in for. A day is a date without a clock time, which is
how planning actually happens; the board totals each day's estimates and says
so when a day is overfull. Each traveler can mark what they want, so a
party of four can see where their interests actually overlap. One tap promotes
an idea onto the timeline with a date and a duration, where it becomes an
ordinary booking; removing that booking puts it back on the list rather than
losing it.

**Count it.** A numbers screen: distance by mode, the shape of each day, where
the hours go, nights per city, and a carbon estimate that shows its factors
rather than asserting a figure, above the same globe you can turn with a finger.

**Take it with you.** A trip leaves the app four ways, all through the system
share sheet. As **markdown** — days as headings, every booking as a line, the
idea list as checkboxes, the booking references as an index. As a **calendar
file** whose events, in UTC, land at the right moment in any calendar app. As a
**trip file** that Waymark on another phone opens back into the whole trip,
party, bookings and ideas. Or as a **QR code** for the phone across the table,
carrying as much of the trip as fits and saying what it left out. References
travel with all of them, because a confirmation code is the reason to send
somebody an itinerary in the first place.

**Keep a copy.** *Back up every trip* writes one file with everything in it,
wherever you keep things; opening it on a new phone brings everything across.
A phone-to-phone transfer during setup carries the app's data too. Nothing goes
to a cloud backup.

**Put a trip away.** A trip that is over can be archived: off the shelf, quiet —
no reminders, not on the widget — and back with one tap.

**Read it your way.** Clock times follow the phone's 12- or 24-hour setting and
distances its region's miles or kilometres. On a tablet or an unfolded phone the
booking you tap opens beside the timeline instead of over it.

**Places you type mean what you meant.** "Paris" is a city, resolved against a
bundled gazetteer of every city of 15,000 people or more, nearest to where the
trip is that day — so a 15:00 check-in in Paris is 15:00 in Paris, not in the
zone the trip was created in. A three-letter code is a station. Anything else
stays exactly as typed, with no pin on the map rather than a confident pin in
the wrong country, and the screen says back how each place was read.

**Land informed.** What the money is, what the plug is, what number to call and
which way to look before crossing — for **every country** a bundled station can
be in, not for nine hand-written cities.

The destinations are measured rather than assumed. Waymark sums the time
between landing somewhere and leaving it again, ranks the results, and calls
the longest one the destination; anywhere else with a day or more in it is one
too. A trip to Japan that connects through Doha for two hours is not a trip to
Qatar, which is what taking the last arrival used to make of it.

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
of the glyph. It is the adaptive launcher foreground, two filled halves sized to
the 66dp safe zone, with a monochrome layer that cuts the needle out of its own
field so a themed-icon tint does not flatten it into a lozenge.

It replaced a ring-and-chevron mark that was, on inspection, broken: the ring
was centred at the top of the canvas and the chevron sat below it, so the two
halves of the logo never met.

### Motion

Every animation in the app comes from one file, `ui/components/Motion.kt`, and
there are three of them: content settles in on a screen's first composition,
list rows stagger down the column once — not again each time a row scrolls
back into view — and a value animates to its target rather than jumping.
Buttons give 3% under a finger. Nothing bounces, nothing spins, nothing animates
that the reader is waiting for.

Compose scales every one of them by the system's animator duration scale, so a
device with animations turned off in accessibility settings — or in battery
saver — gets the final state immediately.

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
nothing but choices.

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

Thirteen modules, each built by a convention plugin from `build-logic`, so a
module's own build file says only what is particular to it:

```
app                 The activity, Navigation 3 back stack, Hilt graph, reminders,
                    the Live Update and the widget
feature/
├── shelf           The trip shelf, About, backups
├── trip            A trip: timeline, bookings, party, ideas, costs, map, numbers, insights
├── flight          Adding a flight
├── people          Everyone on every trip
└── share           Importing and exporting: files, codes, emails, barcodes
core/
├── domain          Plain Kotlin. Trip, Traveler, Segment (sealed), Idea, and the
│                   logic: TimelineBuilder, ConnectionRisk, PartySplitAnalyzer,
│                   ReminderPlanner, LiveJourney, CostSplitter, WeatherPlan,
│                   TripAnalytics, exports, Geo…
├── catalog         Plain Kotlin. Stations, cities, airlines, country facts,
│                   coastline, the worked example — large tables as resources
├── share           Plain Kotlin. Trip files, backups, QR payloads, emails,
│                   boarding passes
├── data            Room, the settings DataStore, the repositories
├── designsystem    Tokens, type, shapes, the component kit, charts
├── notifications   Whether the app may notify, and asking once
└── online          The only network code: weather and exchange rates, opt-in
baselineprofile     Macrobenchmarks and the baseline-profile generator
```

**Dependency injection is Hilt.** The core modules are plain classes with
plain constructors — their tests build them by hand — and the app module
provides them. View models are `@HiltViewModel`; the two that need a trip
(and, for adding a flight, a flight read from an email) take it through
assisted injection. What Android constructs itself — the reminder worker, the
alarm receiver, the widget — reaches the graph through one entry point.

**Navigation is Navigation 3.** The back stack is a list of `@Serializable`
`Screen` keys that the app owns and edits directly, so "open this booking from
a notification" is two `add`s rather than a deep-link graph, and the list
survives the process being stopped. Each screen gets its own view-model store;
the screens of one trip share the trip's view model through a store kept for
as long as any of them is on the stack, so the timeline, a booking, the map
and the numbers read the same dossier rather than each subscribing again. The
derived state — timeline, splits, ideas board, numbers — is worked out off the
main thread. Back is the system's predictive back, between screens and out of
every sheet.

**The domain layer is plain Kotlin.** No Android imports, no Compose, no Room —
which is why the interesting logic is unit-testable on the JVM and why most of
the tests below run in a fraction of a second.

**Persistence.** One Room database. Segments live in a single table with a
`kind` discriminator; the alternative — a table per kind — buys nothing and
costs four joins on the busiest read in the app. Composite values (places,
traveler sets, seat and ticket maps) go through explicit codecs. Every schema
the app has ever written migrates forward, never destructively, and each one
is opened by the real migrations in the tests.

**Nothing is encrypted, deliberately.** There was an AES-256-GCM vault under an
Android Keystore key, with a `BiometricPrompt` in front of the moment a code
became readable. It is gone. The threat it defended against was somebody
holding the unlocked phone — which is the same person the app is for — and the
cost was that a confirmation code could not be read at a desk without a free
hand for a fingerprint. Nothing here is sent anywhere, so what protects a code
is the device's own lock screen, as it does for everything else on it.

**Flight data comes from the traveler, and only from the traveler.** There is
no provider interface and no status subsystem. The one network client in the
app, `core/online`, fetches the weather and the day's exchange rates, and only
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
the rest, and removes their profile if they are on no other trip. Removing a
whole trip cascades through the database and sweeps the alert table, which sits
outside the graph on purpose. A sweep on start-up repairs anything a previous
version orphaned. Trip deletion asks for the trip's name to be typed: it is the only
irreversible thing the app can do, and the only friction of its kind.

**The station directory.** `core/catalog`'s `airports.txt` holds 8,831 IATA stations —
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

Needs a JDK 21 (the code compiles to Java 17 bytecode) and an Android SDK
with API 37. Gradle finds the SDK through
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
sdkmanager "platform-tools" "platforms;android-37"
```

Then:

```bash
./gradlew testDebugUnitTest         # the unit suite, every module, Robolectric included
./gradlew verifyRoborazziDebug      # the screenshot tests, against the committed references
./gradlew assembleDebug             # the APK
./gradlew lintDebug                 # Android lint, the libraries included
```

The unit and screenshot tests need no emulator and no device. There is
nothing else to configure: no API key, no account, no service.

**Screenshots.** The component kit and the edit-booking screen are drawn in
both themes by Robolectric and compared with the PNGs under each module's
`src/test/screenshots`. The references are drawn on CI's Linux runner, where
they are checked, so a font difference between machines never reads as a
change: after an intended visual change, push a commit whose message contains
`[record screenshots]`, and `screenshots.yml` records and commits them.

**Baseline profile and benchmarks.** `:baselineprofile` drives the release
build on a Gradle-managed emulator: it opens the app, loads the worked
example, opens the trip and reads down it, and records what ran as the
baseline profile the release build compiles in. A commit message containing
`[baseline profile]` runs `baseline-profile.yml`, which records the profile
into `app/src/release/generated/baselineProfiles`, commits it, and measures
cold start uncompiled, with the profile and fully compiled. The profile is
the one the app ships; there is no hand-written one beside it. Locally, with
an emulator image installed:

```bash
./gradlew :app:generateBaselineProfile
./gradlew :baselineprofile:pixel6Api34BenchmarkReleaseAndroidTest
```

Style is checked by ktlint and detekt, run in CI as their command-line jars
against `.editorconfig` and `config/detekt.yml`; the same two commands work
locally with the jars from Maven Central (versions in `build.yml`).

The build writes the Room schema to `core/data/schemas`. A change to the database
shows up there and must be committed with a migration; CI fails a build whose
schema differs from the committed one.

### Releasing

Waymark ships as a sideloaded APK — there is no store listing. Publishing is
a tag push:

```bash
git tag v1.0.0 && git push origin v1.0.0
```

The version comes from the tag — `v1.4.2` builds version name `1.4.2` and code
`10402` — so there is no number in the source to remember to bump.
`.github/workflows/release.yml` runs the unit suite, builds the release APK,
signs it with the keystore held in the repository's secrets, and opens a draft
GitHub release with the APK attached. **[RELEASING.md](RELEASING.md)** covers
making the signing key, the four secrets, building signed on your own machine,
and the short list of things worth checking on a real phone before tagging —
the parts of an app that unit tests cannot reach.

Release builds run R8 with resource shrinking. Without a keystore the build
still completes and says so, and the artifact is named `-unsigned` so it cannot
be mistaken for a shippable one.

---

## Tests

Unit tests over every layer. Most are plain JVM tests of the domain, catalog
and share code; the database, the repository, a view model and a Compose screen
run under Robolectric against a real SQLite and a real composition, and the
screenshot tests draw the component kit and a whole screen in both themes.

- `CostSplitterTest` — an even split, the odd cent, a split among some, no
  more settling payments than people less one, a foreign expense converted
  once, an expense with no rate listed rather than counted, a payer who has
  left the party still owed, and amounts read the way people type them
- `WeatherPlanTest`, `ParsingTest`, `WeatherRepositoryTest` — where the trip is
  on each day still to come, forecast within two weeks and last year beyond,
  both services' replies read and refused, and **nothing fetched at all while
  online extras are off**
- `ItineraryDocumentTest` — every booking once under its day, every reference,
  the day's weather, and who pays whom
- `LiveJourneyTest` — the journey under way: nothing long before departure, a
  countdown from three hours out, a bar that fills to landing, gone on arrival,
  quiet when flight reminders are off, and woken exactly when it next changes
- `ComponentsScreenshotTest`, `EditSegmentScreenshotTest` — the kit and the
  edit screen, Dusk and Dawn, pixel for pixel

- `MigrationTest` — **every schema an earlier build could have left on a
  phone** — three version 1 builds, version 2, early and late version 3 —
  opened through the real migrations and checked by Room against today's
  entities, with the trip, its traveler, booking, idea and reminder coming
  through, and foreign keys still cascading afterwards
- `TripRepositoryTest` — import replaces rather than duplicates, everyone
  ticked is stored as everyone, a booking widens its trip, removing a traveler
  takes their own bookings and keeps the shared ones, a profile on another trip
  survives, an archived trip stays quiet, the widget reads the next few
  bookings soonest first
- `MappersTest` — every kind of row round-trips, an unknown kind is skipped
  rather than a crash, legacy `0,0` coordinates read as unknown
- `ShareViewModelTest`, `EditSegmentScreenTest` — an email offers its flights,
  a trip code is shown before it is written, and the edit screen says why it
  will not save
- `TripArchiveTest` — trip files and QR payloads round-trip, a code fits by
  leaving out what it says it left out, a damaged or newer file is refused
- `ParsingTest` — flights read out of a confirmation email (dates either side
  of the flight number, 12-hour times, a European date not mistaken for a
  time, "Room 215" not mistaken for a flight) and out of an IATA boarding-pass
  barcode, one leg or several
- `SegmentFormTest` — editing a booking: a new airport moves it into that
  airport's zone, an end a little before the start runs past midnight, seats
  are kept only for the people on the flight
- `LegTimesTest`, `LodgingNightsTest` — an arrival's day inferred from two
  clocks, and nights counted as calendar nights in the hotel's zone
- `PlacesTest`, `WhereaboutsTest` — typed places resolved exactly, nearest
  first, never by substring; where the trip is at a given moment
- `IcsExportTest` — one event per booking, UTC times, escaping and folding
- `AirlinesTest` — the carrier table's codes well formed and unique
- `FlightDesignatorTest` — parsing `BA286`, `ba 286`, `BAW286`, `3U8888`, `U2 1234`
- `GeoTest` — great-circle distances against published figures, arc symmetry,
  Mercator round-trip, antimeridian detection
- `ConnectionRiskTest` — minimum connection times, terminal changes,
  immigration, inter-carrier bag re-check, a delay eating a connection
- `TimelineBuilderTest` — ordering, one entry per segment (two for lodging),
  day breaks, traveler filtering, the now marker, and **two travelers on
  parallel flights not being read as one broken connection**
- `PartySplitAnalyzerTest` — the split window and per-traveler coverage
- `ReminderPlannerTest` — that every category can raise one rather than only
  flights, that a category switched off raises nothing, the window boundaries at
  both ends, and **that the signature is stable as the clock closes in** so a
  booking is announced once rather than at every check — but changes when the
  booking moves, because the time did
- `TransitAndTimeTest` — estimates, mode selection, duration and zone-shift text
- `IdeaBoardTest` — section ordering, per-traveler filtering, and promotion to
  a timeline segment
- `CountriesTest` — every row well formed and unique, and the one that decides
  whether the feature works at all: **every country in the bundled station
  directory has notes**, so there is no airport you can enter and get a blank
  screen for
- `TimeAtPlaceTest` — that the worked example resolves to London then Paris,
  that a two-hour connection is not a destination, and that a trip with one
  movement has nowhere to have stayed
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
- `MarkdownExportTest` — sections, chronology, checkbox states, a filename safe
  on any filesystem, and that **every booking reference in the fixture appears
  in the exported document**
- `LabelPlacerTest` — no two labels overlapping, no label covering a foreign
  mark, everything inside the viewport, priority winning a contested slot, and
  forty marks in one cluster yielding some labels rather than all or none
- `CoastlineTest` — closed rings, real coordinates, no seam crossing from
  interior longitudes, and a point-in-polygon check that Paris is on land and
  the mid-Pacific is not

---

## Notes on honesty

Places where the app says less than it could:

- **Nothing about a booking is live, and the app says so.** Every flight time,
  gate and terminal was typed in by a traveler. The app holds the network
  permission for its two online extras, weather and exchange rates, and makes
  no request at all until they are turned on; a test checks that with them off
  nothing is fetched.
- **Weather beyond two weeks is a year old.** No service forecasts further
  out, so those days show what the same dates were like last year, labelled as
  such. It is a guide to what to pack, not a forecast.
- **Exchange rates are reference rates.** The European Central Bank's daily
  rates cover about thirty currencies and are not what a card or a bureau
  charges. A rate from a receipt can be typed in, and a typed rate is never
  replaced by a live one.
- **Offline cuts both ways.** Nothing is backed up off the device on its own.
  A trip lives on one phone until it is shared or backed up to a file, and a
  lost phone with no backup is a lost trip. The About sheet says this too,
  rather than leaving it to be discovered after the fact.
- **The email reader skims; it does not understand.** It finds a flight number
  from an airline it knows with an airport code beside it, and the nearest
  date and times. An unusual layout can defeat it, which is why what it finds
  is shown to be checked rather than saved unseen.
- **English only, for now.** Clock times and distances follow the phone's
  settings, but the words are English and live in the code rather than in
  string resources; translating the app starts with moving them.
- **The coastline is 1:110m and coarse.** Italy is a boot and Florida is a
  peninsula; it is not a navigational chart and no place is drawn to a
  resolution finer than about fifty kilometres. It is a basemap for reading a
  trip against, not for finding anything by.
- **Six hundred station names lost their accents before the file reached us.**
  The supplied CSV contains literal replacement characters where accented
  letters used to be — "Kōchi Ryōma" arrived as `K����chi Ry����ma` — so the
  runs are stripped rather than guessed at. Codes, coordinates and time zones
  are unaffected, and the municipality is usually intact.
- **The carbon figure is a model, and says so on screen**, with its per-mode
  factors printed beside it. Lodging and meals are excluded rather than guessed.
- **The country notes are four facts, not a guidebook.** Money, plug,
  emergency number, which side of the road — chosen because they are true of a
  whole country, do not go stale between releases, and are not somebody's
  opinion. There used to be a bundled city guide with neighbourhoods to stay in
  and dishes to order; it covered nine cities, it was one person's taste, and an
  offline app has no way to correct it. Emergency numbers are the commonly
  published primary number, and several countries run separate lines for
  police, fire and ambulance.

---

## Data

Coastlines are derived from [Natural Earth](https://www.naturalearthdata.com)
1:110m land polygons, which are in the public domain. The station directory is
the IATA table supplied with the project, deduplicated and cleaned, with time
zones resolved from coordinates at generation time, and ISO country codes
expanded to names by the bundled country table. The city gazetteer is
[GeoNames](https://www.geonames.org) `cities15000` (CC BY 4.0), built into
`core/catalog`'s `cities.txt` by `tools/build_cities.py`; the airline names are
hand-compiled. That table — currency, socket
type, mains voltage, emergency number and driving side for 240 countries and
territories — is hand-compiled. Everything else in the app was entered by
whoever is using it.

The same credits are in the app, under the version line at the foot of the trip
shelf, so a reader who never sees this file can still find out what is bundled
in the binary they installed.

---

## Licence

[MIT](LICENSE). The bundled geographic data is credited above: Natural Earth is
public domain, GeoNames is CC BY 4.0.
