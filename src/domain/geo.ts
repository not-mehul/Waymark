// Distances, bearings, great circles and the two map projections.

import { formatFixed } from './decimal';
import { positionOf, type LatLon, type Place } from './model';

export type { LatLon };

/** Projected map coordinate, both axes normalised to 0..1. */
export interface MapPoint {
  x: number;
  y: number;
}

export interface BoundingBox {
  minLat: number;
  minLon: number;
  maxLat: number;
  maxLon: number;
}

export function boxCentre(b: BoundingBox): LatLon {
  return { latitude: (b.minLat + b.maxLat) / 2, longitude: (b.minLon + b.maxLon) / 2 };
}

export function paddedBox(b: BoundingBox, fraction: number): BoundingBox {
  const padLat = Math.max((b.maxLat - b.minLat) * fraction, 0.35);
  const padLon = Math.max((b.maxLon - b.minLon) * fraction, 0.35);
  return {
    minLat: Math.max(-85, b.minLat - padLat),
    minLon: Math.max(-180, b.minLon - padLon),
    maxLat: Math.min(85, b.maxLat + padLat),
    maxLon: Math.min(180, b.maxLon + padLon)
  };
}

export const EARTH_RADIUS_KM = 6371.0088;

export const toRadians = (degrees: number) => (degrees * Math.PI) / 180;
export const toDegrees = (radians: number) => (radians * 180) / Math.PI;
const clamp = (v: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, v));

/** Great-circle distance in kilometres between two coordinates. */
export function distanceKmBetween(a: LatLon, b: LatLon): number {
  const dLat = toRadians(b.latitude - a.latitude);
  const dLon = toRadians(b.longitude - a.longitude);
  const lat1 = toRadians(a.latitude);
  const lat2 = toRadians(b.latitude);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) ** 2;
  return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(h)));
}

/**
 * Between two places: zero when either end has no coordinates, because an
 * unknown distance is not an enormous one.
 */
export function distanceKm(a: Place, b: Place): number {
  const from = positionOf(a);
  const to = positionOf(b);
  return from && to ? distanceKmBetween(from, to) : 0;
}

/** Initial bearing from [a] to [b], degrees clockwise from north. */
export function bearingDegrees(a: LatLon, b: LatLon): number {
  const lat1 = toRadians(a.latitude);
  const lat2 = toRadians(b.latitude);
  const dLon = toRadians(b.longitude - a.longitude);
  const y = Math.sin(dLon) * Math.cos(lat2);
  const x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
  return (toDegrees(Math.atan2(y, x)) + 360) % 360;
}

/** Point at [fraction] along the great circle from [a] to [b]. */
export function interpolate(a: LatLon, b: LatLon, fraction: number): LatLon {
  const lat1 = toRadians(a.latitude);
  const lon1 = toRadians(a.longitude);
  const lat2 = toRadians(b.latitude);
  const lon2 = toRadians(b.longitude);
  const d =
    2 *
    Math.asin(
      Math.min(
        1,
        Math.sqrt(
          Math.sin((lat2 - lat1) / 2) ** 2 +
            Math.cos(lat1) * Math.cos(lat2) * Math.sin((lon2 - lon1) / 2) ** 2
        )
      )
    );
  if (d < 1e-9) return a;
  const f = clamp(fraction, 0, 1);
  const aCoef = Math.sin((1 - f) * d) / Math.sin(d);
  const bCoef = Math.sin(f * d) / Math.sin(d);
  const x = aCoef * Math.cos(lat1) * Math.cos(lon1) + bCoef * Math.cos(lat2) * Math.cos(lon2);
  const y = aCoef * Math.cos(lat1) * Math.sin(lon1) + bCoef * Math.cos(lat2) * Math.sin(lon2);
  const z = aCoef * Math.sin(lat1) + bCoef * Math.sin(lat2);
  return {
    latitude: toDegrees(Math.atan2(z, Math.sqrt(x * x + y * y))),
    longitude: toDegrees(Math.atan2(y, x))
  };
}

/** Sampled great-circle path, dense enough to look like a curve on screen. */
export function arc(a: LatLon, b: LatLon, samples = 48): LatLon[] {
  const steps = Math.max(2, samples);
  return Array.from({ length: steps + 1 }, (_, i) => interpolate(a, b, i / steps));
}

/** Web-Mercator-ish projection into 0..1 space, latitude clamped at ±85°. */
export function project(point: LatLon): MapPoint {
  const lat = clamp(point.latitude, -85.05112878, 85.05112878);
  const x = (point.longitude + 180) / 360;
  const sinLat = Math.sin(toRadians(lat));
  const y = 0.5 - Math.log((1 + sinLat) / (1 - sinLat)) / (4 * Math.PI);
  return { x, y };
}

export function bounds(points: readonly LatLon[]): BoundingBox | null {
  if (points.length === 0) return null;
  let minLat = 90;
  let maxLat = -90;
  let minLon = 180;
  let maxLon = -180;
  for (const p of points) {
    minLat = Math.min(minLat, p.latitude);
    maxLat = Math.max(maxLat, p.latitude);
    minLon = Math.min(minLon, p.longitude);
    maxLon = Math.max(maxLon, p.longitude);
  }
  return { minLat, minLon, maxLat, maxLon };
}

/** True when a route crosses the antimeridian and must be drawn in two pieces. */
export function crossesDateLine(a: LatLon, b: LatLon): boolean {
  return Math.abs(a.longitude - b.longitude) > 180;
}

/** Kilometres, or miles where the road signs are in miles. Set once at start-up. */
export const distanceSettings = { metric: true };

/** Countries whose road distances are posted in miles. */
export const MILE_REGIONS: ReadonlySet<string> = new Set(['US', 'GB', 'LR', 'MM']);

export function formatDistance(km: number, metric: boolean = distanceSettings.metric): string {
  if (metric) return km < 10 ? `${formatFixed(km, 1)} km` : `${Math.trunc(km)} km`;
  const miles = km * 0.621371;
  return miles < 10 ? `${formatFixed(miles, 1)} mi` : `${Math.trunc(miles)} mi`;
}

// — Orthographic projection: the globe ——————————————————————————————————

/**
 * A point seen on a globe: unit-sphere screen coordinates in −1..1, and
 * whether it is on the near face.
 */
export interface GlobePoint {
  x: number;
  y: number;
  visible: boolean;
}

/** The view of a sphere from infinitely far away, centred on (centreLat, centreLon). */
export function orthographic(point: LatLon, centreLat: number, centreLon: number): GlobePoint {
  const lat = toRadians(point.latitude);
  const lon = toRadians(point.longitude);
  const lat0 = toRadians(centreLat);
  const lon0 = toRadians(centreLon);
  const cosC = Math.sin(lat0) * Math.sin(lat) + Math.cos(lat0) * Math.cos(lat) * Math.cos(lon - lon0);
  return {
    x: Math.cos(lat) * Math.sin(lon - lon0),
    y: Math.cos(lat0) * Math.sin(lat) - Math.sin(lat0) * Math.cos(lat) * Math.cos(lon - lon0),
    visible: cosC >= 0
  };
}

/** The great-circle path, sampled and projected for the globe in one pass. */
export function globeArc(
  from: LatLon,
  to: LatLon,
  centreLat: number,
  centreLon: number,
  samples = 64
): GlobePoint[] {
  return arc(from, to, samples).map((p) => orthographic(p, centreLat, centreLon));
}

/**
 * Where to point the globe so a set of places is in view: the normalised mean
 * of their unit vectors, which behaves across the antimeridian.
 */
export function centroid(points: readonly LatLon[]): LatLon | null {
  if (points.length === 0) return null;
  let x = 0;
  let y = 0;
  let z = 0;
  for (const p of points) {
    const lat = toRadians(p.latitude);
    const lon = toRadians(p.longitude);
    x += Math.cos(lat) * Math.cos(lon);
    y += Math.cos(lat) * Math.sin(lon);
    z += Math.sin(lat);
  }
  x /= points.length;
  y /= points.length;
  z /= points.length;
  const hypotenuse = Math.sqrt(x * x + y * y);
  if (hypotenuse < 1e-9 && Math.abs(z) < 1e-9) return points[0]!;
  return { latitude: toDegrees(Math.atan2(z, hypotenuse)), longitude: toDegrees(Math.atan2(y, x)) };
}
