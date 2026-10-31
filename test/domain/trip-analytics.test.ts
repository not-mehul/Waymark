import { describe, expect, it } from 'vitest';
import {
  EMISSION_FACTORS,
  bookedShare,
  busyHours,
  carbonKg,
  hasDistance,
  tripReport
} from '../../src/domain/analytics';
import { distanceKm } from '../../src/domain/geo';
import { nights, segmentEnd, segmentStart, type Ground, type Lodging } from '../../src/domain/model';
import { compareDates, daysBetween, maxDate, minDate } from '../../src/domain/time';
import { dossierOf, flightsIn, sample } from '../helpers';

const bundle = sample();
const dossier = dossierOf(bundle);
const report = tripReport(dossier, bundle.ideas);
const sum = (values: number[]) => values.reduce((a, b) => a + b, 0);
const near = (actual: number, expected: number, delta: number) =>
  expect(Math.abs(actual - expected), `${actual} is not within ${delta} of ${expected}`).toBeLessThanOrEqual(
    delta
  );

describe('TripAnalytics', () => {
  it('distance splits into air and ground and adds up', () => {
    expect(report.flownKm).toBeGreaterThan(15_000);
    expect(report.groundKm).toBeGreaterThan(0);
    near(report.flownKm + report.groundKm, report.totalDistanceKm, 0.001);
  });

  it('the mode breakdown covers the ground distance and leads with air', () => {
    expect(report.distanceByMode[0]!.label).toBe('Air');
    expect(report.distanceByMode[0]!.emphasis).toBe(true);
    near(sum(report.distanceByMode.slice(1).map((m) => m.value)), report.groundKm, 0.5);
    // Every bar carries its own written value; none is blank.
    expect(report.distanceByMode.every((m) => m.display.trim() !== '')).toBe(true);
  });

  it('the longest leg is the transatlantic one', () => {
    expect(report.longestLegKm).toBeGreaterThan(8_000);
    expect(report.longestLegLabel).toContain('SFO');
  });

  it('there is one day load per calendar day of the trip', () => {
    const first = bundle.segments.map((s) => segmentStart(s).toPlainDate()).reduce(minDate);
    const last = bundle.segments.map((s) => segmentEnd(s).toPlainDate()).reduce(maxDate);
    expect(report.dayLoads).toHaveLength(daysBetween(first, last) + 1);
    for (let i = 1; i < report.dayLoads.length; i++) {
      expect(compareDates(report.dayLoads[i - 1]!.date, report.dayLoads[i]!.date)).toBeLessThan(0);
    }
  });

  it('a day cannot hold more hours than a day has', () => {
    for (const load of report.dayLoads) {
      expect(busyHours(load), `${load.date} held ${busyHours(load)}h`).toBeLessThanOrEqual(24.01);
      expect(load.movingHours >= 0 && load.bookedHours >= 0).toBe(true);
    }
  });

  it('the busiest day carries one of the long-haul flights', () => {
    const busiest = report.busiestDay!;
    const longHaulDays = new Set(
      flightsIn(bundle.segments).flatMap((f) => [
        String(segmentStart(f).toPlainDate()),
        String(segmentEnd(f).toPlainDate())
      ])
    );
    expect(longHaulDays.has(String(busiest.date)), `busiest was ${busiest.date}`).toBe(true);
    near(busyHours(busiest), Math.max(...report.dayLoads.map(busyHours)), 0.0001);
  });

  it('countries and zones are counted without repeats', () => {
    expect(report.countriesVisited).toEqual(expect.arrayContaining(['France', 'United Kingdom']));
    expect([...new Set(report.countriesVisited)]).toEqual(report.countriesVisited);
    // San Francisco to London, London to Paris, Paris back to San Francisco:
    // three changes of clock for each traveler.
    expect(report.clockChanges).toBe(3);
    near(report.hoursShifted, 8 + 1 + 9, 0.001);
  });

  it('nights are counted per city', () => {
    const lodgings = bundle.segments.filter((s): s is Lodging => s.kind === 'LODGING');
    near(sum(report.nightsByCity.map((m) => m.value)), sum(lodgings.map(nights)), 0.001);
  });

  it('carbon uses the long-haul factor for long flights and is dominated by them', () => {
    const flights = flightsIn(bundle.segments);
    expect(carbonKg(flights)).toBeGreaterThan(report.carbonKg * 0.9);

    // A single 10 000 km flight at the published long-haul factor.
    const longest = [...flights].sort(
      (a, b) => distanceKm(b.origin, b.destination) - distanceKm(a.origin, a.destination)
    )[0]!;
    const km = distanceKm(longest.origin, longest.destination);
    near(carbonKg([longest]), km * EMISSION_FACTORS.LONG_HAUL_FLIGHT, 0.001);
  });

  it('walking costs nothing', () => {
    const ground = bundle.segments.find((s): s is Ground => s.kind === 'GROUND')!;
    near(carbonKg([{ ...ground, mode: 'WALK' }]), 0, 0.0001);
  });

  it('the booked share stays a fraction', () => {
    expect(bookedShare(report)).toBeGreaterThanOrEqual(0);
    expect(bookedShare(report)).toBeLessThanOrEqual(1);
  });

  it('an empty trip reports zeroes rather than throwing', () => {
    const empty = tripReport({ ...dossier, segments: [] });
    expect(empty.totalDistanceKm).toBe(0);
    expect(empty.dayLoads).toEqual([]);
    expect(empty.busiestDay).toBeNull();
    expect(hasDistance(empty)).toBe(false);
  });
});
