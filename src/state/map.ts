// What the map draws: one mark per place and one line per leg, from the
// bookings and the saved ideas. Ported from MapScreen.kt and MapModel.kt.

import { distanceKm } from '../domain/geo';
import {
  chronological,
  destinationOf,
  hasCoordinates,
  positionOf,
  shortLabel,
  type Idea,
  type LatLon,
  type Lodging,
  type Place,
  type Segment
} from '../domain/model';

/**
 * Ranking, not just styling. When labels compete for room the station wins,
 * because a traveler looking at a trip map is orienting by the airports.
 */
export type ChartKind = 'STATION' | 'STAY' | 'STOP';
export const CHART_RANK: Record<ChartKind, number> = { STATION: 0, STAY: 1, STOP: 2 };

/**
 * A place worth drawing. [id] is the mark's own, unique on the map: a flight
 * has a mark at each end, and both open the same booking, [segmentId]. A
 * saved idea not on the timeline yet has none.
 */
export interface ChartPlace {
  id: string;
  label: string;
  position: LatLon;
  kind: ChartKind;
  segmentId: string | null;
}

/** A leg between two places. Flights arc; ground legs run straight and dashed. */
export interface ChartRoute {
  id: string;
  from: LatLon;
  to: LatLon;
  flying: boolean;
}

function kindOf(place: Place, segment: Segment): ChartKind {
  if (place.code) return 'STATION';
  return segment.kind === 'LODGING' ? 'STAY' : 'STOP';
}

export function chartPlaces(segments: readonly Segment[], ideas: readonly Idea[]): ChartPlace[] {
  const seen = new Set<string>();
  const booked: ChartPlace[] = [];
  for (const segment of segments) {
    for (const [place, end] of [
      [segment.origin, 'from'],
      [destinationOf(segment), 'to']
    ] as const) {
      const position = positionOf(place);
      const key = place.code ?? place.name;
      if (!position || seen.has(key)) continue;
      seen.add(key);
      booked.push({
        id: `${segment.id}:${end}`,
        label: shortLabel(place),
        position,
        kind: kindOf(place, segment),
        segmentId: segment.id
      });
    }
  }
  // Saved ideas with coordinates sit beside the bookings: "what is near
  // where we are staying" is a map question.
  const names = new Set(booked.map((p) => p.label));
  const saved = ideas.flatMap((idea): ChartPlace[] => {
    const position = idea.place ? positionOf(idea.place) : null;
    if (!position || idea.status === 'DISMISSED' || names.has(idea.title)) return [];
    return [
      {
        id: `idea:${idea.id}`,
        label: idea.title,
        position,
        kind: 'STOP',
        segmentId: idea.scheduledSegmentId
      }
    ];
  });
  return [...booked, ...saved];
}

export function chartRoutes(segments: readonly Segment[]): ChartRoute[] {
  return segments.flatMap((s): ChartRoute[] => {
    const to = destinationOf(s);
    const a = positionOf(s.origin);
    const b = positionOf(to);
    if (!a || !b || s.origin.name === to.name) return [];
    return [{ id: s.id, from: a, to: b, flying: s.kind === 'FLIGHT' }];
  });
}

/** Every leg that goes somewhere, in order. */
export function mapLegs(segments: readonly Segment[]): Segment[] {
  return chronological(segments).filter(
    (s) => s.origin.name !== destinationOf(s).name || s.origin.code !== null
  );
}

export function mapStays(segments: readonly Segment[]): Lodging[] {
  return chronological(segments).filter((s): s is Lodging => s.kind === 'LODGING');
}

/** As the crow flies; null when either end has no coordinates or they are the same place. */
export function legDistance(s: Segment): number | null {
  const to = destinationOf(s);
  if (!hasCoordinates(s.origin) || !hasCoordinates(to)) return null;
  const km = distanceKm(s.origin, to);
  return km > 0 ? km : null;
}
