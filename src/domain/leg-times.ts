// Turning two local clock times into a leg with a real length. SFO 21:45 to
// Singapore 06:30 lands two calendar days later, and Hong Kong 00:50 to Los
// Angeles 22:15 lands the day before it left; "add a day" fixes neither.

import {
  daysBetween,
  minutesBetween,
  zonedOf,
  zonedOfDateTime,
  type LocalDate,
  type LocalDateTime,
  type LocalTime,
  type ZonedDateTime
} from './time';

/** Longer than any scheduled flight (about nineteen hours), short enough to catch a wrong airport. */
export const MAX_FLIGHT_MINUTES = 22 * 60;

/** The calendar shifts an arrival can sit on, relative to the departure date. */
export const DAY_OFFSETS: readonly number[] = [-1, 0, 1, 2];

export interface Leg {
  departure: ZonedDateTime;
  arrival: ZonedDateTime;
}

export function legMinutes(leg: Leg): number {
  return minutesBetween(leg.departure, leg.arrival);
}

/** Arrival's local date minus departure's local date: `+1`, `-1`, `0`. */
export function legDayOffset(leg: Leg): number {
  return daysBetween(leg.departure.toPlainDate(), leg.arrival.toPlainDate());
}

/**
 * A flight from local departure and arrival clock times. With [dayOffset]
 * null, the arrival day that gives the shortest positive flight wins. Null
 * when the times cannot describe a flight.
 */
export function flightLeg(
  date: LocalDate,
  departs: LocalTime,
  departureZone: string,
  arrives: LocalTime,
  arrivalZone: string,
  dayOffset: number | null = null
): Leg | null {
  const departure = zonedOf(date, departs, departureZone);
  const offsets = dayOffset === null ? DAY_OFFSETS : [dayOffset];
  let best: Leg | null = null;
  for (const offset of offsets) {
    const leg = { departure, arrival: zonedOf(date.add({ days: offset }), arrives, arrivalZone) };
    const minutes = legMinutes(leg);
    if (minutes < 1 || minutes > MAX_FLIGHT_MINUTES) continue;
    if (best === null || minutes < legMinutes(best)) best = leg;
  }
  return best;
}

/**
 * An end that should follow a start. When the end as typed is not after the
 * start but would be a day later, it runs past midnight; anything further out
 * of order is returned unchanged for the caller to refuse.
 */
export function endAfter(start: ZonedDateTime, end: LocalDateTime, endZone: string): ZonedDateTime {
  const asTyped = zonedOfDateTime(end, endZone);
  if (asTyped.epochMilliseconds > start.epochMilliseconds) return asTyped;
  const nextDay = asTyped.add({ days: 1 });
  return nextDay.epochMilliseconds > start.epochMilliseconds ? nextDay : asTyped;
}

/** True when [end] follows [start] — the check every save button needs. */
export function isOrdered(start: ZonedDateTime, end: ZonedDateTime): boolean {
  return end.epochMilliseconds > start.epochMilliseconds;
}
