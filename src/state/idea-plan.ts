// What the Ideas tab works out from a trip: its cities and days, where a
// new idea's address sits, and which clock an idea is scheduled on. Ported
// from TripViewModel's idea half.

import { resolvePlace } from '../catalog/places';
import { MAX_DAYS } from '../domain/analytics';
import {
  destinationOf,
  tripEndDate,
  tripStartDate,
  type IdeaKind,
  type Idea,
  type Place,
  type PriceBand,
  type TripDossier
} from '../domain/model';
import { compareDates, todayIn, zoneOrUtc, type LocalDate } from '../domain/time';
import type { NewIdea } from '../db/ideas';
import { planContext } from './plan';

/** What the Add an idea steps collect. */
export interface IdeaDraft {
  title: string;
  kind: IdeaKind;
  city: string;
  note: string;
  address: string;
  priceBand: PriceBand | null;
  bestTime: string;
  minutes: string;
}

export function newIdeaDraft(cities: readonly string[]): IdeaDraft {
  return {
    title: '',
    kind: 'SEE',
    city: cities[0] ?? '',
    note: '',
    address: '',
    priceBand: null,
    bestTime: '',
    minutes: ''
  };
}

/** Every city the bookings touch, in the order they are reached. */
export function tripCities(dossier: TripDossier | null | undefined): string[] {
  const seen: string[] = [];
  for (const s of [...(dossier?.segments ?? [])].sort((a, b) => a.startEpochMillis - b.startEpochMillis)) {
    for (const city of [destinationOf(s).city, s.origin.city]) {
      if (city.trim() !== '' && !seen.includes(city)) seen.push(city);
    }
  }
  return seen;
}

/** The trip's own days; capped at a year so a mistyped date cannot build a decade. */
export function tripDays(dossier: TripDossier | null | undefined): LocalDate[] {
  if (!dossier) return [];
  const days: LocalDate[] = [];
  for (
    let d = tripStartDate(dossier.trip);
    compareDates(d, tripEndDate(dossier.trip)) <= 0 && days.length < MAX_DAYS;
    d = d.add({ days: 1 })
  ) {
    days.push(d);
  }
  return days;
}

const positiveMinutes = (text: string) => {
  const n = Number.parseInt(text.replace(/\D/g, ''), 10);
  return Number.isFinite(n) && n > 0 ? n : null;
};

/**
 * The fields to store. The address is kept as typed, on its city's clock;
 * the city's centre is not the address, so nothing is pinned.
 */
export function ideaFields(draft: IdeaDraft, dossier: TripDossier | null | undefined): NewIdea {
  const city = draft.city.trim() || tripCities(dossier)[0] || '';
  const address = draft.address.trim();
  let where: Place | null = null;
  if (address !== '' && dossier) {
    const context = planContext(dossier, tripStartDate(dossier.trip));
    where = {
      ...resolvePlace(city, context.near, context.zoneId).place,
      name: draft.title.trim(),
      address,
      latitude: null,
      longitude: null
    };
  }
  return {
    title: draft.title.trim(),
    kind: draft.kind,
    city,
    note: draft.note,
    place: where,
    priceBand: draft.priceBand,
    bestTime: draft.bestTime,
    typicalMinutes: positiveMinutes(draft.minutes)
  };
}

/**
 * The clock an idea happens on. In order: the idea's own place; a booking
 * in the same city, whose zone is already known exactly; the city looked up
 * by name; and failing all of those, wherever the trip is on that day.
 */
export function ideaZone(idea: Idea, dossier: TripDossier | null | undefined, date: LocalDate): string {
  const context = planContext(dossier, date);
  if (idea.place) return zoneOrUtc(idea.place.timeZoneId);
  const city = idea.city.trim().toLowerCase();
  if (city === '') return context.zoneId;
  const booked = (dossier?.segments ?? [])
    .flatMap((s) => [s.origin, destinationOf(s)])
    .find((p) => p.city.toLowerCase() === city);
  if (booked) return zoneOrUtc(booked.timeZoneId);
  const looked = resolvePlace(idea.city, context.near, context.zoneId);
  return looked.match === 'TYPED' ? context.zoneId : zoneOrUtc(looked.place.timeZoneId);
}

/** The day it is pencilled in for, or the first day of the trip that has not already gone. */
export function defaultScheduleDate(
  idea: Idea,
  dossier: TripDossier | null | undefined,
  now: number
): LocalDate {
  if (idea.plannedDate) return idea.plannedDate;
  const today = todayIn(zoneOrUtc(dossier?.trip.homeZoneId), now);
  if (!dossier) return today;
  const start = tripStartDate(dossier.trip);
  return compareDates(start, today) >= 0 ? start : today;
}
