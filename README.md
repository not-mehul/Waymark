# Waymark

An Android travel-logistics app: every flight, stay, transfer and booking for a
trip — for one traveler or a party that splits up — on one timeline, one vector
chart, and one encrypted vault. It works with the radio off.

Kotlin, Jetpack Compose, Room, WorkManager. Single module, no third-party
runtime dependencies beyond AndroidX.

---

## What it does

**Type a flight number.** `BA286` and a date produce a populated segment:
route, both local clocks, terminals, aircraft, block time. The schedule is
bundled with the app, so the lookup answers on a plane, in a queue, or in a
country where the SIM does not work. A live provider can be configured on top;
it is never required.

**Track it.** Each flight carries a state — scheduled, boarding, en route,
delayed, landed — with gate, belt, progress and, when airborne, a position on
the chart. Everything is stamped with its source, so the traveler always knows
whether they are reading a live feed or the offline model.

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

**See the geography.** An offline vector chart draws great-circle legs, the
graticule, and every place whose coordinates the app holds. Pinch, pan, tap.

**Land informed.** Bundled destination notes: currency, plug, emergency number,
airport transfer, transit, tipping, seasons, neighbourhoods.

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
| Motion is responsiveness, not decoration | 150–220 ms transitions; nothing animates on load |
| Voice: restrained, no marketing, italic term-definition asides | `EditorialNote`, and every string in the app |

Two deliberate departures, both documented at the call site:

- **Backdrop blur.** Compose has no cheap backdrop blur below API 31, so panel
  translucency carries the recession instead of a per-frame render effect. The
  reading-zone effect is preserved; the cost is not.
- **Glows.** Drawn as wide radial gradients with long transparent tails rather
  than blurred layers — already soft, one draw call, and works on every
  supported API level.

Both themes share one type ramp; only colour changes. First launch follows the
system setting; the toggle overrides it.

---

## Architecture

```
com.waymark
├── domain/
│   ├── model/      Trip, Traveler, Segment (sealed), Reservation, FlightStatus
│   └── logic/      TimelineBuilder, ConnectionRisk, TransitEstimator,
│                   PartySplitAnalyzer, Geo, Bcbp, Code39, TimeText
├── data/
│   ├── catalog/    Bundled airports, schedules, destination notes, sample trip
│   ├── local/      Room entities, DAOs, codecs, SecretCipher (Keystore AES-GCM)
│   ├── remote/     FlightStatusProvider: offline model + optional HTTP feed
│   └── repo/       TripRepository, VaultRepository, FlightRepository
├── alerts/         DelayWatchWorker (WorkManager) + notification channels
├── di/             AppContainer — the whole graph, readable top to bottom
└── ui/
    ├── theme/      Tokens, type ramp, shapes, spacing
    ├── components/ Panel, buttons, chips, toggles, modal, icons, backdrop
    ├── trips/ trip/ add/ segment/ pass/ insights/ map/ vault/
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
key is *not* bound to user authentication: the background delay watcher and the
boarding-pass screen must work on a phone nobody is holding. What is gated —
by `BiometricPrompt`, with graceful fallback where nothing is enrolled — is the
moment a code becomes readable on screen.

**Flight data.** `FlightStatusProvider` implementations are tried in order.
`HttpFlightStatusProvider` is first and only available when a key is compiled
in and the network is up; `OfflineFlightStatusProvider` is last and always
answers. The offline model is deterministic — the same flight on the same day
always yields the same schedule adjustment, so the app never contradicts itself
between screens or across restarts — and its output is labelled as a model
everywhere it surfaces. It is not a claim about the actual aircraft.

---

## Building

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Requires the Android SDK (compileSdk 35, minSdk 26, JDK 17).

Optional live flight feed — the app is fully functional without it:

```properties
# local.properties
waymark.flightApiKey=…
```

The provider is shaped for AviationStack's `/flights` response; swapping
vendors means changing `parse()` and the base URL, nothing else.

---

## Tests

73 JVM unit tests over the domain and catalog layers:

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
- `FlightCatalogTest` — catalog consistency, both local clocks, a westbound
  date-line crossing that lands the previous day
- `OfflineFlightStatusProviderTest` — determinism, phase transitions, delay
  distribution

---

## Notes on honesty

Three places where the app says less than it could:

- **The chart draws no coastline.** There is no tile server and no bundled
  basemap. A schematic coastline traced from memory would look like data and be
  nothing of the sort, so the chart draws the graticule, the legs, and the
  places whose coordinates it actually holds.
- **The barcode is Code 39 of the short reference**, not the full BCBP payload:
  sixty characters in a one-dimensional symbology is too dense to scan off a
  phone. Airlines use a 2D symbol for that, and an imported pass image is shown
  in preference to the rendered one.
- **The offline flight model is a model.** It is deterministic, shaped like
  real-world delay distributions, and labelled as such on every screen it
  reaches.
