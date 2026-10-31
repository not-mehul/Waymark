// How long it takes to get from one place to another, and how much of that is
// the part nobody schedules — the walk to the rank, the wait for the train.

import { distanceKm } from './geo';
import { groundModeLabel, hasCoordinates, type GroundMode, type Place } from './model';
import { duration } from './time-text';

export type Confidence = 'HIGH' | 'MEDIUM' | 'LOW';

export const CONFIDENCE_LABEL: Record<Confidence, string> = { HIGH: 'High', MEDIUM: 'Medium', LOW: 'Low' };

export interface TransitEstimate {
  mode: GroundMode;
  distanceKm: number;
  travelMinutes: number;
  overheadMinutes: number;
  confidence: Confidence;
}

export function totalMinutes(e: TransitEstimate): number {
  return e.travelMinutes + e.overheadMinutes;
}

export function transitSummary(e: TransitEstimate): string {
  return `${duration(totalMinutes(e))} by ${groundModeLabel(e.mode).toLowerCase()}`;
}

/** Door-to-door speeds in km/h, averaged over a whole journey. */
const AVERAGE_SPEED_KMH: Record<GroundMode, number> = {
  WALK: 4.6,
  TRANSIT: 24,
  BUS: 20,
  TAXI: 30,
  RIDESHARE: 30,
  RENTAL_CAR: 45,
  SHUTTLE: 26,
  FERRY: 32,
  TRAIN: 95
};

/** Fixed minutes spent not moving: finding the rank, queueing, buying a ticket. */
const OVERHEAD_MINUTES: Record<GroundMode, number> = {
  WALK: 0,
  TRANSIT: 12,
  BUS: 10,
  TAXI: 8,
  RIDESHARE: 7,
  RENTAL_CAR: 25,
  SHUTTLE: 15,
  FERRY: 15,
  TRAIN: 18
};

/** Straight-line distance understates road distance; dense cities wander more. */
function detourFactor(km: number, mode: GroundMode): number {
  if (mode === 'FERRY') return 1.1;
  if (mode === 'TRAIN') return 1.15;
  if (km < 3) return 1.45;
  if (km < 15) return 1.32;
  if (km < 60) return 1.22;
  return 1.15;
}

export function estimate(from: Place, to: Place, mode: GroundMode): TransitEstimate {
  const straightKm = distanceKm(from, to);
  const routeKm = straightKm * detourFactor(straightKm, mode);
  const speed = AVERAGE_SPEED_KMH[mode] ?? 30;
  const travel = speed <= 0 ? 0 : Math.round((routeKm / speed) * 60);
  let confidence: Confidence;
  if (!hasCoordinates(from) || !hasCoordinates(to)) confidence = 'LOW';
  else if (straightKm > 400) confidence = 'LOW';
  else if (straightKm > 80) confidence = 'MEDIUM';
  else confidence = 'HIGH';
  return {
    mode,
    distanceKm: routeKm,
    travelMinutes: Math.max(travel, straightKm > 0.05 ? 1 : 0),
    overheadMinutes: OVERHEAD_MINUTES[mode] ?? 10,
    confidence
  };
}

/** The mode a traveler would actually pick for this hop, absent a booking. */
export function suggestMode(from: Place, to: Place): GroundMode {
  const km = distanceKm(from, to);
  const airportInvolved = from.code != null || to.code != null;
  if (km < 1.2) return 'WALK';
  if (airportInvolved && km < 60) return 'TRANSIT';
  if (km < 12) return 'TRANSIT';
  if (km < 90) return 'TAXI';
  return 'TRAIN';
}

/** Best guess without a booked mode, used to fill gaps in the timeline. */
export function estimateBest(from: Place, to: Place): TransitEstimate {
  return estimate(from, to, suggestMode(from, to));
}

/** Every plausible option, cheapest-effort first, for the gap detail sheet. */
export function transitOptions(from: Place, to: Place): TransitEstimate[] {
  const km = distanceKm(from, to);
  const modes: GroundMode[] = [];
  if (km < 4) modes.push('WALK');
  modes.push('TRANSIT', 'TAXI');
  if (km > 40) modes.push('TRAIN');
  return [...new Set(modes)]
    .map((m) => estimate(from, to, m))
    .sort((a, b) => totalMinutes(a) - totalMinutes(b));
}
