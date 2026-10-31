import { describe, expect, it } from 'vitest';
import { airportPlaceFor } from '../../src/catalog/airports';
import { place } from '../../src/domain/model';
import { localTime, zonedOf } from '../../src/domain/time';
import { dateRange, dayOffsetSuffix, duration, relative, zoneShift } from '../../src/domain/time-text';
import { estimate, suggestMode, totalMinutes, transitOptions } from '../../src/domain/transit';
import { date } from '../helpers';

const heathrow = airportPlaceFor('LHR');
const bloomsbury = place({
  name: 'Bloomsbury',
  city: 'London',
  country: 'United Kingdom',
  latitude: 51.5205,
  longitude: -0.1265,
  timeZoneId: 'Europe/London'
});

describe('Transit and time', () => {
  it('a Heathrow transfer lands in the right hour', () => {
    const e = estimate(heathrow, bloomsbury, 'TRANSIT');
    expect(totalMinutes(e)).toBeGreaterThanOrEqual(40);
    expect(totalMinutes(e)).toBeLessThanOrEqual(100);
    expect(e.overheadMinutes).toBeGreaterThan(0);
  });

  it('a walk across the street is minutes, not an hour', () => {
    const nearby = { ...bloomsbury, name: 'Museum', latitude: 51.5194, longitude: -0.127 };
    expect(totalMinutes(estimate(bloomsbury, nearby, 'WALK'))).toBeLessThanOrEqual(6);
  });

  it('mode suggestion scales with distance', () => {
    const nextDoor = { ...bloomsbury, latitude: 51.521, longitude: -0.1268 };
    expect(suggestMode(bloomsbury, nextDoor)).toBe('WALK');
    expect(suggestMode(heathrow, bloomsbury)).toBe('TRANSIT');
  });

  it('options are ordered by time and never empty', () => {
    const options = transitOptions(heathrow, bloomsbury);
    expect(options.length).toBeGreaterThan(0);
    expect([...options].sort((a, b) => totalMinutes(a) - totalMinutes(b))).toEqual(options);
  });

  it('confidence drops when coordinates are missing', () => {
    const e = estimate(bloomsbury, place({ name: 'Somewhere' }), 'TAXI');
    expect(e.confidence).toBe('LOW');
    // Unknown is not "the far side of the world": no travel time is invented.
    expect(e.travelMinutes).toBe(0);
  });

  it('durations read the way a traveler says them', () => {
    expect(duration(45)).toBe('45m');
    expect(duration(120)).toBe('2h');
    expect(duration(160)).toBe('2h 40m');
    expect(duration(-15)).toBe('−15m');
  });

  it('relative time is honest about direction', () => {
    const now = 1_800_000_000_000;
    expect(relative(now, now)).toBe('now');
    expect(relative(now + 3_600_000, now)).toBe('in 1h');
    expect(relative(now - 1_200_000, now)).toBe('20m ago');
  });

  it('an overnight arrival is marked with a day offset', () => {
    const departure = zonedOf(date(2026, 5, 14), localTime(16, 20), 'America/Los_Angeles');
    const arrival = zonedOf(date(2026, 5, 15), localTime(10, 55), 'Europe/London');
    expect(dayOffsetSuffix(departure, arrival)).toBe('+1');
    expect(dayOffsetSuffix(departure, departure)).toBeNull();
  });

  it('zone shift states what the body is being asked to absorb', () => {
    const noon = zonedOf(date(2026, 5, 14), localTime(12), 'UTC').epochMilliseconds;
    expect(zoneShift('America/Los_Angeles', 'Europe/London', noon)).toBe('+8h');
    expect(zoneShift('UTC', 'UTC', 0)).toBeNull();
  });

  it('date ranges collapse within a month', () => {
    const range = dateRange(date(2026, 5, 14), date(2026, 5, 20));
    expect(range).toContain('14');
    expect(range).toContain('20');
    expect(range).toContain('May');
  });
});
