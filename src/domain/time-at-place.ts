// Where a trip actually spends its time. Between landing somewhere and
// leaving again, the party is there; those gaps are summed per place and
// ranked. A two-hour connection in Doha is not a trip to Qatar.

import { chronological, destinationOf, type Place, type TripDossier } from './model';

/** Somewhere the trip stops, and how long it stops there. */
export interface Stay {
  place: Place;
  minutes: number;
}

export function stayHours(s: Stay): number {
  return s.minutes / 60;
}

/** A day. Below this, somewhere is a connection rather than a destination. */
export const DESTINATION_MINUTES = 24 * 60;

/** Merged on city where there is one: an airport and a station in one city are one stay. */
function keyOf(p: Place): string {
  return p.city.trim().toLowerCase() || p.name.trim().toLowerCase();
}

/** Every place the trip pauses, longest first, optionally for one traveler. */
export function stays(dossier: TripDossier, travelerFilter: string | null = null): Stay[] {
  const moves = chronological(
    dossier.segments
      .filter((s) => s.kind === 'FLIGHT' || s.kind === 'GROUND')
      .filter(
        (s) => travelerFilter === null || s.travelerIds.length === 0 || s.travelerIds.includes(travelerFilter)
      )
  );
  if (moves.length < 2) return [];

  const totals = new Map<string, Stay>();
  for (let i = 1; i < moves.length; i++) {
    const arrived = moves[i - 1]!;
    const leaves = moves[i]!;
    const minutes = Math.trunc((leaves.startEpochMillis - arrived.endEpochMillis) / 60_000);
    if (minutes <= 0) continue;
    const p = destinationOf(arrived);
    const key = keyOf(p);
    if (key === '') continue;
    const existing = totals.get(key);
    totals.set(key, { place: existing?.place ?? p, minutes: (existing?.minutes ?? 0) + minutes });
  }

  return [...totals.values()].sort((a, b) => {
    if (b.minutes !== a.minutes) return b.minutes - a.minutes;
    const ka = keyOf(a.place);
    const kb = keyOf(b.place);
    return ka < kb ? -1 : ka > kb ? 1 : 0;
  });
}

/** The one place a trip is about, or null when it never settles anywhere. */
export function primaryStay(dossier: TripDossier, travelerFilter: string | null = null): Stay | null {
  return stays(dossier, travelerFilter)[0] ?? null;
}

/** The destination, plus everywhere else worth a day; always at least the longest stay. */
export function destinations(dossier: TripDossier, travelerFilter: string | null = null): Stay[] {
  const ranked = stays(dossier, travelerFilter);
  if (ranked.length === 0) return [];
  const worthIt = ranked.filter((s) => s.minutes >= DESTINATION_MINUTES);
  return worthIt.length > 0 ? worthIt : [ranked[0]!];
}
