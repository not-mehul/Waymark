import { describe, expect, it } from 'vitest';
import { coastlineRings } from '../../src/catalog/coastline';
import { distanceKmBetween, type LatLon } from '../../src/domain/geo';

const rings = coastlineRings();
const ll = (latitude: number, longitude: number): LatLon => ({ latitude, longitude });

/** Even-odd ray cast, the standard point-in-polygon test. */
const covers = (point: LatLon) =>
  rings.some((ring) => {
    let inside = false;
    for (let i = 0; i < ring.length; i++) {
      const a = ring[i]!;
      const b = ring[(i + 1) % ring.length]!;
      if (a.latitude > point.latitude === b.latitude > point.latitude) continue;
      const crossing =
        ((b.longitude - a.longitude) * (point.latitude - a.latitude)) / (b.latitude - a.latitude) +
        a.longitude;
      if (point.longitude < crossing) inside = !inside;
    }
    return inside;
  });

const steps = (ring: LatLon[]) => ring.slice(1).map((b, i) => [ring[i]!, b] as const);

describe('Coastline', () => {
  it('the world is present and parses', () => {
    expect(rings.length).toBeGreaterThanOrEqual(40);
    const points = rings.reduce((n, r) => n + r.length, 0);
    expect(points).toBeGreaterThanOrEqual(1_000);
    expect(points).toBeLessThanOrEqual(3_000);
  });

  it('every coordinate is a real coordinate', () => {
    for (const ring of rings) {
      for (const p of ring) {
        expect(Math.abs(p.latitude), `lat ${p.latitude}`).toBeLessThanOrEqual(90);
        expect(Math.abs(p.longitude), `lon ${p.longitude}`).toBeLessThanOrEqual(180);
      }
    }
  });

  it('every ring is closed, so it can be filled', () => {
    for (const ring of rings) {
      expect(ring.length).toBeGreaterThanOrEqual(4);
      expect(ring[ring.length - 1]).toEqual(ring[0]);
    }
  });

  // A ring that hops the seam from one interior longitude to another fills as
  // a stripe across the whole chart. A step between the two edges themselves
  // is allowed — that is how Antarctica closes along the bottom of the world.
  it('no ring hops the antimeridian from interior longitudes', () => {
    for (const ring of rings) {
      for (const [a, b] of steps(ring)) {
        if (Math.abs(a.longitude) === 180 && Math.abs(b.longitude) === 180) continue;
        expect(
          Math.abs(a.longitude - b.longitude),
          `seam crossing at ${a.longitude} → ${b.longitude}`
        ).toBeLessThanOrEqual(180);
      }
    }
  });

  it('the coastline is coarse enough to draw every frame', () => {
    // One point per fifty kilometres or so; a denser table would be a
    // per-frame cost with no visible return at this size on screen.
    const lengths = rings.flatMap((ring) => steps(ring).map(([a, b]) => distanceKmBetween(a, b)));
    expect(lengths.length).toBeGreaterThan(0);
    expect(lengths.reduce((a, b) => a + b, 0) / lengths.length).toBeGreaterThan(20);
  });

  it('known land is inside a ring and known ocean is not', () => {
    expect(covers(ll(48.85, 2.35)), 'Paris is not on land').toBe(true);
    expect(covers(ll(38.5, -98)), 'Kansas is not on land').toBe(true);
    expect(covers(ll(0, -140)), 'mid-Pacific is land').toBe(false);
    expect(covers(ll(30, -40)), 'mid-Atlantic is land').toBe(false);
  });

  it('the largest ring is the Africa-Eurasia landmass', () => {
    const biggest = [...rings].sort((a, b) => b.length - a.length)[0]!;
    const lons = biggest.map((p) => p.longitude);
    // It reaches from the Atlantic coast well into Asia.
    expect(Math.min(...lons)).toBeLessThan(-10);
    expect(Math.max(...lons)).toBeGreaterThan(100);
  });
});
