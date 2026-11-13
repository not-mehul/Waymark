import { describe, expect, it } from 'vitest';
import { fingerprint, mergeTrips, type SyncBase } from '../../src/share/sync-merge';
import {
  archiveOf,
  bundlesOf,
  decodeArchive,
  encodeArchive,
  type TripBundle
} from '../../src/share/trip-archive';
import { date, sample } from '../helpers';

const a: TripBundle = { ...sample(date(2026, 5, 14)), costs: null, documents: [], checklist: [] };
const b: TripBundle = {
  ...sample(date(2026, 9, 1)),
  trip: { ...sample(date(2026, 9, 1)).trip, id: 'trip-b', name: 'Autumn' },
  costs: null,
  documents: [],
  checklist: []
};
const renamed = (t: TripBundle, name: string): TripBundle => ({ ...t, trip: { ...t.trip, name } });
const baseOf = (...trips: TripBundle[]): SyncBase =>
  Object.fromEntries(trips.map((t) => [t.trip.id, fingerprint(t)]));
const throughFile = (t: TripBundle) => bundlesOf(decodeArchive(encodeArchive(archiveOf([t]))))[0]!;

describe('mergeTrips', () => {
  it('gives a trip the same fingerprint after a round trip through the file', () => {
    expect(fingerprint(throughFile(a))).toBe(fingerprint(a));
  });

  it('takes a new trip from either side on the first sync', () => {
    const merge = mergeTrips([a], [b], {});
    expect(merge.incoming.map((t) => t.trip.id)).toEqual(['trip-b']);
    expect(merge.merged).toHaveLength(2);
    expect(merge.removed).toEqual([]);
  });

  it('takes the side that changed', () => {
    const base = baseOf(a);
    expect(mergeTrips([a], [renamed(a, 'There')], base).incoming[0]!.trip.name).toBe('There');
    const mine = mergeTrips([renamed(a, 'Here')], [a], base);
    expect(mine.incoming).toEqual([]);
    expect(mine.merged[0]!.trip.name).toBe('Here');
  });

  it('keeps this phone’s copy when both changed, and says so', () => {
    const merge = mergeTrips([renamed(a, 'Here')], [renamed(a, 'There')], baseOf(a));
    expect(merge.conflicts).toEqual([a.trip.id]);
    expect(merge.merged[0]!.trip.name).toBe('Here');
  });

  it('keeps a deletion on either side when the other did not change', () => {
    const base = baseOf(a, b);
    expect(mergeTrips([a, b], [a], base).removed).toEqual(['trip-b']);
    const deletedHere = mergeTrips([a], [a, b], base);
    expect(deletedHere.incoming).toEqual([]);
    expect(deletedHere.merged.map((t) => t.trip.id)).toEqual([a.trip.id]);
  });

  it('brings a deleted trip back when the other side changed it since', () => {
    const base = baseOf(a, b);
    expect(mergeTrips([a, renamed(b, 'Edited')], [a], base).removed).toEqual([]);
    expect(mergeTrips([a], [a, renamed(b, 'Edited')], base).incoming[0]!.trip.name).toBe('Edited');
  });
});
