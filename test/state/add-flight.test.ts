import { describe, expect, it } from 'vitest';
import { segmentEnd } from '../../src/domain/model';
import { localDate, localTime } from '../../src/domain/time';
import {
  canSave,
  entryLeg,
  flightOf,
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
