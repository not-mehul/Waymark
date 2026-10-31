import { describe, expect, it } from 'vitest';
import { PROGRESS_MAX, TICK_MILLIS, liveJourneyOf, nextJourneyChangeAt } from '../../src/domain/live-journey';
import { durationMillis } from '../../src/domain/model';
import { LEAD_OFF, ReminderPreferences } from '../../src/domain/reminders';
import { HOUR, MINUTE, flightsIn, sample } from '../helpers';

const segments = sample().segments;
const flight = [...flightsIn(segments)].sort((a, b) => a.startEpochMillis - b.startEpochMillis)[0]!;
const on = ReminderPreferences.DEFAULT;

describe('LiveJourney', () => {
  it('nothing shows long before a departure', () => {
    expect(liveJourneyOf([flight], on, flight.startEpochMillis - 5 * HOUR)).toBeNull();
  });

  it('a flight shows from three hours out, counting down to departure', () => {
    const journey = liveJourneyOf([flight], on, flight.startEpochMillis - 40 * MINUTE)!;
    expect(journey).not.toBeNull();
    expect(journey.phase).toBe('DEPARTING');
    expect(journey.progress).toBe(0);
    expect(journey.chip).toBe('40m');
    expect(journey.headline).toBe('Departs in 40m');
    expect(journey.targetMillis).toBe(flight.startEpochMillis);
  });

  it('under way it counts down to landing and fills in', () => {
    const halfway = flight.startEpochMillis + Math.trunc(durationMillis(flight) / 2);
    const journey = liveJourneyOf([flight], on, halfway)!;
    expect(journey).not.toBeNull();
    expect(journey.phase).toBe('UNDER_WAY');
    expect(journey.progress).toBe(PROGRESS_MAX / 2);
    expect(journey.headline.startsWith('Lands in ')).toBe(true);
    expect(journey.targetMillis).toBe(flight.endEpochMillis);
  });

  it('it is gone on landing', () => {
    expect(liveJourneyOf([flight], on, flight.endEpochMillis)).toBeNull();
  });

  it('turning flight reminders off quiets it', () => {
    const off = on.with('FLIGHTS', LEAD_OFF);
    expect(liveJourneyOf([flight], off, flight.startEpochMillis)).toBeNull();
    expect(nextJourneyChangeAt([flight], off, flight.startEpochMillis - 5 * HOUR)).toBeNull();
  });

  it('stays and bookings never show', () => {
    const quiet = segments.filter((s) => s.kind === 'LODGING' || s.kind === 'EXPERIENCE');
    for (const s of quiet) expect(liveJourneyOf([s], on, s.startEpochMillis)).toBeNull();
  });

  it('it wakes for the window, then every tick, then for landing', () => {
    const before = flight.startEpochMillis - 5 * HOUR;
    expect(nextJourneyChangeAt([flight], on, before)).toBe(flight.startEpochMillis - 3 * HOUR);

    const showing = flight.startEpochMillis - 2 * HOUR;
    expect(nextJourneyChangeAt([flight], on, showing)).toBe(showing + TICK_MILLIS);

    const nearlyThere = flight.endEpochMillis - 5 * MINUTE;
    expect(nextJourneyChangeAt([flight], on, nearlyThere)).toBe(flight.endEpochMillis);
  });
});
