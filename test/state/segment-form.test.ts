// Ported from SegmentFormTest.kt.
import { beforeAll, describe, expect, it } from 'vitest';
import { AIRPORTS_ASSET, installAirports, parseAirportDirectory } from '../../src/catalog/airports';
import { buildSampleItinerary } from '../../src/catalog/sample-itinerary';
import {
  positionOf,
  segmentEnd,
  segmentStart,
  type Flight,
  type Ground,
  type Lodging,
  type Segment
} from '../../src/domain/model';
import { localDate } from '../../src/domain/time';
import { applyForm, formOf, type FormOutcome, type SegmentForm } from '../../src/state/segment-form';
import { bundledLines } from '../helpers';

const bundle = buildSampleItinerary(localDate(2026, 5, 14));
const party = bundle.travelers;
const outbound = bundle.segments.find((s): s is Flight => s.kind === 'FLIGHT' && s.id === 'seg-outbound')!;
const stay = bundle.segments.find((s): s is Lodging => s.kind === 'LODGING')!;

function saved(outcome: FormOutcome): Segment {
  if (!('saved' in outcome)) throw new Error(`Refused: ${JSON.stringify(outcome.refused)}`);
  return outcome.saved;
}
const refused = (outcome: FormOutcome) => ('refused' in outcome ? outcome.refused : null);
const edit = (s: Segment, changes: Partial<SegmentForm>) => ({ ...formOf(s, party), ...changes });

beforeAll(() => installAirports(parseAirportDirectory(bundledLines(AIRPORTS_ASSET))));

describe('SegmentForm', () => {
  it('an untouched form saves the booking unchanged', () => {
    const form = formOf(outbound, party);
    const result = saved(applyForm(form, outbound, party));
    // Everyone ticked is written back as everyone.
    expect(result).toEqual({ ...outbound, travelerIds: form.travelerIds });
  });

  it('a new flight number and gate are written to the flight', () => {
    const result = saved(
      applyForm(edit(outbound, { name: 'ba 287', departureGate: 'a9' }), outbound, party)
    ) as Flight;
    expect(result.carrierCode).toBe('BA');
    expect(result.flightNumber).toBe('287');
    expect(result.departureGate).toBe('A9');
  });

  it("changing an airport moves the departure into that airport's zone", () => {
    const result = saved(applyForm(edit(outbound, { where: 'JFK' }), outbound, party)) as Flight;
    expect(result.origin.code).toBe('JFK');
    expect(result.startZoneId).toBe('America/New_York');
    // The clock time is kept as typed, in the new zone.
    expect(segmentStart(result).toPlainTime()).toEqual(segmentStart(outbound).toPlainTime());
  });

  it('an unknown airport or a bad flight number is refused', () => {
    expect(refused(applyForm(edit(outbound, { destination: 'QQQ' }), outbound, party))).toEqual({
      kind: 'UNKNOWN_AIRPORT',
      code: 'QQQ'
    });
    expect(refused(applyForm(edit(outbound, { name: 'hello' }), outbound, party))).toEqual({
      kind: 'NOT_A_FLIGHT_NUMBER'
    });
  });

  it('an end a little before the start runs past midnight, further is refused', () => {
    const form = formOf(stay, party);
    const sameDayEarlier = {
      ...form,
      endDate: form.startDate,
      endTime: form.startTime.subtract({ hours: 1 })
    };
    const result = saved(applyForm(sameDayEarlier, stay, party));
    expect(segmentEnd(result).toPlainDate()).toEqual(form.startDate.add({ days: 1 }));

    const daysEarlier = { ...form, endDate: form.startDate.subtract({ days: 3 }) };
    expect(refused(applyForm(daysEarlier, stay, party))).toEqual({ kind: 'ENDS_BEFORE_START' });
  });

  it('nobody on a booking, or a stay with no name, is refused', () => {
    expect(refused(applyForm(edit(outbound, { travelerIds: [] }), outbound, party))).toEqual({
      kind: 'NO_TRAVELER'
    });
    expect(refused(applyForm(edit(stay, { name: '  ' }), stay, party))).toEqual({ kind: 'NO_NAME' });
  });

  it('a stay keeps its place while the words are unchanged', () => {
    const result = saved(
      applyForm(edit(stay, { room: 'Room 4', address: '1 Example Street' }), stay, party)
    ) as Lodging;
    expect(positionOf(result.origin)).toEqual(positionOf(stay.origin));
    expect(result.roomDescription).toBe('Room 4');
    expect(result.origin.address).toBe('1 Example Street');
  });

  it('seats and tickets are kept only for people on the flight', () => {
    const first = party[0]!.id;
    const seats = Object.fromEntries(party.map((t) => [t.id, '1A']));
    const result = saved(
      applyForm(edit(outbound, { travelerIds: [first], seats }), outbound, party)
    ) as Flight;
    expect(Object.keys(result.seats)).toEqual([first]);
  });

  it('a ground leg can change how it goes', () => {
    const ground = bundle.segments.find((s): s is Ground => s.kind === 'GROUND');
    if (!ground) return;
    const result = saved(applyForm(edit(ground, { mode: 'BUS', pickup: '' }), ground, party)) as Ground;
    expect(result.mode).toBe('BUS');
    expect(result.pickupInstruction).toBeNull();
    expect(result.endEpochMillis).toBeGreaterThan(result.startEpochMillis);
  });
});
