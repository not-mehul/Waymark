// The rules the repository keeps, against a real IndexedDB (fake-indexeddb).
import 'fake-indexeddb/auto';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { openDatabase, type WaymarkDb } from '../../src/db/database';
import { TripRepository } from '../../src/db/trips';
import { copyCommon, type Segment } from '../../src/domain/model';
import type { TripBundle } from '../../src/share/trip-archive';
import { flightsIn, sample as buildSample } from '../helpers';

const sample = buildSample();
const bundle: TripBundle = { ...sample, costs: null };
let db: WaymarkDb;
let trips: TripRepository;
let opened = 0;

beforeEach(async () => {
  db = openDatabase(`trip-repository-${opened++}`);
  trips = new TripRepository(db);
  await trips.importBundles([bundle]);
});

afterEach(async () => {
  await db.delete();
});

const dossier = async () => (await trips.dossier(sample.trip.id))!;
const copyAsOnly = (segment: Segment, travelerId: string, id: string) =>
  copyCommon(segment, { id, travelerIds: [travelerId] });

describe('TripRepository', () => {
  it('an imported trip reads back whole', async () => {
    const back = await trips.bundles();
    expect(back).toHaveLength(1);
    expect(back[0]!.trip).toEqual(sample.trip);
    expect(back[0]!.travelers.map((t) => t.id)).toEqual(sample.travelers.map((t) => t.id));
    expect(new Set(back[0]!.segments.map((s) => s.id))).toEqual(new Set(sample.segments.map((s) => s.id)));
    expect(new Set(back[0]!.ideas.map((i) => i.id))).toEqual(new Set(sample.ideas.map((i) => i.id)));
  });

  it('importing the same trip again replaces it rather than adding a second', async () => {
    await trips.importBundles([{ ...bundle, segments: bundle.segments.slice(1) }]);
    const back = await trips.bundles();
    expect(back).toHaveLength(1);
    expect(back[0]!.segments).toHaveLength(sample.segments.length - 1);
    expect(await trips.existingTripIds([sample.trip.id, 'other'])).toEqual(new Set([sample.trip.id]));
  });

  it('a booking with everyone ticked is stored as everyone', async () => {
    const everyone = sample.travelers.map((t) => t.id);
    const flight = flightsIn(sample.segments)[0]!;
    await trips.saveSegment({ ...flight, travelerIds: everyone });
    expect((await dossier()).segments.find((s) => s.id === flight.id)!.travelerIds).toEqual([]);
  });

  it("a booking saved outside the trip's dates widens the trip", async () => {
    const flight = flightsIn(sample.segments)[0]!;
    const later = {
      ...flight,
      id: 'seg-late',
      startEpochMillis: sample.trip.endEpochMillis + 86_400_000,
      endEpochMillis: sample.trip.endEpochMillis + 2 * 86_400_000
    };
    await trips.saveSegment(later);
    expect((await dossier()).trip.endEpochMillis).toBe(later.endEpochMillis);
  });

  it('removing a traveler takes their own bookings and keeps the shared ones', async () => {
    const leaving = sample.travelers[0]!;
    const stays = sample.travelers.slice(1).map((t) => t.id);
    await trips.saveSegment(copyAsOnly(sample.segments[0]!, leaving.id, 'seg-alone'));
    await trips.removeTraveler(sample.trip.id, leaving.id);

    const after = await dossier();
    expect(after.segments.some((s) => s.id === 'seg-alone')).toBe(false);
    expect(after.party.travelers.some((t) => t.id === leaving.id)).toBe(false);
    for (const s of after.segments) {
      expect(s.travelerIds.length === 0 || s.travelerIds.every((id) => stays.includes(id))).toBe(true);
    }
    // On no other trip, so the profile goes too.
    expect(await db.travelers.get(leaving.id)).toBeUndefined();
  });

  it('a profile on another trip outlives being taken off this one', async () => {
    const traveler = sample.travelers[0]!;
    const second = await trips.createTrip('Lisbon', 'Lisbon', 0, 86_400_000, 'Europe/Lisbon');
    await trips.addExistingTraveler(second, traveler.id);
    await trips.removeTraveler(sample.trip.id, traveler.id);

    expect((await db.travelers.get(traveler.id))?.fullName).toBe(traveler.fullName);
    expect((await trips.tripCounts()).get(traveler.id)).toBe(1);
    expect(second).not.toBe('');
  });

  it('deleting a trip removes everything on it', async () => {
    await trips.deleteTrip(sample.trip.id);
    expect(await trips.bundles()).toEqual([]);
    expect(await db.segments.count()).toBe(0);
    expect(await db.travelers.count()).toBe(0);
  });

  it('an archived trip stays quiet', async () => {
    await trips.setArchived(sample.trip.id, true);
    const now = sample.trip.startEpochMillis - 86_400_000;
    expect(await trips.reminderItinerary(now)).toEqual([]);
    expect(await trips.nextAcrossTrips(now)).toEqual([]);
  });

  it('the widget reads the next few bookings soonest first', async () => {
    const now = sample.trip.startEpochMillis - 86_400_000;
    const next = await trips.nextAcrossTrips(now, 3);
    expect(next).toHaveLength(3);
    const starts = next.map(([, s]) => s.startEpochMillis);
    expect(starts).toEqual([...starts].sort((a, b) => a - b));
    expect(next.every(([t]) => t.id === sample.trip.id)).toBe(true);
  });
});
