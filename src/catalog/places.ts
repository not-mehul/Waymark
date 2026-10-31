// What a typed place means: a station, a city, or just the words. Every step
// is an exact match; text that matches nothing stays as typed, with no
// coordinates, because an honest blank beats a confident pin in the wrong
// country.

import { distanceKmBetween } from '../domain/geo';
import { place, type LatLon, type Place } from '../domain/model';
import { airportPlace, findAirport } from './airports';
import { citiesNamed, type City } from './cities';

export type PlaceMatch = 'STATION' | 'CITY' | 'TYPED';

export interface ResolvedPlace {
  place: Place;
  match: PlaceMatch;
}

/** "London, United Kingdom", "LHR · Heathrow", or the words as typed. */
export function resolvedLabel(r: ResolvedPlace): string {
  switch (r.match) {
    case 'STATION':
      return `${r.place.code} · ${r.place.name}`;
    case 'CITY':
      return [r.place.city, r.place.country].filter((s) => s.trim() !== '').join(', ');
    case 'TYPED':
      return r.place.name;
  }
}

/** Within this, a same-named city is assumed to be the one meant. */
const NEARBY_KM = 300;

const LETTER = /^\p{L}$/u;

/** The best reading of [text]; [fallbackZoneId] is used when nothing matches. */
export function resolvePlace(text: string, near: LatLon | null, fallbackZoneId: string): ResolvedPlace {
  return placeCandidates(text, near, fallbackZoneId)[0]!;
}

/** Every reading worth offering, best first, always ending with the text as typed. */
export function placeCandidates(
  text: string,
  near: LatLon | null,
  fallbackZoneId: string,
  limit = 4
): ResolvedPlace[] {
  const query = text.trim();
  const typed: ResolvedPlace = {
    place: place({ name: query || 'Unnamed place', timeZoneId: fallbackZoneId }),
    match: 'TYPED'
  };
  if (query === '') return [typed];

  if (query.length === 3 && [...query].every((c) => LETTER.test(c))) {
    const station = findAirport(query);
    if (station) return [{ place: airportPlace(station), match: 'STATION' }, typed];
  }

  // The whole text first, then each part from the end — addresses run from
  // the specific to the general, and the city is usually last.
  const parts = [
    ...new Set(
      [
        query,
        ...query
          .split(',')
          .map((p) => p.trim())
          .reverse()
      ].filter((p) => p !== '')
    )
  ];
  let cities: City[] = [];
  for (const part of parts) {
    cities = citiesNamed(part);
    if (cities.length > 0) break;
  }
  return [
    ...rank(cities, near)
      .slice(0, limit)
      .map((c) => ({ place: c.toPlace(query), match: 'CITY' as const })),
    typed
  ];
}

function rank(cities: City[], near: LatLon | null): City[] {
  if (near === null || cities.length < 2) return cities;
  // A same-named city close to where the trip is beats a bigger one far away.
  const close = cities.filter((c) => distanceKmBetween(near, c.position) <= NEARBY_KM);
  const far = cities.filter((c) => distanceKmBetween(near, c.position) > NEARBY_KM);
  close.sort((a, b) => distanceKmBetween(near, a.position) - distanceKmBetween(near, b.position));
  return [...close, ...far];
}
