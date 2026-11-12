import { beforeAll, describe, expect, it } from 'vitest';
import { countryForName } from '../../src/catalog/countries';
import { placeCount } from '../../src/catalog/gazetteer';
import { placeCandidates, resolvedLabel, resolvePlace } from '../../src/catalog/places';
import { hasCoordinates } from '../../src/domain/model';
import { installWorldPlaces } from '../helpers';

const newYork = { latitude: 40.71, longitude: -74.0 };
const wichita = { latitude: 37.69, longitude: -97.34 };

beforeAll(installWorldPlaces);

describe('Places', () => {
  it('the gazetteer is the whole world, down to small towns', () => {
    expect(placeCount()).toBeGreaterThan(300_000);
    // A town of a few thousand people, which the old list of 15,000+ cities missed.
    const zermatt = resolvePlace('Zermatt', null, 'UTC');
    expect(zermatt.match).toBe('CITY');
    expect(zermatt.place.timeZoneId).toBe('Europe/Zurich');
  });

  it('a landmark is pinned where it stands and filed under its city', () => {
    const tower = resolvePlace('Eiffel Tower', null, 'America/New_York');
    expect(tower.match).toBe('PLACE');
    expect(tower.place.city).toBe('Paris');
    expect(tower.place.timeZoneId).toBe('Europe/Paris');
    expect(tower.place.latitude).toBeCloseTo(48.858, 2);
    expect(tower.place.longitude).toBeCloseTo(2.294, 2);
    expect(resolvedLabel(tower)).toBe('Eiffel Tower, Paris, France');
  });

  it('islands, regions and old names are places too', () => {
    expect(resolvePlace('Santorini', null, 'UTC').place.timeZoneId).toBe('Europe/Athens');
    expect(resolvedLabel(resolvePlace('Tuscany', null, 'UTC'))).toBe('Tuscany, Italy');
    expect(resolvePlace('Bombay', null, 'UTC').place.city).toBe('Mumbai');
  });

  it('an address is read from the specific to the general, inside what follows', () => {
    // A specific part counts only inside the general one.
    expect(resolvePlace('Paris, Texas', null, 'UTC').place.timeZoneId).toBe('America/Chicago');
    expect(resolvePlace('Hilton, London', null, 'UTC').place.city).toBe('London');
    // A trailing country narrows the rest to it.
    expect(resolvePlace('Florence, USA', null, 'UTC').place.country).toBe(
      countryForName('United States')?.name
    );
    expect(resolvePlace('Rue de Rivoli 5, Paris, France', null, 'UTC').place.timeZoneId).toBe('Europe/Paris');
  });

  it("a city resolves to its own clock, not the trip's", () => {
    const paris = resolvePlace('Paris', null, 'America/Los_Angeles');
    expect(paris.match).toBe('CITY');
    expect(paris.place.timeZoneId).toBe('Europe/Paris');
    expect(paris.place.country).toBe('France');
    expect(resolvePlace('Kyoto', null, 'UTC').place.timeZoneId).toBe('Asia/Tokyo');
  });

  it('a place named in an address is found, and the address is kept as the name', () => {
    const stay = resolvePlace('Bloomsbury, London', null, 'UTC');
    expect(stay.match).toBe('PLACE');
    expect(stay.place.city).toBe('London');
    expect(stay.place.name).toBe('Bloomsbury, London');
    expect(stay.place.timeZoneId).toBe('Europe/London');
    // Bloomsbury itself, not the middle of London.
    expect(stay.place.longitude).toBeCloseTo(-0.12, 1);
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
