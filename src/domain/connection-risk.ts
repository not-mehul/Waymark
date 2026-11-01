// Minimum connection times. Airlines publish these per airport; without that
// feed these are the industry rules of thumb, close enough to warn on and
// honest about being estimates.

import { groundModeLabel, shortLabel, type Flight, type Place } from './model';
import { duration } from './time-text';
import { estimateBest, totalMinutes, transitSummary } from './transit';

export type RiskLevel = 'COMFORTABLE' | 'TIGHT' | 'AT_RISK' | 'BROKEN';

export const RISK_LABEL: Record<RiskLevel, string> = {
  COMFORTABLE: 'Comfortable',
  TIGHT: 'Tight',
  AT_RISK: 'At risk',
  BROKEN: 'Not connecting'
};

/** How much slack a connection has once the realistic minimum is taken out, and why. */
export interface ConnectionVerdict {
  level: RiskLevel;
  availableMinutes: number;
  requiredMinutes: number;
  reason: string;
}

export function slackMinutes(v: ConnectionVerdict): number {
  return v.availableMinutes - v.requiredMinutes;
}

const SAME_TERMINAL_DOMESTIC = 40;
const CHANGE_TERMINAL_DOMESTIC = 65;
const SAME_TERMINAL_INTERNATIONAL = 60;
const CHANGE_TERMINAL_INTERNATIONAL = 90;
const CHANGE_AIRPORT_BASE = 75;
const IMMIGRATION_SURCHARGE = 30;
const BAG_RECLAIM_SURCHARGE = 20;

export function requiredMinutes(inbound: Flight, onward: Flight, checkedBags = true): [number, string] {
  const sameAirport = inbound.destination.code != null && inbound.destination.code === onward.origin.code;
  const international =
    inbound.origin.country !== inbound.destination.country ||
    onward.origin.country !== onward.destination.country;
  const entersNewCountry = inbound.origin.country !== inbound.destination.country;

  if (!sameAirport) {
    const transfer = estimateBest(inbound.destination, onward.origin);
    const required =
      CHANGE_AIRPORT_BASE + totalMinutes(transfer) + (entersNewCountry ? IMMIGRATION_SURCHARGE : 0);
    return [
      required,
      `Airport change via ${groundModeLabel(transfer.mode).toLowerCase()} ` +
        `(${duration(totalMinutes(transfer))}) plus check-in`
    ];
  }

  const sameTerminal =
    inbound.arrivalTerminal != null && inbound.arrivalTerminal === onward.departureTerminal;
  let required = sameTerminal
    ? international
      ? SAME_TERMINAL_INTERNATIONAL
      : SAME_TERMINAL_DOMESTIC
    : international
      ? CHANGE_TERMINAL_INTERNATIONAL
      : CHANGE_TERMINAL_DOMESTIC;
  const notes = [
    sameTerminal
      ? `Same terminal at ${shortLabel(inbound.destination)}`
      : `Terminal change at ${shortLabel(inbound.destination)}`
  ];
  if (entersNewCountry) {
    required += IMMIGRATION_SURCHARGE;
    notes.push('immigration');
  }
  if (inbound.carrierCode !== onward.carrierCode && checkedBags) {
    required += BAG_RECLAIM_SURCHARGE;
    notes.push('bags re-checked between carriers');
  }
  return [required, notes.join(', ')];
}

/** Judge a connection on the times as booked — the only times Waymark has. */
export function assessConnection(inbound: Flight, onward: Flight, checkedBags = true): ConnectionVerdict {
  const available = Math.trunc((onward.startEpochMillis - inbound.endEpochMillis) / 60_000);
  const [required, reason] = requiredMinutes(inbound, onward, checkedBags);
  const slack = available - required;
  const level: RiskLevel =
    available <= 0 ? 'BROKEN' : slack < 0 ? 'AT_RISK' : slack < 25 ? 'TIGHT' : 'COMFORTABLE';
  const detail = {
    BROKEN: 'The onward flight leaves before this one lands',
    AT_RISK: `${reason} needs ${duration(required)}`,
    TIGHT: `${reason} — ${duration(slack)} of slack`,
    COMFORTABLE: reason
  }[level];
  return { level, availableMinutes: available, requiredMinutes: required, reason: detail };
}

/** Ground-side version: any two segments with a gap between them. */
export function assessGap(
  fromPlace: Place,
  toPlace: Place,
  availableMinutes: number,
  bufferMinutes = 15
): ConnectionVerdict {
  const e = estimateBest(fromPlace, toPlace);
  const required = totalMinutes(e) + bufferMinutes;
  const slack = availableMinutes - required;
  const level: RiskLevel =
    availableMinutes <= 0 ? 'BROKEN' : slack < 0 ? 'AT_RISK' : slack < 20 ? 'TIGHT' : 'COMFORTABLE';
  return { level, availableMinutes, requiredMinutes: required, reason: transitSummary(e) };
}
