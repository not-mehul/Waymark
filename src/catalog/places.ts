// What a typed place means: a station, a place in the gazetteer, or just the
// words. Every step is an exact match; text that matches nothing stays as
// typed, with no coordinates, because an honest blank beats a confident pin in
// the wrong country.

import { foldPlaceName } from 'dusk-dawn/places';
import { distanceKmBetween } from '../domain/geo';
import { type LatLon, place, type Place } from '../domain/model';
import { airportPlace, findAirport } from './airports';
import { countryForCode, countryForName } from './countries';
import { placesNamed, positionOfWorldPlace, worldPlaceToPlace, type WorldPlace } from './gazetteer';

/** STATION: an airport code. CITY: a town or city. PLACE: anything else the gazetteer knows. */
export type PlaceMatch = 'STATION' | 'CITY' | 'PLACE' | 'TYPED';

export interface ResolvedPlace {
  place: Place;
  match: PlaceMatch;
  /** The gazetteer's own name for a PLACE ("Eiffel Tower" for "the eiffel tower"). */
  known?: string;
}

/** "London, United Kingdom", "Eiffel Tower, Paris, France", "LHR · Heathrow", or the words as typed. */
export function resolvedLabel(r: ResolvedPlace): string {
  switch (r.match) {
    case 'STATION':
      return `${r.place.code} · ${r.place.name}`;
    case 'CITY':
      return joined([r.place.city, r.place.country]);
    case 'PLACE': {
      const known = r.known ?? r.place.name;
      return joined([known, r.place.city === known ? '' : r.place.city, r.place.country]);
    }
    case 'TYPED':
      return r.place.name;
  }
}

const joined = (parts: string[]) => parts.filter((s) => s.trim() !== '').join(', ');

/** Within this, a same-named place is assumed to be the one meant. */
const NEARBY_KM = 300;
/** "Eiffel Tower, Paris": the specific part must lie this close to the general one. */
const WITHIN_KM = 50;

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

  return [
    ...rank(matchesFor(query), near)
      .slice(0, limit)
      .map((p) => ({
        place: worldPlaceToPlace(p, query),
        match: p.kind === 'city' ? ('CITY' as const) : ('PLACE' as const),
        known: p.name
      })),
    typed
  ];
}

/**
 * The whole text first. Failing that, an address runs from the specific to
 * the general: the most specific part that names somewhere inside the parts
 * after it ("Bloomsbury, London", "Paris, Texas"), else the most general part
 * that names anywhere ("Hilton, London" is London, not a village called
 * Hilton). A trailing country narrows every part to that country.
 */
function matchesFor(query: string): WorldPlace[] {
  const whole = placesNamed(query);
  if (whole.length > 0) return whole;

  const parts = query
    .split(',')
    .map((p) => p.trim())
    .filter((p) => p !== '');
  const country = parts.length > 1 ? countryNamed(parts[parts.length - 1]!) : null;
  if (country) parts.pop();
  const named = (part: string) => placesNamed(part).filter((p) => country === null || p.country === country);

  // The most general part that names somewhere, reading from the end.
  let general: WorldPlace[] = [];
  let at = parts.length;
  while (general.length === 0 && --at >= 0) general = named(parts[at]!);
  const anchor = general[0];
  if (anchor === undefined) return [];

  for (let i = 0; i < at; i++) {
    const inside = named(parts[i]!).filter((p) => within(p, anchor));
    if (inside.length > 0) return inside;
  }
  return general;
}

/** What people write for a country instead of its name. */
const COUNTRY_ALIASES: Record<string, string> = {
  usa: 'US',
  'u s a': 'US',
  'united states of america': 'US',
  america: 'US',
  uk: 'GB',
  'u k': 'GB',
  britain: 'GB',
  'great britain': 'GB',
  england: 'GB',
  scotland: 'GB',
  wales: 'GB',
  'northern ireland': 'GB',
  uae: 'AE',
  holland: 'NL'
};

function countryNamed(text: string): string | null {
  const alias = COUNTRY_ALIASES[foldPlaceName(text)];
  if (alias) return alias;
  return (countryForName(text) ?? (text.length === 2 ? countryForCode(text) : null))?.code ?? null;
}

/** Whether [p] lies inside [area]: its region, or within reach of a town or landmark. */
function within(p: WorldPlace, area: WorldPlace): boolean {
  if (p.country !== area.country) return false;
  if (area.kind === 'region') return p.region === area.name;
  return distanceKmBetween(positionOfWorldPlace(p), positionOfWorldPlace(area)) <= WITHIN_KM;
}

function rank(places: WorldPlace[], near: LatLon | null): WorldPlace[] {
  if (near === null || places.length < 2) return places;
  // A same-named place close to where the trip is beats a bigger one far away.
  const km = (p: WorldPlace) => distanceKmBetween(near, positionOfWorldPlace(p));
  const close = places.filter((p) => km(p) <= NEARBY_KM);
  const far = places.filter((p) => km(p) > NEARBY_KM);
  close.sort((a, b) => km(a) - km(b));
  return [...close, ...far];
}
