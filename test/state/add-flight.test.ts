import { describe, expect, it } from 'vitest';
import { segmentEnd } from '../../src/domain/model';
import { localDate, localTime } from '../../src/domain/time';
import {
  canSave,
  entryLeg,
  flightOf,
  fromBoardingPass,
  newFlightEntry,
  prefill,
  timeProblem
} from '../../src/state/add-flight';

const entry = {
  ...newFlightEntry(localDate(2026, 5, 14), ['a', 'b']),
  designator: 'ba 286',
  origin: 'SFO',
  destination: 'LHR',
  departTime: localTime(16, 20),
  arriveTime: localTime(10, 55)
};

describe('adding a flight', () => {
  it('works out that an evening departure lands the next morning', () => {
    expect(canSave(entry)).toBe(true);
    const saved = flightOf(entry, 'trip', ['a', 'b'])!;
    expect(saved.carrierCode + saved.flightNumber).toBe('BA286');
    expect(segmentEnd(saved).toPlainDate()).toEqual(localDate(2026, 5, 15));
    expect(saved.bookedWith).toBe('British Airways');
    // Everyone ticked is stored as everyone.
    expect(saved.travelerIds).toEqual([]);
  });

  it('keeps a chosen subset of the party', () => {
    expect(flightOf({ ...entry, selectedTravelers: ['b'] }, 'trip', ['a', 'b'])!.travelerIds).toEqual(['b']);
  });

  it('says when the times cannot be a flight, and when the day chosen cannot be', () => {
    const backwards = { ...entry, arrivalDayOffset: -1 };
    expect(entryLeg(backwards)).toBeNull();
    expect(timeProblem(backwards)).toMatch(/arrival day/);
    expect(canSave({ ...entry, destination: 'SFO' })).toBe(false);
    expect(canSave({ ...entry, designator: 'hello' })).toBe(false);
  });

  it('takes a seat from a pass only when one person is flying', () => {
    expect(prefill(entry, { seat: '21A' }).seats).toEqual({});
    expect(prefill({ ...entry, selectedTravelers: ['a'] }, { seat: '21A' }).seats).toEqual({ a: '21A' });
  });
});

describe('fromBoardingPass', () => {
  const pass = 'M1DESMARAIS/LUC       EABC123 YULFRAAC 0834 326J001A0025 100';
  const today = localDate(2026, 11, 1);

  it('fills in everything a pass carries, and leaves the clock times', () => {
    const solo = newFlightEntry(today, ['a']);
    const read = fromBoardingPass(solo, pass, today)!;
    expect(read.entry).toMatchObject({
      designator: 'AC834',
      date: localDate(2026, 11, 22),
      origin: 'YUL',
      destination: 'FRA',
      confirmationCode: 'ABC123',
      seats: { a: '1A' },
      departTime: null,
      arriveTime: null
    });
    expect(read.notice).toContain('Add the departure and arrival times');
  });

  it('puts the seat on no one when the flight has more than one traveller', () => {
    const read = fromBoardingPass(newFlightEntry(today, ['a', 'b']), pass, today)!;
    expect(read.entry.seats).toEqual({});
  });

  it('is null for text that is not a pass', () => {
    expect(fromBoardingPass(entry, 'waymark://import?d=abc', today)).toBeNull();
  });
});
