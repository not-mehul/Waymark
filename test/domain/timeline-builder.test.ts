import { describe, expect, it } from 'vitest';
import { experience, flight, lodging, place } from '../../src/domain/model';
import {
  buildTimeline,
  type DayBreak,
  type TimelineEntry,
  type TimelineEvent,
  type TimelineLink
} from '../../src/domain/timeline';
import { HOUR, at, date, dossierOf, flightsIn, instant, sample } from '../helpers';

const bundle = sample();
const dossier = dossierOf(bundle);
const duringTrip = instant('2026-05-16T09:00:00Z');

const events = (entries: TimelineEntry[]) => entries.filter((e): e is TimelineEvent => e.type === 'event');
const links = (entries: TimelineEntry[]) => entries.filter((e): e is TimelineLink => e.type === 'link');
const dayBreaks = (entries: TimelineEntry[]) => entries.filter((e): e is DayBreak => e.type === 'day');
const traveler = (prefix: string) => bundle.travelers.find((x) => x.fullName.startsWith(prefix))!;

describe('TimelineBuilder', () => {
  it('entries come out in clock order', () => {
    const keys = buildTimeline(dossier, { nowMillis: duringTrip }).map((e) => e.sortKey);
    expect(keys).toEqual([...keys].sort((a, b) => a - b));
  });

  it('every segment appears exactly once, and lodging twice', () => {
    const entries = events(buildTimeline(dossier, { nowMillis: duringTrip }));
    for (const segment of bundle.segments) {
      const appearances = entries.filter((e) => e.segment.id === segment.id).length;
      expect(appearances, `${segment.id} appearances`).toBe(segment.kind === 'LODGING' ? 2 : 1);
    }
  });

  it('each day gets one break, before its first event', () => {
    const entries = buildTimeline(dossier, { nowMillis: duringTrip });
    const breaks = dayBreaks(entries);
    expect(new Set(breaks.map((b) => String(b.date))).size).toBe(breaks.length);
    const firstIndex = entries.findIndex((e) => e.type === 'event');
    expect(entries.slice(0, firstIndex).some((e) => e.type === 'day')).toBe(true);
  });

  it("filtering by traveler drops the other traveler's segments", () => {
    const entries = events(
      buildTimeline(dossier, { travelerFilter: traveler('Julian').id, nowMillis: duringTrip })
    );
    expect(entries.some((e) => e.segment.id === 'seg-eurostar-mara')).toBe(false);
    expect(entries.some((e) => e.segment.id === 'seg-eurostar-julian')).toBe(true);
  });

  it('the now marker lands between past and future', () => {
    const entries = buildTimeline(dossier, { nowMillis: duringTrip });
    const nowIndex = entries.findIndex((e) => e.type === 'now');
    expect(nowIndex).toBeGreaterThanOrEqual(0);
    // Compared on each entry's own instant: a lodging check-out sits at the
    // end of the stay, not at the check-in that started it.
    entries.forEach((e, index) => {
      if (e.type !== 'event') return;
      if (index < nowIndex) expect(e.sortKey).toBeLessThanOrEqual(duringTrip);
      if (index > nowIndex) expect(e.sortKey).toBeGreaterThanOrEqual(duringTrip);
    });
  });

  it('links describe the gaps between movements', () => {
    const found = links(buildTimeline(dossier, { nowMillis: duringTrip }));
    expect(found.length).toBeGreaterThan(0);
    for (const link of found) {
      expect(link.fromSegment.id).not.toBe(link.toSegment.id);
      expect(link.toSegment.startEpochMillis).toBeGreaterThanOrEqual(link.fromSegment.startEpochMillis);
    }
  });

  it('an empty trip produces an empty timeline rather than a header', () => {
    expect(buildTimeline({ ...dossier, segments: [] }, { nowMillis: duringTrip })).toEqual([]);
  });

  // Two people on separate flights out of the same airport used to be read as
  // one person making an impossible connection, because the builder walked
  // every segment in one chronological line regardless of who was on it.
  it('two travelers on parallel flights are not read as one connection', () => {
    const outbound = flightsIn(bundle.segments)[0]!;
    // Same route, same morning, one each — the way a party often flies out.
    const hers = {
      ...outbound,
      id: 'seg-hers',
      travelerIds: [traveler('Mara').id],
      endEpochMillis: outbound.startEpochMillis + 10 * HOUR
    };
    const his = {
      ...outbound,
      id: 'seg-his',
      travelerIds: [traveler('Julian').id],
      startEpochMillis: outbound.startEpochMillis + 2 * HOUR,
      endEpochMillis: outbound.startEpochMillis + 12 * HOUR
    };
    const found = links(buildTimeline({ ...dossier, segments: [hers, his] }, { nowMillis: duringTrip }));
    expect(found, 'invented a connection between separate itineraries').toEqual([]);
  });

  it("a traveler's own connection is still judged", () => {
    const mara = traveler('Mara');
    const outbound = flightsIn(bundle.segments)[0]!;
    const first = {
      ...outbound,
      id: 'seg-first',
      travelerIds: [mara.id],
      endEpochMillis: outbound.startEpochMillis + 5 * HOUR
    };
    const onward = {
      ...outbound,
      id: 'seg-onward',
      travelerIds: [mara.id],
      origin: outbound.destination,
      destination: outbound.origin,
      startEpochMillis: outbound.startEpochMillis + 6 * HOUR,
      endEpochMillis: outbound.startEpochMillis + 11 * HOUR
    };
    const found = links(buildTimeline({ ...dossier, segments: [first, onward] }, { nowMillis: duringTrip }));
    expect(found).toHaveLength(1);
    expect(found[0]!.fromSegment.id).toBe('seg-first');
    expect(found[0]!.verdict).not.toBeNull();
  });

  // CX880 leaves Hong Kong after midnight on the 6th and lands in Los Angeles
  // on the evening of the 5th. A dinner the night before and the hotel on
  // arrival make the dates run 5th, 6th, 5th — which used to crash the
  // timeline with a duplicate list key.
  it('a date that comes round twice heads the column twice, with distinct keys', () => {
    const hongKong = 'Asia/Hong_Kong';
    const losAngeles = 'America/Los_Angeles';
    const hkg = place({
      name: 'Hong Kong International',
      code: 'HKG',
      city: 'Hong Kong',
      timeZoneId: hongKong
    });
    const lax = place({
      name: 'Los Angeles International',
      code: 'LAX',
      city: 'Los Angeles',
      timeZoneId: losAngeles
    });
    const segments = [
      experience({
        id: 'dinner',
        tripId: 't',
        name: 'Dinner',
        category: 'Food',
        origin: { ...hkg, name: 'Restaurant', code: null },
        startEpochMillis: at(date(2026, 11, 5), '19:00', hongKong),
        endEpochMillis: at(date(2026, 11, 5), '21:00', hongKong),
        startZoneId: hongKong,
        endZoneId: hongKong
      }),
      flight({
        id: 'cx880',
        tripId: 't',
        carrierCode: 'CX',
        flightNumber: '880',
        origin: hkg,
        destination: lax,
        startEpochMillis: at(date(2026, 11, 6), '00:50', hongKong),
        endEpochMillis: at(date(2026, 11, 5), '22:15', losAngeles),
        startZoneId: hongKong,
        endZoneId: losAngeles
      }),
      lodging({
        id: 'hotel',
        tripId: 't',
        propertyName: 'Hotel',
        origin: { ...lax, name: 'Hotel', code: null },
        startEpochMillis: at(date(2026, 11, 5), '23:30', losAngeles),
        endEpochMillis: at(date(2026, 11, 7), '11:00', losAngeles),
        startZoneId: losAngeles,
        endZoneId: losAngeles
      })
    ];
    const trip = { ...dossier, segments, party: { tripId: 't', travelers: [] } };
    const entries = buildTimeline(trip, { nowMillis: instant('2026-11-01T00:00:00Z') });

    const keys = entries.map((e) => e.key);
    expect(new Set(keys).size, `duplicate keys in ${keys.join(', ')}`).toBe(keys.length);
    expect(dayBreaks(entries).filter((b) => String(b.date) === '2026-11-05')).toHaveLength(2);
  });

  it('keys are unique on the worked example too', () => {
    const keys = buildTimeline(dossier, { nowMillis: duringTrip }).map((e) => e.key);
    expect(new Set(keys).size).toBe(keys.length);
  });
});
