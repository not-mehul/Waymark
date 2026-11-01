import { describe, expect, it } from 'vitest';
import { DESTINATION_MINUTES, destinations, primaryStay, stays } from '../../src/domain/time-at-place';
import { HOUR, dossierOf, flightsIn, sample } from '../helpers';

const bundle = sample();
const dossier = dossierOf(bundle);

describe('TimeAtPlace', () => {
  it('the worked example is a trip to London and Paris, in that order', () => {
    expect(destinations(dossier).map((s) => s.place.city)).toEqual(['London', 'Paris']);
  });

  it('stays are ranked by time and each is at least a day', () => {
    const all = stays(dossier);
    expect(all.length).toBeGreaterThan(0);
    expect(all.map((s) => s.minutes)).toEqual([...all.map((s) => s.minutes)].sort((a, b) => b - a));
    for (const d of destinations(dossier)) {
      expect(d.minutes, `${d.place.city} is under a day`).toBeGreaterThanOrEqual(DESTINATION_MINUTES);
    }
  });

  // Picking the last arrival made a trip to Japan through Doha a trip to Qatar;
  // picking every arrival gave a two-hour connection the same billing as the week after it.
  it('a connection is not a destination', () => {
    const flight = flightsIn(bundle.segments)[0]!;
    const start = flight.startEpochMillis;

    const out = { ...flight, id: 'leg-1', startEpochMillis: start, endEpochMillis: start + 6 * HOUR };
    const onward = {
      ...flight,
      id: 'leg-2',
      origin: flight.destination,
      destination: flight.origin,
      // Two hours on the ground at the first arrival, then a week away.
      startEpochMillis: start + 8 * HOUR,
      endEpochMillis: start + 14 * HOUR
    };
    const home = {
      ...flight,
      id: 'leg-3',
      origin: flight.origin,
      destination: flight.destination,
      startEpochMillis: start + 14 * HOUR + 7 * 24 * HOUR,
      endEpochMillis: start + 20 * HOUR + 7 * 24 * HOUR
    };

    const trip = { ...dossier, segments: [out, onward, home] };
    // Both places are visited; only the week counts as a destination.
    expect(stays(trip)).toHaveLength(2);
    const found = destinations(trip);
    expect(found).toHaveLength(1);
    expect(found[0]!.place.city).toBe(flight.origin.city);
  });

  it('a trip with one movement has nowhere to have stayed', () => {
    const single = { ...dossier, segments: bundle.segments.slice(0, 1) };
    expect(stays(single)).toEqual([]);
    expect(primaryStay(single)).toBeNull();
    expect(destinations(single)).toEqual([]);
  });

  it('a trip shorter than a day still has a destination', () => {
    const flight = flightsIn(bundle.segments)[0]!;
    const start = flight.startEpochMillis;
    const out = { ...flight, id: 'a', startEpochMillis: start, endEpochMillis: start + HOUR };
    const back = {
      ...flight,
      id: 'b',
      origin: flight.destination,
      destination: flight.origin,
      startEpochMillis: start + 6 * HOUR,
      endEpochMillis: start + 7 * HOUR
    };
    expect(destinations({ ...dossier, segments: [out, back] })).toHaveLength(1);
  });

  it("filtering to one traveler measures that traveler's own days", () => {
    const julian = bundle.travelers.find((t) => t.fullName.startsWith('Julian'))!;
    const his = stays(dossier, julian.id);
    expect(his.length).toBeGreaterThan(0);
    expect(his.every((s) => s.minutes > 0)).toBe(true);
  });
});
