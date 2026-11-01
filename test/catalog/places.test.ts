import { beforeAll, describe, expect, it } from 'vitest';
import { CITIES_ASSET, citiesLoaded, installCities, parseCityDirectory } from '../../src/catalog/cities';
import { countryForName } from '../../src/catalog/countries';
import { placeCandidates, resolvePlace } from '../../src/catalog/places';
import { hasCoordinates } from '../../src/domain/model';
import { bundledLines } from '../helpers';

const newYork = { latitude: 40.71, longitude: -74.0 };
const wichita = { latitude: 37.69, longitude: -97.34 };

beforeAll(() => {
  if (!citiesLoaded()) installCities(parseCityDirectory(bundledLines(CITIES_ASSET)));
});

describe('Places', () => {
  it('the gazetteer is the whole world, with a zone on every city', () => {
    const parsed = parseCityDirectory(bundledLines(CITIES_ASSET));
    expect(parsed.length).toBeGreaterThan(30_000);
    expect(parsed.every((c) => c.timeZoneId.includes('/') || c.timeZoneId === 'UTC')).toBe(true);
    // Largest first, so an ambiguous name means the city most people mean.
    expect(parsed[0]!.population).toBeGreaterThan(parsed[parsed.length - 1]!.population);
  });

  it("a city resolves to its own clock, not the trip's", () => {
    const paris = resolvePlace('Paris', null, 'America/Los_Angeles');
    expect(paris.match).toBe('CITY');
    expect(paris.place.timeZoneId).toBe('Europe/Paris');
    expect(paris.place.country).toBe('France');
    expect(resolvePlace('Kyoto', null, 'UTC').place.timeZoneId).toBe('Asia/Tokyo');
  });

  it('a city named in an address is found, and the address is kept as the name', () => {
    const stay = resolvePlace('Bloomsbury, London', null, 'UTC');
    expect(stay.place.city).toBe('London');
    expect(stay.place.name).toBe('Bloomsbury, London');
    expect(stay.place.timeZoneId).toBe('Europe/London');
  });

  it('a shared name goes to the city nearest the trip, or the largest without one', () => {
    expect(resolvePlace('Manhattan', null, 'UTC').place.timeZoneId).toBe('America/New_York');
    expect(resolvePlace('Manhattan', newYork, 'UTC').place.timeZoneId).toBe('America/New_York');
    expect(resolvePlace('Manhattan', wichita, 'UTC').place.timeZoneId).toBe('America/Chicago');
  });

  it('english names and spelling variants find the city', () => {
    expect(countryForName(resolvePlace('Bruges', null, 'UTC').place.country)?.code).toBe('BE');
    expect(resolvePlace('st. petersburg', null, 'UTC').place.timeZoneId).toBe('Europe/Moscow');
    expect(resolvePlace('ZURICH', null, 'UTC').place.timeZoneId).toBe('Europe/Zurich');
  });

  it('a station code is a station', () => {
    const heathrow = resolvePlace('lhr', null, 'UTC');
    expect(heathrow.match).toBe('STATION');
    expect(heathrow.place.code).toBe('LHR');
  });

  it("nothing is matched by a fragment, and unmatched text keeps the caller's zone", () => {
    const fragment = resolvePlace('Manhatt', null, 'Europe/Rome');
    expect(fragment.match).toBe('TYPED');
    expect(fragment.place.timeZoneId).toBe('Europe/Rome');
    expect(hasCoordinates(fragment.place)).toBe(false);

    const bakery = resolvePlace('the bakery on the corner', null, 'Asia/Tokyo');
    expect(bakery.place.name).toBe('the bakery on the corner');
    expect(bakery.place.timeZoneId).toBe('Asia/Tokyo');
  });

  it('the typed reading is always offered last', () => {
    const options = placeCandidates('Paris', null, 'UTC');
    expect(options.length).toBeGreaterThanOrEqual(3);
    expect(options[options.length - 1]!.match).toBe('TYPED');
    expect(countryForName(options[0]!.place.country)?.code).toBe('FR');
  });
});
