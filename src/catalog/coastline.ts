// The shape of the land, so the map has ground under it: Natural Earth's
// 1:110m land polygons (public domain), reduced to 1404 vertices across 50
// rings. No ring crosses the antimeridian. Parsed on first use, so a trip
// that never opens the map never pays for it.

import type { LatLon } from '../domain/model';
import { COASTLINE_RINGS } from './coastline-data';

let parsed: LatLon[][] | null = null;

/** Closed rings, largest landmass first. */
export function coastlineRings(): LatLon[][] {
  parsed ??= COASTLINE_RINGS.map((ring) =>
    ring.split(',').map((pair) => {
      const space = pair.indexOf(' ');
      return { latitude: Number(pair.slice(space + 1)), longitude: Number(pair.slice(0, space)) };
    })
  );
  return parsed;
}
