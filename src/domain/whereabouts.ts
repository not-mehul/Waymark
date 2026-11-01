// Where the trip is at a given moment, read from the bookings around it. A
// plan typed as "the bakery on the corner" has no address, but at its time
// the traveler is somewhere — and that place's clock is the plan's clock.

import { chronological, destinationOf, positionOf, type LatLon, type Place, type Segment } from './model';

/**
 * Where the trip is known to be at [atMillis]: inside a stay or a booked
 * visit, or wherever the last thing before it ended. Null before anything.
 */
export function whereaboutsBefore(segments: readonly Segment[], atMillis: number): Place | null {
  const ordered = chronological(segments);
  const inside = ordered.findLast(
    (s) =>
      (s.kind === 'LODGING' || s.kind === 'EXPERIENCE') &&
      atMillis >= s.startEpochMillis &&
      atMillis <= s.endEpochMillis
  );
  if (inside) return inside.origin;
  let latest: Segment | null = null;
  for (const s of ordered) {
    if (s.endEpochMillis <= atMillis && (latest === null || s.endEpochMillis > latest.endEpochMillis))
      latest = s;
  }
  return latest ? destinationOf(latest) : null;
}

/** Where the trip is at [atMillis], or — before anything — where it is about to start. */
export function whereaboutsAt(segments: readonly Segment[], atMillis: number): Place | null {
  return whereaboutsBefore(segments, atMillis) ?? chronological(segments)[0]?.origin ?? null;
}

export function zoneIdAt(segments: readonly Segment[], atMillis: number, fallback: string): string {
  return whereaboutsAt(segments, atMillis)?.timeZoneId ?? fallback;
}

/** A position to rank same-named cities against, when the place has one. */
export function positionAt(segments: readonly Segment[], atMillis: number): LatLon | null {
  const p = whereaboutsAt(segments, atMillis);
  return p ? positionOf(p) : null;
}
