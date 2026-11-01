// Shorthands the ported Kotlin tests share.
import { readFileSync } from 'node:fs';
import { buildSampleItinerary, type SampleBundle } from '../src/catalog/sample-itinerary';
import type { Flight, Segment, TripDossier } from '../src/domain/model';
import { Temporal, localDate, zonedOf, type LocalDate, type LocalTime } from '../src/domain/time';

/** `LocalTime.parse("21:45")`. */
export const t = (text: string): LocalTime => Temporal.PlainTime.from(text);

/** `LocalDate.of(y, m, d)`. */
export const date = (year: number, month: number, day: number): LocalDate => localDate(year, month, day);

/** `ZonedDateTime.of(date, LocalTime.parse(time), ZoneId.of(zone)).toInstant().toEpochMilli()`. */
export const at = (day: LocalDate, time: string, zone: string): number =>
  zonedOf(day, t(time), zone).epochMilliseconds;

/** `Instant.parse(text).toEpochMilli()`. */
export const instant = (text: string): number => Temporal.Instant.from(text).epochMilliseconds;

/** The worked example, leaving San Francisco on 14 May 2026, as most tests use it. */
export const sample = (departure: LocalDate = date(2026, 5, 14)) => buildSampleItinerary(departure);

export const MINUTE = 60_000;
export const HOUR = 60 * MINUTE;

/** The `TripDossier(trip, TripParty(trip.id, travelers), segments)` most tests open with. */
export const dossierOf = (bundle: SampleBundle, segments: Segment[] = bundle.segments): TripDossier => ({
  trip: bundle.trip,
  party: { tripId: bundle.trip.id, travelers: bundle.travelers },
  segments
});

/** `filterIsInstance<Segment.Flight>()`. */
export const flightsIn = (segments: readonly Segment[]): Flight[] =>
  segments.filter((s): s is Flight => s.kind === 'FLIGHT');

/** Where the bundled catalog files live; vite.config.ts ships the same folder. */
export const CATALOG_SOURCE = 'data/catalog';

/** A bundled catalog file, read off disk. */
export const bundledLines = (name: string): string[] =>
  readFileSync(new URL(`../${CATALOG_SOURCE}/${name}`, import.meta.url), 'utf8').split('\n');
