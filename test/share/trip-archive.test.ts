import { describe, expect, it } from 'vitest';
import { destinationOf, expense, type TripCosts } from '../../src/domain/model';
import {
  InvalidArchive,
  MAX_CODE_CHARS,
  archiveOf,
  bundlesOf,
  decodeArchive,
  encodeArchive,
  fromCode,
  toCode,
  type TripBundle
} from '../../src/share/trip-archive';
import { date, sample } from '../helpers';

const bundle = sample();
const trip: TripBundle = {
  trip: bundle.trip,
  travelers: bundle.travelers,
  segments: bundle.segments,
  ideas: bundle.ideas,
  costs: null
};

const onlyBundle = (bundles: TripBundle[]) => {
  expect(bundles).toHaveLength(1);
  return bundles[0]!;
};
const failureOf = (run: () => unknown): unknown => {
  try {
    run();
    return null;
  } catch (error) {
    return error;
  }
};

const costs: TripCosts = {
  settleCurrency: 'GBP',
  expenses: [
    expense({
      id: 'e1',
      tripId: bundle.trip.id,
      description: 'Dinner at Dishoom',
      amount: { minor: 8_640, currency: 'GBP' },
      paidBy: bundle.travelers[0]!.id,
      // A set: the file writes it sorted, as the Kotlin app does.
      splitAmong: bundle.travelers.map((x) => x.id).sort(),
      spentOn: date(2026, 5, 15),
      category: 'FOOD',
      addedAtMillis: 5
    }),
    expense({
      id: 'e2',
      tripId: bundle.trip.id,
      description: "Musée d'Orsay",
      amount: { minor: 3_200, currency: 'EUR' },
      paidBy: bundle.travelers[bundle.travelers.length - 1]!.id,
      spentOn: date(2026, 5, 19),
      category: 'ACTIVITY',
      addedAtMillis: 6
    })
  ],
  rates: [{ currency: 'EUR', perUnit: 0.85, asOf: date(2026, 5, 1), manual: true }]
};

describe('TripArchive', () => {
  it('a trip survives the round trip through a file exactly', () => {
    const text = encodeArchive(archiveOf([trip], 1));
    const back = onlyBundle(bundlesOf(decodeArchive(text)));
    expect(back.trip).toEqual(trip.trip);
    expect(back.travelers).toEqual(trip.travelers);
    expect(back.segments).toEqual(trip.segments);
    expect(back.ideas).toEqual(trip.ideas);
  });

  it('the worked example fits in a QR code, bookings intact', () => {
    const code = toCode(archiveOf([trip], 1));
    expect(code, 'the example is too large for a code').not.toBeNull();
    expect(code!.payload.length).toBeLessThanOrEqual(MAX_CODE_CHARS);
    const back = onlyBundle(bundlesOf(fromCode(code!.payload)));
    expect(back.trip).toEqual(trip.trip);
    expect(back.travelers).toEqual(trip.travelers);
    // Whatever was left out to fit, every booking arrives with its times, its
    // places and its reference.
    expect(back.segments).toHaveLength(trip.segments.length);
    back.segments.forEach((got, i) => {
      const sent = trip.segments[i]!;
      expect(got.id).toBe(sent.id);
      expect(got.startEpochMillis).toBe(sent.startEpochMillis);
      expect(got.endEpochMillis).toBe(sent.endEpochMillis);
      expect(got.origin).toEqual(sent.origin);
      expect(destinationOf(got)).toEqual(destinationOf(sent));
      expect(got.confirmationCode).toBe(sent.confirmationCode);
    });
  });

  it('a small trip travels in a code complete', () => {
    const small = { ...trip, segments: trip.segments.slice(0, 3), ideas: trip.ideas.slice(0, 1) };
    const code = toCode(archiveOf([small], 1))!;
    expect(code.detail).toBe('EVERYTHING');
    const back = onlyBundle(bundlesOf(fromCode(code.payload)));
    expect(back.segments).toEqual(small.segments);
    expect(back.ideas).toEqual(small.ideas);
  });

  it('something that is not a Waymark file says so', () => {
    for (const text of ['', '{}', '{"format":"other","version":1,"exportedAt":0}', 'not json']) {
      expect(
        failureOf(() => decodeArchive(text)),
        text
      ).toBeInstanceOf(InvalidArchive);
    }
    expect(failureOf(() => fromCode('%%%'))).toBeInstanceOf(InvalidArchive);
  });

  it('a file from a newer version is refused with a reason', () => {
    const failure = failureOf(() => decodeArchive('{"format":"waymark","version":99,"exportedAt":0}'));
    expect(failure).toBeInstanceOf(InvalidArchive);
    expect((failure as Error).message).toContain('newer');
  });

  it('fields a later version adds are ignored rather than fatal', () => {
    const text = encodeArchive(archiveOf([trip], 1)).replace('"format"', '"somethingNew": 1, "format"');
    expect(decodeArchive(text).trips).toHaveLength(1);
  });

  it('what a trip cost travels with it in a file', () => {
    const text = encodeArchive(archiveOf([{ ...trip, costs }], 1));
    expect(onlyBundle(bundlesOf(decodeArchive(text))).costs).toEqual(costs);
  });

  it('a file that says nothing of costs leaves them unknown, not empty', () => {
    const text = encodeArchive(archiveOf([trip], 1));
    expect(onlyBundle(bundlesOf(decodeArchive(text))).costs).toBeNull();
  });

  it('a code carries the itinerary, not the spending', () => {
    const code = toCode(archiveOf([{ ...trip, costs }], 1))!;
    expect(onlyBundle(bundlesOf(fromCode(code.payload))).costs).toBeNull();
  });
});

describe('documents, the checklist and passports', () => {
  it('travel in a backup and come back whole', () => {
    const [first, ...rest] = trip.travelers;
    const full: TripBundle = {
      ...trip,
      travelers: [
        { ...first!, identityDocs: [{ kind: 'PASSPORT', label: 'UK', expires: '2030-02-01' }] },
        ...rest
      ],
      documents: [
        {
          id: 'doc-1',
          tripId: trip.trip.id,
          segmentId: trip.segments[0]!.id,
          name: 'pass.png',
          mime: 'image/png',
          dataBase64: 'iVBORw0KGgo=',
          code: 'M1ELLISON/MARA',
          addedAtMillis: 5
        }
      ],
      checklist: [{ id: 'chk-1', tripId: trip.trip.id, text: 'Passport', done: true, position: 0 }]
    };
    const back = onlyBundle(bundlesOf(decodeArchive(encodeArchive(archiveOf([full])))));
    expect(back.documents).toEqual(full.documents);
    expect(back.checklist).toEqual(full.checklist);
    expect(back.travelers[0]!.identityDocs).toEqual(full.travelers[0]!.identityDocs);
  });

  it('are left out of a trip code, and an older file says nothing about them', () => {
    const back = onlyBundle(bundlesOf(decodeArchive(encodeArchive(archiveOf([trip])))));
    expect(back.documents).toBeNull();
    expect(back.checklist).toBeNull();
    const code = toCode(archiveOf([{ ...trip, documents: [] }]))!;
    expect(onlyBundle(bundlesOf(fromCode(code.payload))).documents).toBeNull();
  });
});
