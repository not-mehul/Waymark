import 'fake-indexeddb/auto';
import { afterEach, describe, expect, it } from 'vitest';
import { openDatabase } from '../../src/db/database';
import { TripRepository } from '../../src/db/trips';
import { runHandover, type HandoverSource } from '../../src/share/handover';
import { archiveOf, encodeArchive } from '../../src/share/trip-archive';
import { sample } from '../helpers';

const bundle = { ...sample(), costs: null };
const db = openDatabase('handover');
const trips = new TripRepository(db);
afterEach(() => db.delete().then(() => db.open()));

function fileSource(text: string | null) {
  const state = { text, removed: false };
  const source: HandoverSource = {
    read: () => Promise.resolve(state.text),
    remove: () => {
      state.removed = true;
      state.text = null;
      return Promise.resolve();
    }
  };
  return { source, state };
}

describe('Handover', () => {
  it("the old app's file is imported whole, and only then deleted", async () => {
    const { source, state } = fileSource(encodeArchive(archiveOf([bundle], 1)));
    const result = await runHandover(source, trips);
    expect(result).toEqual({ outcome: 'imported', trips: 1, bookings: bundle.segments.length });
    expect(state.removed).toBe(true);
    expect((await trips.dossier(bundle.trip.id))?.segments).toHaveLength(bundle.segments.length);
  });

  it('no file means nothing to do', async () => {
    expect(await runHandover(fileSource(null).source, trips)).toEqual({ outcome: 'none' });
  });

  it('a file that cannot be read is kept for the next launch', async () => {
    const { source, state } = fileSource('{"format":"waymark","version":99,"exportedAt":0}');
    const result = await runHandover(source, trips);
    expect(result.outcome).toBe('failed');
    expect(state.removed).toBe(false);
    expect(await trips.trips()).toEqual([]);
  });

  it('running it twice changes nothing the second time', async () => {
    const text = encodeArchive(archiveOf([bundle], 1));
    await runHandover(fileSource(text).source, trips);
    await runHandover(fileSource(text).source, trips);
    expect(await trips.trips()).toHaveLength(1);
  });
});
