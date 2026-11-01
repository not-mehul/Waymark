// The two projections the map draws with. They share everything but the
// maths that turns a latitude into a pixel: a flat chart for reading a
// region, and a globe for the long hauls where a flat map lies about the
// route. Ported from WorldMap.kt.

import { crossesDateLine, orthographic, project, type MapPoint } from '../../domain/geo';
import type { LatLon } from '../../domain/model';

export interface Point {
  x: number;
  y: number;
}

export interface MapView {
  /** Null when the point is not visible: behind the globe, say. */
  screen(p: LatLon): Point | null;
  /** Screen position ignoring visibility. */
  raw(p: LatLon): Point;
  visible(p: LatLon): boolean;
  /** True when consecutive points are not neighbours: across the seam, or the horizon. */
  breaks(a: LatLon, b: LatLon): boolean;
  /** Degrees between grid lines at the current scale. */
  readonly graticuleStep: number;
  /** How much of the world is on screen, 1 being all of it. */
  readonly worldFraction: number;
}

export const FLAT_MIN = 0.55;
export const FLAT_MAX = 400;
export const GLOBE_MIN = 1;
export const GLOBE_MAX = 9;

export class FlatView implements MapView {
  private readonly scale: number;

  constructor(
    readonly centre: MapPoint,
    readonly zoom: number,
    readonly width: number,
    readonly height: number
  ) {
    this.scale = width * zoom;
  }

  screen(p: LatLon): Point {
    return this.raw(p);
  }

  raw(p: LatLon): Point {
    const q = project(p);
    return {
      x: (q.x - this.centre.x) * this.scale + this.width / 2,
      y: (q.y - this.centre.y) * this.scale + this.height / 2
    };
  }

  visible(): boolean {
    return true;
  }

  breaks(a: LatLon, b: LatLon): boolean {
    return crossesDateLine(a, b);
  }

  get graticuleStep(): number {
    return this.zoom > 90 ? 5 : this.zoom > 30 ? 10 : this.zoom > 12 ? 15 : 30;
  }

  get worldFraction(): number {
    return this.zoom <= 0 ? 1 : Math.min(1, 1 / this.zoom);
  }
}

export class GlobeView implements MapView {
  /** The viewport disc, fixed, and the sphere, which grows as it is zoomed. */
  readonly discRadius: number;
  readonly radius: number;
  readonly cx: number;
  readonly cy: number;

  constructor(
    readonly centreLat: number,
    readonly centreLon: number,
    width: number,
    height: number,
    readonly zoom: number
  ) {
    this.discRadius = (Math.min(width, height) / 2) * 0.94;
    this.radius = this.discRadius * zoom;
    this.cx = width / 2;
    this.cy = height / 2;
  }

  screen(p: LatLon): Point | null {
    return this.visible(p) ? this.raw(p) : null;
  }

  raw(p: LatLon): Point {
    const q = orthographic(p, this.centreLat, this.centreLon);
    return { x: this.cx + q.x * this.radius, y: this.cy - q.y * this.radius };
  }

  visible(p: LatLon): boolean {
    return orthographic(p, this.centreLat, this.centreLon).visible;
  }

  /** A leg only breaks on the globe where it passes behind the horizon. */
  breaks(a: LatLon, b: LatLon): boolean {
    return this.visible(a) !== this.visible(b);
  }

  get graticuleStep(): number {
    return this.zoom > 6 ? 10 : this.zoom > 2.5 ? 15 : 30;
  }

  get worldFraction(): number {
    return Math.min(1, 1 / this.zoom);
  }

  angleOf(p: Point): number {
    return Math.atan2(p.y - this.cy, p.x - this.cx);
  }

  onLimb(angle: number): Point {
    return { x: this.cx + this.radius * Math.cos(angle), y: this.cy + this.radius * Math.sin(angle) };
  }
}

const samePoint = (a: LatLon, b: LatLon) => a.latitude === b.latitude && a.longitude === b.longitude;

/**
 * A ring split into the runs actually on screen, broken wherever the
 * projection says two consecutive points are not neighbours.
 */
export function runsOf(ring: readonly LatLon[], view: MapView): Point[][] {
  const runs: Point[][] = [];
  let current: Point[] = [];
  ring.forEach((point, index) => {
    const previous = index === 0 ? null : ring[index - 1]!;
    if (previous && view.breaks(previous, point) && current.length > 0) {
      runs.push(current);
      current = [];
    }
    if (view.visible(point)) current.push(view.raw(point));
  });
  if (current.length > 0) runs.push(current);
  // A closed ring split at its loop point is one piece of coast: join the
  // ends, so the horizon closure is one chord, not two.
  if (
    runs.length > 1 &&
    samePoint(ring[0]!, ring.at(-1)!) &&
    view.visible(ring[0]!) &&
    view.visible(ring[ring.length - 2]!)
  ) {
    const tail = runs.pop()!;
    runs[0] = [...tail, ...runs[0]!];
  }
  return runs;
}

const graticules = new Map<number, LatLon[][]>();

/** The grid at one spacing, sampled, so it curves on the globe. Built once per spacing. */
export function graticule(step: number): LatLon[][] {
  let lines = graticules.get(step);
  if (!lines) {
    lines = [];
    for (let lon = -180; lon <= 180; lon += step) {
      const line: LatLon[] = [];
      for (let lat = -90; lat <= 90; lat += 3) line.push({ latitude: lat, longitude: lon });
      lines.push(line);
    }
    for (let lat = -60; lat <= 60; lat += step) {
      const line: LatLon[] = [];
      for (let lon = -180; lon <= 180; lon += 4) line.push({ latitude: lat, longitude: lon });
      lines.push(line);
    }
    graticules.set(step, lines);
  }
  return lines;
}

/** Centre and zoom that put every point comfortably inside the canvas. */
export function fitViewport(
  points: readonly MapPoint[],
  width: number,
  height: number
): { centre: MapPoint; zoom: number } {
  if (points.length === 0 || width <= 0) return { centre: { x: 0.5, y: 0.5 }, zoom: 1 };
  const xs = points.map((p) => p.x);
  const ys = points.map((p) => p.y);
  const [minX, maxX, minY, maxY] = [Math.min(...xs), Math.max(...xs), Math.min(...ys), Math.max(...ys)];
  const spanX = Math.max(maxX - minX, 0.004);
  const spanY = Math.max(maxY - minY, 0.004);
  const fitX = 1 / (spanX * 1.35);
  const fitY = height / width / (spanY * 1.4);
  const zoom = Math.min(Math.max(Math.min(fitX, fitY), FLAT_MIN), 200);
  return { centre: { x: (minX + maxX) / 2, y: (minY + maxY) / 2 }, zoom };
}
