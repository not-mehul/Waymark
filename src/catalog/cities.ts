// Every city of fifteen thousand people or more, with the time zone it keeps
// (GeoNames, CC BY 4.0). Matching is exact — on the name, or on one of the
// names the city is known by — and never a substring, so "Manhattan" is not
// an airfield in Kansas.

import { place, type LatLon, type Place } from '../domain/model';
import { toDoubleOrNull, toIntOrNull } from './airports';
import { countryForCode } from './countries';

export class City {
  readonly key: string;

  constructor(
    readonly name: string,
    readonly latitude: number,
    readonly longitude: number,
    /** ISO 3166-1 alpha-2. */
    readonly countryCode: string,
    /** In thousands; only used to rank cities that share a name. */
    readonly population: number,
    readonly timeZoneId: string,
    private readonly alternates: string
  ) {
    this.key = normaliseCity(name);
  }

  get country(): string {
    return countryForCode(this.countryCode)?.name ?? this.countryCode;
  }

  get position(): LatLon {
    return { latitude: this.latitude, longitude: this.longitude };
  }

  knownAs(needle: string): boolean {
    return this.alternates !== '' && this.alternates.split(';').some((a) => normaliseCity(a) === needle);
  }

  /** A place in this city — [label] is what the traveler typed, kept as the name. */
  toPlace(label: string = this.name): Place {
    return place({
      name: label,
      city: this.name,
      country: this.country,
      latitude: this.latitude,
      longitude: this.longitude,
      timeZoneId: this.timeZoneId
    });
  }

  toString(): string {
    return `${this.name}, ${this.countryCode}`;
  }
}

let directory: readonly City[] = [];

export function citiesLoaded(): boolean {
  return directory.length > 0;
}

/** Install the parsed gazetteer. Rows arrive largest first. */
export function installCities(parsed: readonly City[]): void {
  directory = parsed;
}

/** Every city called exactly [name]: own-name matches first, then known-as, each largest first. */
export function citiesNamed(name: string): City[] {
  const needle = normaliseCity(name);
  if (needle === '') return [];
  const own = directory.filter((c) => c.key === needle);
  const known = directory.filter((c) => c.key !== needle && c.knownAs(needle));
  return [...own, ...known];
}

/**
 * Case, accents, punctuation and the usual abbreviations do not decide a
 * match: "st. petersburg", "Saint-Petersburg" and "SAINT PETERSBURG" are one.
 */
export function normaliseCity(value: string): string {
  let folded = value
    .trim()
    .toLowerCase()
    .normalize('NFD')
    .replace(/\p{M}+/gu, '')
    .replace(/[^a-z0-9]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
  if (folded.startsWith('the ')) folded = folded.slice(4);
  return folded.replace(/^st /, 'saint ').replace(/^ste /, 'sainte ');
}

export const CITIES_ASSET = 'cities.txt';

/** Parses the bundled gazetteer; the same zone-table-then-rows shape as the stations. */
export function parseCityDirectory(lines: Iterable<string>): City[] {
  const zones: string[] = [];
  const cities: City[] = [];
  let readingZones = true;
  for (const line of lines) {
    if (readingZones) {
      if (line.trim() === '') readingZones = false;
      else zones.push(line.trim());
      continue;
    }
    if (line.trim() === '') continue;
    const city = parseCityLine(line, zones);
    if (city) cities.push(city);
  }
  return cities;
}

function parseCityLine(line: string, zones: readonly string[]): City | null {
  const parts = line.split('|');
  if (parts.length !== 7) return null;
  const latitude = toDoubleOrNull(parts[1]!);
  const longitude = toDoubleOrNull(parts[2]!);
  if (latitude === null || longitude === null) return null;
  if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) return null;
  const index = toIntOrNull(parts[5]!);
  if (index === null) return null;
  const zone = zones[index];
  if (zone === undefined) return null;
  const name = parts[0]!.trim();
  if (name === '') return null;
  return new City(
    name,
    latitude,
    longitude,
    parts[3]!.trim().toUpperCase(),
    toIntOrNull(parts[4]!) ?? 0,
    zone,
    parts[6]!.trim()
  );
}
