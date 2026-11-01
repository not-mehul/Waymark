// The worked example: a week in London and Paris with flights, a hotel, a
// Eurostar and a few ideas already on it. Offered on an empty shelf rather
// than installed, so a real first trip never begins by deleting a fake one,
// and anchored to today so the sample trip is always about to happen.

import { buildSampleItinerary } from '../catalog/sample-itinerary';
import { todayIn, systemZone, type LocalDate } from '../domain/time';
import type { TripRepository } from './trips';

const DAYS_UNTIL_DEPARTURE = 6;

/** Writes the example — all of it or none of it — and returns its trip id. */
export async function seedSample(
  trips: TripRepository,
  departure: LocalDate = todayIn(systemZone()).add({ days: DAYS_UNTIL_DEPARTURE })
): Promise<string> {
  const bundle = buildSampleItinerary(departure);
  await trips.importBundles([{ ...bundle, costs: null }]);
  return bundle.trip.id;
}
