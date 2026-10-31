import { describe, expect, it } from 'vitest';
import { tripEndDate, tripStartDate } from '../../src/domain/model';
import { compareDates } from '../../src/domain/time';
import {
  basisFor,
  describeWeather,
  isWet,
  stopLabel,
  temperature,
  weatherKeyOf,
  weatherStops
} from '../../src/domain/weather';
import { date, dossierOf, sample } from '../helpers';

const bundle = sample();
const dossier = dossierOf(bundle);
const iso = (d: { toString(): string }) => d.toString();

describe('WeatherPlan', () => {
  it('the worked example leaves San Francisco for London, then Paris, day by day', () => {
    const stops = weatherStops(dossier, date(2026, 5, 1));
    expect([...new Set(stops.map(stopLabel))]).toEqual(['San Francisco', 'London', 'Paris']);
    const days = stops.flatMap((s) => s.days);
    // Every day once, in order, with no gaps.
    expect(days.map(iso)).toEqual([...days].sort(compareDates).map(iso));
    expect(new Set(days.map(iso)).size).toBe(days.length);
    expect(days[days.length - 1]).toEqual(tripEndDate(bundle.trip));
  });

  it('days already gone are not asked about', () => {
    const today = tripStartDate(bundle.trip).add({ days: 2 });
    const days = weatherStops(dossier, today).flatMap((s) => s.days);
    expect(days.every((d) => compareDates(d, today) >= 0)).toBe(true);
    expect(days[0]).toEqual(today);
  });

  it('a trip that is over needs no weather', () => {
    expect(weatherStops(dossier, tripEndDate(bundle.trip).add({ days: 1 }))).toEqual([]);
  });

  it('the next sixteen days are forecast, the rest are last year', () => {
    const today = date(2026, 5, 1);
    expect(basisFor(today, today)).toBe('FORECAST');
    expect(basisFor(today.add({ days: 15 }), today)).toBe('FORECAST');
    expect(basisFor(today.add({ days: 16 }), today)).toBe('LAST_YEAR');
  });

  it('places are cached to about a kilometre', () => {
    expect(weatherKeyOf(51.5074, -0.1278)).toBe('51.51,-0.13');
  });

  it("weather is said in words and in the reader's degrees", () => {
    expect(describeWeather(2)).toBe('Partly cloudy');
    expect(describeWeather(95)).toBe('Thunderstorms');
    expect(temperature(21.4, false)).toBe('21°');
    expect(temperature(21.4, true)).toBe('71°');
    expect(isWet(61)).toBe(true);
    expect(isWet(3)).toBe(false);
  });
});
