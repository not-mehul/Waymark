// Add a plan: the draft a step at a time, and the booking it becomes.
// Ported from PlanDraft and TripViewModel.planSegment.

import { resolvePlace } from '../catalog/places';
import { endAfter, isOrdered } from '../domain/leg-times';
import {
  experience,
  ground,
  lodging,
  placeZone,
  positionOf,
  tripStartDate,
  type GroundMode,
  type LatLon,
  type Place,
  type Segment,
  type SegmentKind,
  type TripDossier
} from '../domain/model';
import { localTime, systemZone, zonedOf, type LocalDate, type LocalTime } from '../domain/time';
import { positionAt, zoneIdAt } from '../domain/whereabouts';
import { newId } from '../db/trips';

export interface PlanDraft {
  kind: SegmentKind;
  title: string;
  where: string;
  destination: string;
  mode: GroundMode;
  startDate: LocalDate;
  startTime: LocalTime;
  endDate: LocalDate;
  endTime: LocalTime;
  vendor: string;
  confirmationCode: string;
  note: string;
  travelerIds: string[];
  /** The reading of `where` the traveler picked; null means the best one. */
  wherePlace: Place | null;
  destinationPlace: Place | null;
}

export function newPlanDraft(day: LocalDate, travelerIds: string[]): PlanDraft {
  return {
    kind: 'LODGING',
    title: '',
    where: '',
    destination: '',
    mode: 'TRAIN',
    startDate: day,
    startTime: localTime(15),
    endDate: day.add({ days: 1 }),
    endTime: localTime(11),
    vendor: '',
    confirmationCode: '',
    note: '',
    travelerIds,
    wherePlace: null,
    destinationPlace: null
  };
}

/**
 * Sensible defaults per kind: a hotel is a 15:00 check-in and a night long;
 * everything else is a morning slot on one day.
 */
export function withKind(draft: PlanDraft, kind: SegmentKind): PlanDraft {
  const stay = kind === 'LODGING';
  return {
    ...draft,
    kind,
    startTime: localTime(stay ? 15 : 9),
    endTime: localTime(11),
    endDate: stay ? draft.startDate.add({ days: 1 }) : draft.startDate
  };
}

/** Where the trip is on a day: what typed places are resolved against. */
export interface PlanContext {
  near: LatLon | null;
  zoneId: string;
}

export function planContext(dossier: TripDossier | null | undefined, date: LocalDate): PlanContext {
  const home = dossier?.trip.homeZoneId ?? systemZone();
  const noon = zonedOf(date, localTime(12), home).epochMilliseconds;
  const segments = dossier?.segments ?? [];
  return { near: positionAt(segments, noon), zoneId: zoneIdAt(segments, noon, home) };
}

/** The first day of the trip, the day a new plan starts on. */
export function defaultPlanDay(dossier: TripDossier | null | undefined, today: LocalDate): LocalDate {
  return dossier ? tripStartDate(dossier.trip) : today;
}

/**
 * The booking a finished draft describes, or null when its times do not
 * make one. Each end is the reading the traveler picked, or the best one: a
 * station code, a city, or the text as typed in the zone the trip is in that
 * day. Never the trip's home zone by default: a 15:00 check-in in Paris on a
 * trip created in San Francisco is 15:00 in Paris.
 */
export function planSegment(
  draft: PlanDraft,
  tripId: string,
  dossier: TripDossier | null | undefined
): Segment | null {
  if (draft.title.trim() === '') return null;
  const context = planContext(dossier, draft.startDate);
  const origin =
    draft.wherePlace ?? resolvePlace(draft.where.trim() || draft.title, context.near, context.zoneId).place;
  const isGround = draft.kind === 'GROUND';
  const destination = isGround
    ? (draft.destinationPlace ??
      resolvePlace(draft.destination, positionOf(origin) ?? context.near, context.zoneId).place)
    : origin;

  const startZone = placeZone(origin);
  const endZone = placeZone(destination);
  const start = zonedOf(draft.startDate, draft.startTime, startZone);
  const end = endAfter(
    start,
    (draft.kind === 'LODGING' ? draft.endDate : draft.startDate).toPlainDateTime(draft.endTime),
    endZone
  );
  if (!isOrdered(start, end)) return null;

  const common = {
    id: newId('seg'),
    tripId,
    origin,
    startEpochMillis: start.epochMilliseconds,
    endEpochMillis: end.epochMilliseconds,
    startZoneId: startZone,
    travelerIds: [...draft.travelerIds],
    confirmationCode: draft.confirmationCode.trim() || null,
    bookedWith: draft.vendor.trim() || null,
    note: draft.note.trim() || null
  };
  switch (draft.kind) {
    case 'LODGING':
      return lodging({ ...common, propertyName: draft.title.trim(), endZoneId: startZone });
    case 'GROUND':
      return ground({
        ...common,
        endZoneId: endZone,
        mode: draft.mode,
        destination,
        provider: draft.title.trim() || common.bookedWith
      });
    default:
      return experience({ ...common, endZoneId: startZone, name: draft.title.trim(), category: 'Booking' });
  }
}
