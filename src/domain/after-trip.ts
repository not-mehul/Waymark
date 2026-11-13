// The cities a trip actually stayed in, for the hand-offs once it is over:
// "Rate in Ledger" (style.md §14.1) and a postcard for the nookling.

import { countryOf } from '../catalog/countries';
import {
  chronological,
  destinationOf,
  segmentEnd,
  segmentStart,
  type Place,
  type TripDossier
} from './model';
import { destinations } from './time-at-place';
import type { LocalDate } from './time';

export interface CityVisit {
  city: string;
  /** ISO 3166-1 alpha-2, or '' when the place does not say. */
  country: string;
  /** The first day the trip was there. */
  arrived: LocalDate;
}

const cityOf = (p: Place) => (p.city.trim() || p.name.trim()).replace(/\s+/g, ' ');
const keyOf = (p: Place) => cityOf(p).toLowerCase();

/**
 * Every city with a stay booked in it, then every other place the trip spent
 * a day or more, in the order they were reached. A connection is not a visit.
 */
export function citiesVisited(dossier: TripDossier): CityVisit[] {
  const out = new Map<string, CityVisit>();
  const visit = (p: Place, arrived: LocalDate) => {
    const key = keyOf(p);
    if (key === '' || out.has(key)) return;
    out.set(key, { city: cityOf(p), country: countryOf(p)?.code ?? '', arrived });
  };
  const ordered = chronological(dossier.segments);
  for (const s of ordered) if (s.kind === 'LODGING') visit(s.origin, segmentStart(s).toPlainDate());
  for (const stay of destinations(dossier)) {
    const reached = ordered.find(
      (s) => s.kind !== 'LODGING' && keyOf(destinationOf(s)) === keyOf(stay.place)
    );
    if (reached) visit(stay.place, segmentEnd(reached).toPlainDate());
  }
  return [...out.values()].sort((a, b) => a.arrived.toString().localeCompare(b.arrived.toString()));
}
