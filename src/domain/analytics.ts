// The trip, counted. Everything is derived from bookings the traveler
// entered; the one modelled figure, carbon, carries its factors in the open.

import { distanceKm, formatDistance } from './geo';
import {
  chronological,
  destinationOf,
  groundModeLabel,
  hasCoordinates,
  involves,
  nights,
  segmentEnd,
  segmentStart,
  shortLabel,
  type Flight,
  type Ground,
  type GroundMode,
  type Idea,
  type Lodging,
  type Segment,
  type TripDossier
} from './model';
import { compareDates, daysBetween, offsetSeconds, startOfDay, type LocalDate } from './time';

/** One row of a chart: a label, a value, and the text beside the mark. */
export interface Measure {
  label: string;
  value: number;
  display: string;
  emphasis: boolean;
}

/** How a single day is spent, in hours. */
export interface DayLoad {
  date: LocalDate;
  movingHours: number;
  bookedHours: number;
  freeHours: number;
}

export function busyHours(d: DayLoad): number {
  return d.movingHours + d.bookedHours;
}

export const WAKING_HOURS = 16;

export interface TripAnalyticsReport {
  totalDistanceKm: number;
  flownKm: number;
  groundKm: number;
  distanceByMode: Measure[];
  hoursMoving: number;
  hoursBooked: number;
  hoursAtRest: number;
  nightsByCity: Measure[];
  dayLoads: DayLoad[];
  busiestDay: DayLoad | null;
  quietestDay: DayLoad | null;
  ideasDone: number;
  ideasOutstanding: number;
  ideasScheduled: number;
  /** How many times a traveler's clock changes — the most travelled itinerary's count. */
  clockChanges: number;
  /** Total hours of clock change on that itinerary, either direction. */
  hoursShifted: number;
  countriesVisited: string[];
  longestLegKm: number;
  longestLegLabel: string | null;
  carbonKg: number;
}

export function hasDistance(r: TripAnalyticsReport): boolean {
  return r.totalDistanceKm > 0;
}

/** Share of the trip's waking hours with something on them. */
export function bookedShare(r: TripAnalyticsReport): number {
  const busy = r.hoursMoving + r.hoursBooked;
  const waking = Math.max(1, r.dayLoads.length * WAKING_HOURS);
  return Math.min(1, Math.max(0, busy / waking));
}

/** kg CO₂e per passenger-km: mid-range figures from published national inventories. */
export const EMISSION_FACTORS = {
  SHORT_HAUL_FLIGHT: 0.246,
  LONG_HAUL_FLIGHT: 0.15,
  TRAIN: 0.035,
  BUS: 0.097,
  CAR: 0.171,
  FERRY: 0.113,
  LONG_HAUL_THRESHOLD_KM: 3700
} as const;

/** A year: the longest trip the day-by-day chart will lay out. */
export const MAX_DAYS = 366;

const sum = (values: number[]) => values.reduce((a, b) => a + b, 0);
const maxBy = <T>(items: readonly T[], key: (t: T) => number): T | null =>
  items.reduce<T | null>((best, t) => (best === null || key(t) > key(best) ? t : best), null);
const minBy = <T>(items: readonly T[], key: (t: T) => number): T | null =>
  items.reduce<T | null>((best, t) => (best === null || key(t) < key(best) ? t : best), null);
/** Kotlin's sortedByDescending: stable. */
const byDescending = <T>(items: T[], key: (t: T) => number) => items.sort((a, b) => key(b) - key(a));

export function tripReport(dossier: TripDossier, ideas: readonly Idea[] = []): TripAnalyticsReport {
  const segments = chronological(dossier.segments);
  const flights = segments.filter((s): s is Flight => s.kind === 'FLIGHT');
  const ground = segments.filter((s): s is Ground => s.kind === 'GROUND');

  const flownKm = sum(flights.map(legKm));
  const groundKm = sum(ground.map(legKm));

  const byMode: Measure[] = [];
  if (flownKm > 0)
    byMode.push({ label: 'Air', value: flownKm, display: formatDistance(flownKm), emphasis: true });
  const perMode = new Map<GroundMode, number>();
  for (const g of ground) perMode.set(g.mode, (perMode.get(g.mode) ?? 0) + legKm(g));
  for (const [mode, km] of byDescending(
    [...perMode].filter(([, km]) => km > 0),
    ([, km]) => km
  )) {
    byMode.push({ label: groundModeLabel(mode), value: km, display: formatDistance(km), emphasis: false });
  }

  const hoursMoving = sum(segments.filter((s) => s.kind === 'FLIGHT' || s.kind === 'GROUND').map(hours));
  const hoursBooked = sum(segments.filter((s) => s.kind === 'EXPERIENCE').map(hours));

  const perCity = new Map<string, number>();
  for (const l of segments.filter((s): s is Lodging => s.kind === 'LODGING')) {
    const city = l.origin.city.trim() === '' ? l.propertyName : l.origin.city;
    perCity.set(city, (perCity.get(city) ?? 0) + nights(l));
  }
  const nightsByCity = byDescending([...perCity], ([, n]) => n).map(([city, count]) => ({
    label: city,
    value: count,
    display: `${Math.round(count)} night` + (count === 1 ? '' : 's'),
    emphasis: false
  }));

  const loads = dayLoads(segments);
  const longest = maxBy(flights, legKm);
  const shifts = itineraries(dossier).map(clockShifts);

  return {
    totalDistanceKm: flownKm + groundKm,
    flownKm,
    groundKm,
    distanceByMode: byMode,
    hoursMoving,
    hoursBooked,
    hoursAtRest: Math.max(0, loads.length * 24 - hoursMoving - hoursBooked),
    nightsByCity,
    dayLoads: loads,
    busiestDay: maxBy(loads, busyHours),
    quietestDay: minBy(
      loads.filter((d) => busyHours(d) > 0),
      busyHours
    ),
    ideasDone: ideas.filter((i) => i.status === 'DONE').length,
    ideasOutstanding: ideas.filter((i) => i.status === 'SAVED').length,
    ideasScheduled: ideas.filter((i) => i.status === 'SCHEDULED').length,
    clockChanges: shifts.length === 0 ? 0 : Math.max(...shifts.map((s) => s.length)),
    hoursShifted: shifts.length === 0 ? 0 : Math.max(...shifts.map((own) => sum(own.map(Math.abs)) / 60)),
    countriesVisited: [
      ...new Set(
        segments.flatMap((s) => [s.origin.country, destinationOf(s).country]).filter((c) => c.trim() !== '')
      )
    ].sort((a, b) => (a < b ? -1 : a > b ? 1 : 0)),
    longestLegKm: longest ? legKm(longest) : 0,
    longestLegLabel: longest ? `${shortLabel(longest.origin)} → ${shortLabel(longest.destination)}` : null,
    carbonKg: carbonKg(segments)
  };
}

/**
 * Minutes of clock change on each move where the clock changes, on the
 * offsets in force at the moment of travel: San Francisco to London is +480.
 */
export function clockShifts(segments: readonly Segment[]): number[] {
  return chronological(segments.filter((s) => s.kind === 'FLIGHT' || s.kind === 'GROUND'))
    .map((leg) => {
      const before = offsetSeconds(leg.startZoneId, leg.startEpochMillis);
      const after = offsetSeconds(leg.endZoneId, leg.startEpochMillis);
      return Math.trunc((after - before) / 60);
    })
    .filter((m) => m !== 0);
}

/** Each traveler's own bookings, or the whole trip when there is no party. */
function itineraries(dossier: TripDossier): Segment[][] {
  const travelers = dossier.party.travelers;
  if (travelers.length === 0) return [dossier.segments];
  return travelers.map((t) => dossier.segments.filter((s) => involves(s, t.id)));
}

/** Hours per day with something on them, over the trip's own calendar. */
export function dayLoads(segments: readonly Segment[]): DayLoad[] {
  if (segments.length === 0) return [];
  const starts = segments.map((s) => segmentStart(s).toPlainDate());
  const ends = segments.map((s) => segmentEnd(s).toPlainDate());
  const first = starts.reduce((a, b) => (compareDates(b, a) < 0 ? b : a));
  const last = ends.reduce((a, b) => (compareDates(b, a) > 0 ? b : a));
  const days = Math.min(MAX_DAYS - 1, Math.max(0, daysBetween(first, last)));

  return Array.from({ length: days + 1 }, (_, offset) => {
    const date = first.add({ days: offset });
    const onThisDay = segments.filter(
      (_s, i) => compareDates(date, starts[i]!) >= 0 && compareDates(date, ends[i]!) <= 0
    );
    const moving = sum(
      onThisDay.filter((s) => s.kind === 'FLIGHT' || s.kind === 'GROUND').map((s) => hoursOnDay(s, date))
    );
    const booked = sum(onThisDay.filter((s) => s.kind === 'EXPERIENCE').map((s) => hoursOnDay(s, date)));
    return {
      date,
      movingHours: moving,
      bookedHours: booked,
      freeHours: Math.max(0, WAKING_HOURS - moving - booked)
    };
  });
}

/** A modelled figure: distance times a published factor per mode. Lodging and meals excluded. */
export function carbonKg(segments: readonly Segment[]): number {
  return sum(
    segments.map((s) => {
      const km = legKm(s);
      if (s.kind === 'FLIGHT') {
        return (
          km *
          (km >= EMISSION_FACTORS.LONG_HAUL_THRESHOLD_KM
            ? EMISSION_FACTORS.LONG_HAUL_FLIGHT
            : EMISSION_FACTORS.SHORT_HAUL_FLIGHT)
        );
      }
      if (s.kind === 'GROUND') return km * groundFactor(s.mode);
      return 0;
    })
  );
}

function groundFactor(mode: GroundMode): number {
  switch (mode) {
    case 'TRAIN':
    case 'TRANSIT':
      return EMISSION_FACTORS.TRAIN;
    case 'BUS':
    case 'SHUTTLE':
      return EMISSION_FACTORS.BUS;
    case 'TAXI':
    case 'RIDESHARE':
    case 'RENTAL_CAR':
      return EMISSION_FACTORS.CAR;
    case 'FERRY':
      return EMISSION_FACTORS.FERRY;
    case 'WALK':
      return 0;
  }
}

function legKm(s: Segment): number {
  const to = destinationOf(s);
  return hasCoordinates(s.origin) && hasCoordinates(to) ? distanceKm(s.origin, to) : 0;
}

function hours(s: Segment): number {
  return (s.endEpochMillis - s.startEpochMillis) / 3_600_000;
}

/** The part of a segment that falls on one local day, in the zone it starts in. */
function hoursOnDay(s: Segment, date: LocalDate): number {
  const dayStart = startOfDay(date, s.startZoneId).epochMilliseconds;
  const dayEnd = startOfDay(date.add({ days: 1 }), s.startZoneId).epochMilliseconds;
  const from = Math.max(s.startEpochMillis, dayStart);
  const to = Math.min(s.endEpochMillis, dayEnd);
  return Math.max(0, to - from) / 3_600_000;
}
