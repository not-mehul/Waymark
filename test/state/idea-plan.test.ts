import { beforeAll, describe, expect, it } from 'vitest';
import { AIRPORTS_ASSET, installAirports, parseAirportDirectory } from '../../src/catalog/airports';
import { CITIES_ASSET, installCities, parseCityDirectory } from '../../src/catalog/cities';
import { buildSampleItinerary } from '../../src/catalog/sample-itinerary';
import { idea, place, tripStartDate, type TripDossier } from '../../src/domain/model';
import { localDate, zonedOf, localTime } from '../../src/domain/time';
import {
  defaultScheduleDate,
  ideaFields,
  ideaZone,
  newIdeaDraft,
  tripCities,
  tripDays
} from '../../src/state/idea-plan';
import { bundledLines } from '../helpers';

const bundle = buildSampleItinerary(localDate(2026, 5, 14));
const dossier: TripDossier = {
  trip: bundle.trip,
  party: { tripId: bundle.trip.id, travelers: bundle.travelers },
  segments: bundle.segments
};
const base = { id: 'i1', tripId: dossier.trip.id, title: 'Musée Rodin', kind: 'SEE' as const };

beforeAll(() => {
  installAirports(parseAirportDirectory(bundledLines(AIRPORTS_ASSET)));
  installCities(parseCityDirectory(bundledLines(CITIES_ASSET)));
});

describe('the trip, as the Ideas tab reads it', () => {
  it('lists the cities in the order they are reached, and every day of the trip', () => {
    const cities = tripCities(dossier);
    expect(cities.indexOf('London')).toBeLessThan(cities.indexOf('Paris'));
    expect(new Set(cities).size).toBe(cities.length);
    const days = tripDays(dossier);
    expect(days[0]).toEqual(tripStartDate(dossier.trip));
    expect(days.length).toBeGreaterThan(1);
  });
});

describe('ideaFields', () => {
  it('keeps a typed address unpinned, on its city’s clock', () => {
    const fields = ideaFields(
      { ...newIdeaDraft(['Paris']), title: ' Rodin ', address: '77 Rue de Varenne', minutes: '90 min' },
      dossier
    );
    expect(fields.title).toBe('Rodin');
    expect(fields.city).toBe('Paris');
    expect(fields.typicalMinutes).toBe(90);
    expect(fields.place).toMatchObject({
      name: 'Rodin',
      address: '77 Rue de Varenne',
      latitude: null,
      timeZoneId: 'Europe/Paris'
    });
  });

  it('stores no place without an address, and no length that is not a number', () => {
    const fields = ideaFields({ ...newIdeaDraft([]), title: 'Market', minutes: 'soon' }, dossier);
    expect(fields.place).toBeNull();
    expect(fields.typicalMinutes).toBeNull();
    expect(fields.city).toBe(tripCities(dossier)[0]);
  });
});

describe('ideaZone', () => {
  const day = localDate(2026, 5, 18);

  it('prefers the idea’s own place, then a booked place in the same city', () => {
    const own = idea({ ...base, city: 'Paris', place: place({ name: 'x', timeZoneId: 'Asia/Tokyo' }) });
    expect(ideaZone(own, dossier, day)).toBe('Asia/Tokyo');
    expect(ideaZone(idea({ ...base, city: 'paris' }), dossier, day)).toBe('Europe/Paris');
  });

  it('looks a city up by name, and otherwise uses wherever the trip is that day', () => {
    expect(ideaZone(idea({ ...base, city: 'Rome' }), dossier, day)).toBe('Europe/Rome');
    const nowhere = ideaZone(idea({ ...base, city: 'Qwertyville' }), dossier, day);
    expect(nowhere).toBe(ideaZone(idea({ ...base, city: '' }), dossier, day));
  });
});

describe('defaultScheduleDate', () => {
  const start = tripStartDate(dossier.trip);
  const millisOn = (d: typeof start) => zonedOf(d, localTime(9), dossier.trip.homeZoneId).epochMilliseconds;

  it('uses the pencilled day, then the first day of the trip still to come', () => {
    const pencilled = idea({ ...base, city: 'Paris', plannedDate: start.add({ days: 2 }) });
    expect(defaultScheduleDate(pencilled, dossier, millisOn(start))).toEqual(start.add({ days: 2 }));
    const plain = idea({ ...base, city: 'Paris' });
    expect(defaultScheduleDate(plain, dossier, millisOn(start.subtract({ days: 5 })))).toEqual(start);
    expect(defaultScheduleDate(plain, dossier, millisOn(start.add({ days: 1 })))).toEqual(
      start.add({ days: 1 })
    );
  });
});
