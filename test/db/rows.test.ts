// Row ⇄ model, for every kind of row the app writes. (Room's string codecs,
// tested beside this in Kotlin, have no counterpart: IndexedDB stores the
// lists, maps and places as they are.)
import 'fake-indexeddb/auto';
import { afterAll, describe, expect, it } from 'vitest';
import { ideaOf, ideaRow, openDatabase, segmentOf } from '../../src/db/database';
import { place, type RaisedReminder, type Segment } from '../../src/domain/model';
import { date, sample as buildSample } from '../helpers';

const sample = buildSample();
const db = openDatabase('rows');
afterAll(() => db.delete());

describe('Rows', () => {
  it('every booking in the worked example survives a round trip', async () => {
    await db.segments.bulkPut(sample.segments);
    for (const segment of sample.segments)
      expect(segmentOf((await db.segments.get(segment.id))!)).toEqual(segment);
  });

  it('trips, travelers and ideas survive a round trip', async () => {
    await db.trips.put(sample.trip);
    expect(await db.trips.get(sample.trip.id)).toEqual(sample.trip);
    await db.travelers.bulkPut(sample.travelers);
    for (const t of sample.travelers) expect(await db.travelers.get(t.id)).toEqual(t);
    const planned = { ...sample.ideas[0]!, plannedDate: date(2026, 5, 16) };
    await db.ideas.bulkPut([...sample.ideas, planned].map(ideaRow));
    for (const i of [...sample.ideas.slice(1), planned])
      expect(ideaOf((await db.ideas.get(i.id))!)).toEqual(i);
  });

  it('a reminder survives a round trip', async () => {
    const reminder: RaisedReminder = {
      signature: 'reminder:seg-1:1',
      segmentId: 'seg-1',
      label: 'BA286',
      headline: 'BA286 in 3h',
      detail: 'SFO to LHR',
      startEpochMillis: 10_800_000,
      raisedAtMillis: 0,
      posted: true,
      acknowledged: false
    };
    await db.alerts.put(reminder);
    expect(await db.alerts.get(reminder.signature)).toEqual(reminder);
  });

  it('a row of a kind this build does not know is skipped, not a crash', () => {
    const row = { ...sample.segments[0]!, kind: 'TELEPORT' } as unknown as Segment;
    expect(segmentOf(row)).toBeNull();
  });

  it('a place without coordinates stays without them', async () => {
    const hotel = place({ name: 'Hotel Rivoli', city: 'Paris', timeZoneId: 'Europe/Paris' });
    const stay = { ...sample.segments[0]!, id: 'seg-hotel', origin: hotel } as Segment;
    await db.segments.put(stay);
    const back = (await db.segments.get('seg-hotel'))!;
    expect(back.origin).toEqual(hotel);
    expect(back.origin.latitude).toBeNull();
  });
});
