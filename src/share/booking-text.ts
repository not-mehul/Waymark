// Flights read out of a confirmation email, a pasted itinerary, a text
// message — on the device, with nothing sent anywhere. It reads the way a
// person skims: find each flight number, then look just after it for two
// airport codes, a date and two times. A flight number counts only when its
// airline is known and an airport code turns up beside it.

import { airlineName, knowsAirline } from '../catalog/airlines';
import { airportPlace, findAirport } from '../catalog/airports';
import { parseFlightDesignator } from '../domain/flight-designator';
import { flightLeg } from '../domain/leg-times';
import { flight, type Flight } from '../domain/model';
import { compareDates, localTime, Temporal, type LocalDate, type LocalTime } from '../domain/time';

export interface FoundFlight {
  designator: string;
  date: LocalDate | null;
  origin: string | null;
  destination: string | null;
  departs: LocalTime | null;
  arrives: LocalTime | null;
}

export function isComplete(f: FoundFlight): boolean {
  return (
    f.date !== null && f.origin !== null && f.destination !== null && f.departs !== null && f.arrives !== null
  );
}

export interface BookingReading {
  flights: FoundFlight[];
  reference: string | null;
}

/**
 * A complete flight as a booking on [tripId], or null when anything is
 * missing or does not add up: an unknown airport, or times that describe no
 * possible flight.
 */
export function toSegment(
  found: FoundFlight,
  tripId: string,
  id: string,
  reference: string | null
): Flight | null {
  const designator = parseFlightDesignator(found.designator);
  if (!designator) return null;
  const fromAirport = findAirport(found.origin);
  const toAirport = findAirport(found.destination);
  if (!fromAirport || !toAirport) return null;
  const origin = airportPlace(fromAirport);
  const destination = airportPlace(toAirport);
  if (!found.date || !found.departs || !found.arrives) return null;
  const leg = flightLeg(found.date, found.departs, origin.timeZoneId, found.arrives, destination.timeZoneId);
  if (!leg) return null;
  return flight({
    id,
    tripId,
    carrierCode: designator.carrier,
    flightNumber: `${designator.number}${designator.suffix ?? ''}`,
    origin,
    destination,
    startEpochMillis: leg.departure.epochMilliseconds,
    endEpochMillis: leg.arrival.epochMilliseconds,
    startZoneId: origin.timeZoneId,
    endZoneId: destination.timeZoneId,
    confirmationCode: reference,
    bookedWith: airlineName(designator.carrier)
  });
}

const DESIGNATOR = /\b([A-Z][A-Z0-9]|[0-9][A-Z])\s?0*(\d{1,4})([A-Z]?)\b/g;
const CODE = /\b[A-Z]{3}\b/g;
/** 16:20, 4.20 pm, 16h20 — and not the 14.05 of a date written 14.05.2026. */
const TIME = /(?<![\d.:])([01]?\d|2[0-3])[:.h]([0-5]\d)(?![\d:]|\.\d)\s*([AaPp]\.?[Mm]\.?)?/g;
const REFERENCE =
  /(?:booking reference|booking ref|confirmation(?: code| number)?|record locator|reservation code|pnr)\s*(?:is|:|#|no\.?)?\s*([A-Z0-9]{5,8})\b/i;

/** Words that look like airport codes in capitals and are not. */
const NOT_STATIONS = new Set([
  'THE',
  'AND',
  'FOR',
  'YOU',
  'ARE',
  'NOT',
  'ALL',
  'ANY',
  'PNR',
  'REF',
  'TAX',
  'FEE',
  'USD',
  'EUR',
  'GBP',
  'JPY',
  'AUD',
  'CAD',
  'CHF',
  'PLC',
  'LTD',
  'INC',
  'VAT',
  'MON',
  'TUE',
  'WED',
  'THU',
  'FRI',
  'SAT',
  'SUN',
  'JAN',
  'FEB',
  'MAR',
  'APR',
  'MAY',
  'JUN',
  'JUL',
  'AUG',
  'SEP',
  'OCT',
  'NOV',
  'DEC',
  'SEAT',
  'GATE'
]);

/** How far after a flight number its details are looked for. */
const WINDOW = 400;

export function readBookingText(
  text: string,
  today: LocalDate = Temporal.Now.plainDateISO()
): BookingReading {
  const reference = REFERENCE.exec(text)?.[1]?.toUpperCase() ?? null;
  const hits = [...text.matchAll(DESIGNATOR)].filter((m) => knowsAirline(m[1]!));

  const flights: FoundFlight[] = [];
  const seen = new Set<string>();
  hits.forEach((hit, index) => {
    const hitStart = hit.index!;
    const hitEnd = hitStart + hit[0].length;
    // The flight's own stretch of text: up to the next flight number, and no
    // further than a few hundred characters.
    const end = hits[index + 1]?.index ?? text.length;
    const window = text.substring(hitEnd, Math.min(end, hitEnd + WINDOW));
    const stations = [
      ...new Set(
        [...window.matchAll(CODE)]
          .map((m) => m[0])
          .filter((c) => !NOT_STATIONS.has(c) && findAirport(c) !== null)
      )
    ].slice(0, 2);
    if (stations.length === 0) return;
    const times = [...window.matchAll(TIME)]
      .map(timeOf)
      .filter((t): t is LocalTime => t !== null)
      .slice(0, 2);
    const found: FoundFlight = {
      designator: hit[1]! + hit[2]!.replace(/^0+/, '') + hit[3]!,
      date: nearestDate(text, hitStart, hitEnd - 1, hitEnd + window.length, today),
      origin: stations[0] ?? null,
      destination: stations[1] ?? null,
      departs: times[0] ?? null,
      arrives: times[1] ?? null
    };
    const key = `${found.designator}|${found.date?.toString() ?? ''}`;
    if (seen.has(key)) return;
    seen.add(key);
    flights.push(found);
  });
  return { flights, reference };
}

function timeOf(match: RegExpMatchArray): LocalTime | null {
  let hour = Number(match[1]);
  const minute = Number(match[2]);
  const meridiem = (match[3] ?? '').toLowerCase().replace(/[^a-z]/g, '');
  if (meridiem !== '') {
    if (hour < 1 || hour > 12) return null;
    if (meridiem.startsWith('p') && hour !== 12) hour += 12;
    if (meridiem.startsWith('a') && hour === 12) hour = 0;
  }
  return localTime(hour, minute);
}

// — Dates ———————————————————————————————————————————————————————————————

const MONTH_NAMES = [
  'january',
  'february',
  'march',
  'april',
  'may',
  'june',
  'july',
  'august',
  'september',
  'october',
  'november',
  'december'
];
const MONTHS = new Map<string, number>();
MONTH_NAMES.forEach((name, i) => {
  MONTHS.set(name, i + 1);
  if (!MONTHS.has(name.slice(0, 3))) MONTHS.set(name.slice(0, 3), i + 1);
});
MONTHS.set('sept', 9);

const MONTH = [...MONTHS.keys()].sort((a, b) => b.length - a.length).join('|');
const ISO = /\b(\d{4})-(\d{2})-(\d{2})\b/g;
const DAY_MONTH = new RegExp(`\\b(\\d{1,2})(?:st|nd|rd|th)?\\s*(${MONTH})\\.?,?\\s*(\\d{4})?\\b`, 'gi');
const MONTH_DAY = new RegExp(`\\b(${MONTH})\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?,?\\s*(\\d{4})?\\b`, 'gi');

function isoDate(year: number, month: number, day: number): LocalDate | null {
  try {
    return Temporal.PlainDate.from({ year, month, day }, { overflow: 'reject' });
  } catch {
    return null;
  }
}

/**
 * The date written closest to the flight number — a heading just above it or
 * a phrase just after — looking no further ahead than [until]. A year left
 * out is the next one that makes the date upcoming.
 */
function nearestDate(
  text: string,
  hitFirst: number,
  hitLast: number,
  until: number,
  today: LocalDate
): LocalDate | null {
  const scope = text.substring(0, until);
  const candidates: [number, number, LocalDate][] = [];
  const add = (m: RegExpMatchArray, date: LocalDate | null) => {
    if (date) candidates.push([m.index!, m.index! + m[0].length - 1, date]);
  };
  for (const m of scope.matchAll(ISO)) add(m, isoDate(Number(m[1]), Number(m[2]), Number(m[3])));
  for (const m of scope.matchAll(DAY_MONTH)) add(m, dateOf(m[1]!, m[2]!, m[3] ?? '', today));
  for (const m of scope.matchAll(MONTH_DAY)) add(m, dateOf(m[2]!, m[1]!, m[3] ?? '', today));
  let best: [number, LocalDate] | null = null;
  for (const [first, last, date] of candidates) {
    const distance = first > hitLast ? first - hitLast : hitFirst - last;
    if (best === null || distance < best[0]) best = [distance, date];
  }
  return best?.[1] ?? null;
}

function dateOf(day: string, monthName: string, year: string, today: LocalDate): LocalDate | null {
  const month = MONTHS.get(monthName.toLowerCase().replace(/\.+$/, ''));
  if (month === undefined) return null;
  const dayOfMonth = Number(day);
  if (year !== '') return isoDate(Number(year), month, dayOfMonth);
  // MonthDay.of: valid in some year (29 February included).
  if (isoDate(2000, month, dayOfMonth) === null) return null;
  const atYear = (y: number) =>
    Temporal.PlainDate.from({ year: y, month, day: dayOfMonth }, { overflow: 'constrain' });
  const thisYear = atYear(today.year);
  // An email about a flight is about a flight to come.
  return compareDates(thisYear, today.subtract({ days: 7 })) < 0 ? atYear(today.year + 1) : thisYear;
}
