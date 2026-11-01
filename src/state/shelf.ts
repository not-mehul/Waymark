// The shelf's arrangement of trips, as TripsViewModel had it.

import { daysUntilStart, tripStatus, type SeatPreference, type Traveler, type Trip } from '../domain/model';

export interface Shelf {
  active: Trip[];
  upcoming: Trip[];
  past: Trip[];
  /** Put away: off the shelf and quiet, but kept. */
  archived: Trip[];
}

export function arrangeShelf(everything: readonly Trip[], nowMillis: number = Date.now()): Shelf {
  const shown = everything.filter((t) => !t.archived);
  const byStart = (a: Trip, b: Trip) => a.startEpochMillis - b.startEpochMillis;
  const byEndDescending = (a: Trip, b: Trip) => b.endEpochMillis - a.endEpochMillis;
  return {
    active: shown.filter((t) => tripStatus(t, nowMillis) === 'ACTIVE').sort(byStart),
    upcoming: shown.filter((t) => tripStatus(t, nowMillis) === 'UPCOMING').sort(byStart),
    past: shown.filter((t) => tripStatus(t, nowMillis) === 'PAST').sort(byEndDescending),
    archived: everything.filter((t) => t.archived).sort(byEndDescending)
  };
}

export type CardTone = 'active' | 'upcoming' | 'past';

/** The line under a trip card's dates: "Under way", "Complete", "In 6 days". */
export function cardStatus(trip: Trip, nowMillis: number = Date.now()): { text: string; tone: CardTone } {
  const status = tripStatus(trip, nowMillis);
  if (status === 'ACTIVE') return { text: 'Under way', tone: 'active' };
  if (status === 'PAST') return { text: 'Complete', tone: 'past' };
  const days = daysUntilStart(trip, nowMillis);
  const text = days === 0 ? 'Departs today' : days === 1 ? 'Departs tomorrow' : `In ${days} days`;
  return { text, tone: 'upcoming' };
}

export const SEAT_LABEL: Record<SeatPreference, string> = {
  NONE: 'No preference',
  WINDOW: 'Window',
  AISLE: 'Aisle',
  EXIT_ROW: 'Exit row',
  BULKHEAD: 'Bulkhead'
};

/** "Window · Vegetarian · BA 12345678" — the standing details in one line. */
export function profileSummary(traveler: Traveler): string | null {
  const [first, ...rest] = traveler.loyaltyRefs;
  const parts = [
    traveler.seatPreference === 'NONE' ? null : SEAT_LABEL[traveler.seatPreference],
    traveler.mealPreference,
    first === undefined ? null : rest.length > 0 ? `${first} +${rest.length}` : first,
    traveler.contactPhone
  ].filter((p): p is string => p !== null && p !== '');
  return parts.length > 0 ? parts.join(' · ') : null;
}

export const plural = (n: number, one: string, many: string = `${one}s`) => `${n} ${n === 1 ? one : many}`;
