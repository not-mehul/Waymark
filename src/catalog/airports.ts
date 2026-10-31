// Station reference data, bundled so resolving a code works with the radio
// off. Two tiers: the hand-checked core list (real terminals, the names a
// traveler recognises) and the generated world directory, installed from
// airports.txt. The core always wins a code collision and is always present.

import { place, type Place } from '../domain/model';
import { CORE_AIRPORT_ROWS } from './airports-data';
import { countryForCode } from './countries';

export interface Airport {
  code: string;
  name: string;
  city: string;
  country: string;
  latitude: number;
  longitude: number;
  timeZoneId: string;
  terminals: readonly string[];
  /** Lower-cased once, rather than for nine thousand rows on every keystroke. */
  readonly codeKey: string;
  readonly cityKey: string;
  readonly nameKey: string;
}

export function airport(
  fields: Omit<Airport, 'codeKey' | 'cityKey' | 'nameKey' | 'terminals'> & { terminals?: readonly string[] }
): Airport {
  return {
    terminals: [],
    ...fields,
    codeKey: fields.code.toLowerCase(),
    cityKey: fields.city.toLowerCase(),
    nameKey: fields.name.toLowerCase()
  };
}

export function airportPlace(a: Airport): Place {
  return place({
    name: a.name,
    code: a.code,
    city: a.city,
    country: a.country,
    latitude: a.latitude,
    longitude: a.longitude,
    timeZoneId: a.timeZoneId
  });
}

const all: readonly Airport[] = CORE_AIRPORT_ROWS.map(
  ([code, name, city, country, latitude, longitude, timeZoneId, terminals]) =>
    airport({ code, name, city, country, latitude, longitude, timeZoneId, terminals })
);
const core = new Map(all.map((a) => [a.code, a]));

/** The world directory, once something has loaded it. */
let directory = new Map<string, Airport>();

/** Install the parsed directory. Codes already in the core list are kept. */
export function installAirports(airports: readonly Airport[]): void {
  directory = new Map(airports.filter((a) => !core.has(a.code)).map((a) => [a.code, a]));
}

export function airportDirectoryLoaded(): boolean {
  return directory.size > 0;
}

export function findAirport(code: string | null | undefined): Airport | null {
  if (code == null) return null;
  const needle = code.trim().toUpperCase();
  return core.get(needle) ?? directory.get(needle) ?? null;
}

export function airportPlaceFor(code: string): Place {
  const found = findAirport(code);
  return found ? airportPlace(found) : place({ name: code.toUpperCase(), code: code.toUpperCase() });
}

/** The core list only — the set with terminals, used for the worked example. */
export function coreAirports(): readonly Airport[] {
  return all;
}

export function* everyAirport(): Generator<Airport> {
  yield* all;
  yield* directory.values();
}

function score(a: Airport, needle: string): number {
  if (a.codeKey === needle) return 0;
  if (a.codeKey.startsWith(needle)) return 1;
  if (a.cityKey === needle) return 2;
  if (a.cityKey.startsWith(needle)) return 3;
  if (a.nameKey.startsWith(needle)) return 4;
  if (a.cityKey.includes(needle)) return 5;
  if (a.nameKey.includes(needle)) return 6;
  return Number.MAX_SAFE_INTEGER;
}

/**
 * Type-ahead over code, city and name: an exact code beats a code prefix
 * beats a city prefix, and a core station beats a directory one at the same
 * rank, so typing "LON" surfaces Heathrow above a Kentucky airstrip.
 */
export function searchAirports(query: string, limit = 8): Airport[] {
  const needle = query.trim().toLowerCase();
  if (needle === '') return [];
  const scored: [Airport, number][] = [];
  for (const a of everyAirport()) {
    const s = score(a, needle);
    if (s !== Number.MAX_SAFE_INTEGER) scored.push([a, s]);
  }
  scored.sort(
    ([a, sa], [b, sb]) =>
      sa - sb ||
      (core.has(a.code) ? 0 : 1) - (core.has(b.code) ? 0 : 1) ||
      a.city.length - b.city.length ||
      (a.code < b.code ? -1 : a.code > b.code ? 1 : 0)
  );
  return scored.slice(0, limit).map(([a]) => a);
}

// — The bundled directory ———————————————————————————————————————————————

export const AIRPORTS_ASSET = 'airports.txt';

/** "Utirik Airport" → "Utirik": the suffix carries nothing in a list of airports. */
function shortNameOf(name: string): string {
  let trimmed = name;
  for (const suffix of [
    ' International Airport',
    ' Regional Airport',
    ' Municipal Airport',
    ' Airport',
    ' Airstrip',
    ' Airfield',
    ' Airpark',
    ' Air Base',
    ' Seaplane Base',
    ' Heliport'
  ]) {
    if (trimmed.toLowerCase().endsWith(suffix.toLowerCase())) trimmed = trimmed.slice(0, -suffix.length);
  }
  return trimmed.trim() || name;
}

/** Kotlin's String.toDoubleOrNull, for the plain decimals the file holds. */
export function toDoubleOrNull(text: string): number | null {
  const t = text.trim();
  if (!/^[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?$/.test(t)) return null;
  return Number(t);
}

export function toIntOrNull(text: string): number | null {
  return /^[+-]?\d+$/.test(text) ? Number(text) : null;
}

/**
 * Parses the generated world directory: a table of IANA zone names, a blank
 * line, then one airport per line referring to a zone by index. A malformed
 * line is skipped rather than taking the whole directory down with it.
 *
 *     Europe/London
 *
 *     LHR|London Heathrow Airport|London|GB|51.4706|-0.4619|0
 */
export function parseAirportDirectory(lines: Iterable<string>): Airport[] {
  const zones: string[] = [];
  const airports: Airport[] = [];
  let readingZones = true;
  for (const line of lines) {
    if (readingZones) {
      if (line.trim() === '') readingZones = false;
      else zones.push(line.trim());
      continue;
    }
    if (line.trim() === '') continue;
    const parsed = parseAirportLine(line, zones);
    if (parsed) airports.push(parsed);
  }
  return airports;
}

function parseAirportLine(line: string, zones: readonly string[]): Airport | null {
  const parts = line.split('|');
  if (parts.length !== 7) return null;
  const code = parts[0]!.trim().toUpperCase();
  if (code.length !== 3) return null;
  const latitude = toDoubleOrNull(parts[4]!);
  const longitude = toDoubleOrNull(parts[5]!);
  if (latitude === null || longitude === null) return null;
  if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) return null;
  const index = toIntOrNull(parts[6]!.trim());
  if (index === null) return null;
  const zone = zones[index];
  if (zone === undefined) return null;
  const name = parts[1]!.trim();
  // About one row in twelve has no municipality: a remote strip, an island.
  const city = parts[2]!.trim() || shortNameOf(name);
  return airport({
    code,
    name: name || code,
    city,
    // The file carries ISO codes; everything downstream wants a name.
    country: countryForCode(parts[3]!)?.name ?? parts[3]!.trim().toUpperCase(),
    latitude,
    longitude,
    timeZoneId: zone
  });
}
