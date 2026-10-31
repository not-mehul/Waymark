import { describe, expect, it } from 'vitest';
import { whereaboutsAt, zoneIdAt } from '../../src/domain/whereabouts';
import { at, date, sample } from '../helpers';

describe('Whereabouts', () => {
  const segments = sample().segments;

  it('before anything happens the trip is where it starts', () => {
    expect(whereaboutsAt(segments, at(date(2026, 5, 1), '09:00', 'UTC'))?.code).toBe('SFO');
  });

  it("during the London stay the clock is London's", () => {
    expect(zoneIdAt(segments, at(date(2026, 5, 16), '20:00', 'Europe/London'), 'America/Los_Angeles')).toBe(
      'Europe/London'
    );
  });

  it("after the train to Paris the clock is Paris's", () => {
    const split = date(2026, 5, 19);
    expect(zoneIdAt(segments, at(split.add({ days: 1 }), '18:00', 'Europe/Paris'), 'UTC')).toBe(
      'Europe/Paris'
    );
  });

  it('an empty trip is nowhere', () => {
    expect(whereaboutsAt([], 0)).toBeNull();
    expect(zoneIdAt([], 0, 'UTC')).toBe('UTC');
  });
});
