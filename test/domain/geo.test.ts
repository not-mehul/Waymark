import { describe, expect, it } from 'vitest';
import {
  arc,
  bearingDegrees,
  bounds,
  crossesDateLine,
  distanceKmBetween as distanceKm,
  formatDistance,
  interpolate,
  project
} from '../../src/domain/geo';

const ll = (latitude: number, longitude: number) => ({ latitude, longitude });
const sfo = ll(37.6213, -122.379);
const lhr = ll(51.47, -0.4543);
const syd = ll(-33.9399, 151.1753);
const lax = ll(33.9416, -118.4085);

/** assertEquals(expected, actual, delta) */
const near = (actual: number, expected: number, delta: number) =>
  expect(Math.abs(actual - expected), `${actual} is not within ${delta} of ${expected}`).toBeLessThanOrEqual(
    delta
  );

describe('Geo', () => {
  it('great-circle distance matches published figures', () => {
    // SFO–LHR is about 8 600 km; allow one percent for the sphere model.
    near(distanceKm(sfo, lhr), 8620, 90);
    // SYD–LAX is about 12 050 km.
    near(distanceKm(syd, lax), 12050, 130);
  });

  it('distance is symmetric and zero at a point', () => {
    near(distanceKm(sfo, lhr), distanceKm(lhr, sfo), 0.001);
    near(distanceKm(sfo, sfo), 0, 0.001);
  });

  it('initial bearing from San Francisco to London runs north of east', () => {
    const bearing = bearingDegrees(sfo, lhr);
    expect(bearing).toBeGreaterThanOrEqual(20);
    expect(bearing).toBeLessThanOrEqual(45);
  });

  it('interpolation returns the endpoints and stays on the sphere', () => {
    near(interpolate(sfo, lhr, 0).latitude, sfo.latitude, 0.0001);
    near(interpolate(sfo, lhr, 1).longitude, lhr.longitude, 0.0001);
    // The midpoint of a northern transatlantic leg is further north than either end.
    expect(interpolate(sfo, lhr, 0.5).latitude).toBeGreaterThan(Math.max(sfo.latitude, lhr.latitude));
  });

  it('halves of an arc are equal length', () => {
    const middle = interpolate(sfo, lhr, 0.5);
    expect(Math.abs(distanceKm(sfo, middle) - distanceKm(middle, lhr))).toBeLessThan(1);
  });

  it('arc sampling has the requested resolution and ordering', () => {
    const points = arc(sfo, lhr, 24);
    expect(points).toHaveLength(25);
    near(points[0]!.latitude, sfo.latitude, 0.0001);
    near(points[points.length - 1]!.latitude, lhr.latitude, 0.0001);
  });

  it('projection maps the world into the unit square', () => {
    const nullIsland = project(ll(0, 0));
    near(nullIsland.x, 0.5, 0.0001);
    near(nullIsland.y, 0.5, 0.0001);
    const northWest = project(ll(80, -180));
    near(northWest.x, 0, 0.0001);
    expect(northWest.y).toBeGreaterThanOrEqual(0);
    expect(northWest.y).toBeLessThanOrEqual(0.2);
  });

  it('date line crossing is detected', () => {
    expect(crossesDateLine(ll(0, 179), ll(0, -179))).toBe(true);
    expect(crossesDateLine(ll(0, 10), ll(0, -10))).toBe(false);
  });

  it('bounds cover every point', () => {
    const b = bounds([sfo, lhr, syd])!;
    expect(b.minLat).toBeLessThanOrEqual(syd.latitude);
    expect(b.maxLat).toBeGreaterThanOrEqual(lhr.latitude);
    expect(b.maxLon).toBeGreaterThanOrEqual(syd.longitude);
  });

  it('distance formatting switches unit and precision', () => {
    expect(formatDistance(8.6)).toBe('8.6 km');
    expect(formatDistance(120.4)).toBe('120 km');
    expect(formatDistance(100, false)).toMatch(/mi$/);
  });
});
