import { describe, expect, it } from 'vitest';
import { centroid, globeArc, orthographic } from '../../src/domain/geo';

const ll = (latitude: number, longitude: number) => ({ latitude, longitude });
const london = ll(51.47, -0.45);
const sydney = ll(-33.94, 151.18);
const sanFrancisco = ll(37.62, -122.38);

const near = (actual: number, expected: number, delta: number) =>
  expect(Math.abs(actual - expected), `${actual} is not within ${delta} of ${expected}`).toBeLessThanOrEqual(
    delta
  );

describe('Globe projection', () => {
  it('the point facing the viewer lands in the middle of the disc', () => {
    const point = orthographic(london, london.latitude, london.longitude);
    near(point.x, 0, 1e-9);
    near(point.y, 0, 1e-9);
    expect(point.visible).toBe(true);
  });

  it('everything projects inside the unit disc', () => {
    for (const p of [london, sydney, sanFrancisco, ll(0, 0), ll(89, 12)]) {
      const point = orthographic(p, 20, 0);
      expect(Math.hypot(point.x, point.y)).toBeLessThanOrEqual(1 + 1e-9);
    }
  });

  it('the far side of the world is culled', () => {
    // Looking at London, Sydney is very nearly antipodal.
    expect(orthographic(sydney, london.latitude, london.longitude).visible).toBe(false);
    expect(orthographic(sydney, sydney.latitude, sydney.longitude).visible).toBe(true);
  });

  it('the horizon is the boundary, not an arbitrary cut', () => {
    // A point 90° away in longitude on the equator sits exactly on the rim.
    const rim = orthographic(ll(0, 90), 0, 0);
    near(Math.abs(rim.x), 1, 1e-9);
    expect(rim.visible).toBe(true);
    expect(orthographic(ll(0, 90.5), 0, 0).visible).toBe(false);
  });

  it('north is up and east is right when looking at the equator', () => {
    expect(orthographic(ll(30, 0), 0, 0).y).toBeGreaterThan(0);
    expect(orthographic(ll(0, 30), 0, 0).x).toBeGreaterThan(0);
  });

  it('a globe arc keeps its ends and reports crossing the horizon', () => {
    const arc = globeArc(sanFrancisco, sydney, sanFrancisco.latitude, sanFrancisco.longitude);
    expect(arc[0]!.visible).toBe(true);
    // A Pacific crossing from a San Francisco viewpoint disappears round the back before it arrives.
    expect(arc.some((p) => !p.visible)).toBe(true);
    expect(arc).toHaveLength(65);
  });

  it('the centroid sits between the places, and survives the date line', () => {
    const pacific = centroid([ll(0, 179), ll(0, -179)])!;
    // Averaging the longitudes would give 0° — the wrong side of the planet.
    expect(Math.abs(pacific.longitude)).toBeGreaterThan(179);
    near(pacific.latitude, 0, 1e-6);

    const atlantic = centroid([sanFrancisco, london])!;
    expect(atlantic.longitude).toBeGreaterThanOrEqual(-122.4);
    expect(atlantic.longitude).toBeLessThanOrEqual(-0.4);
    expect(atlantic.latitude).toBeGreaterThan(37);
  });

  it('an empty set has no centroid', () => {
    expect(centroid([])).toBeNull();
  });
});
