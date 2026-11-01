// Which of a trip's bookings are close enough to say something about, and
// when the next one will be. A plain function over segments and a clock, so
// what is due is tested here rather than inferred from a phone that did or
// did not buzz. The Android side only sets the alarm and posts the result.

import { distanceKm } from './geo';
import {
  chronological,
  designator,
  groundModeLabel,
  hasCoordinates,
  segmentStart,
  shortLabel,
  type Flight,
  type Segment,
  type SegmentKind
} from './model';
import { clock, dayCompact, duration } from './time-text';
import { estimateBest, totalMinutes, transitSummary } from './transit';
import { whereaboutsBefore } from './whereabouts';

/** Minutes before something the traveler wants to hear about it; 0 is off. */
export type Lead = number;

export const LEAD_OFF: Lead = 0;
export const isOn = (lead: Lead) => lead > 0;

/** The choices offered for a booking's start. */
export const LEAD_CHOICES: readonly Lead[] = [0, 15, 30, 60, 2 * 60, 3 * 60, 6 * 60, 12 * 60, 24 * 60];

/** What a reminder is about: the booking starting, check-in opening, or time to go. */
export type ReminderMoment = 'START' | 'CHECK_IN' | 'LEAVE';
export const REMINDER_MOMENTS: readonly ReminderMoment[] = ['START', 'CHECK_IN', 'LEAVE'];
const SIGNATURE_PREFIX: Record<ReminderMoment, string> = {
  START: 'reminder',
  CHECK_IN: 'checkin',
  LEAVE: 'leave'
};

export type ReminderCategory = 'FLIGHTS' | 'LEAVE' | 'CHECK_IN' | 'GROUND' | 'BOOKINGS' | 'STAYS';

export interface ReminderCategoryInfo {
  kind: SegmentKind;
  moment: ReminderMoment;
  label: string;
  detail: string;
  default: Lead;
  choices: readonly Lead[];
}

/** In the order the settings sheet lists them. */
export const REMINDER_CATEGORIES: readonly ReminderCategory[] = [
  'FLIGHTS',
  'LEAVE',
  'CHECK_IN',
  'GROUND',
  'BOOKINGS',
  'STAYS'
];

export const REMINDER_CATEGORY: Record<ReminderCategory, ReminderCategoryInfo> = {
  FLIGHTS: {
    kind: 'FLIGHT',
    moment: 'START',
    label: 'Flights',
    detail: 'Before a departure',
    default: 3 * 60,
    choices: LEAD_CHOICES
  },
  LEAVE: {
    kind: 'FLIGHT',
    moment: 'LEAVE',
    label: 'Leave for the airport',
    detail: 'Warning before it is time to go',
    default: 30,
    choices: [0, 15, 30, 60]
  },
  CHECK_IN: {
    kind: 'FLIGHT',
    moment: 'CHECK_IN',
    label: 'Online check-in',
    detail: 'Before departure, when check-in usually opens',
    default: 24 * 60,
    choices: [0, 24 * 60, 36 * 60, 48 * 60]
  },
  GROUND: {
    kind: 'GROUND',
    moment: 'START',
    label: 'Trains and transfers',
    detail: 'Before a ground leg',
    default: 60,
    choices: LEAD_CHOICES
  },
  BOOKINGS: {
    kind: 'EXPERIENCE',
    moment: 'START',
    label: 'Bookings',
    detail: 'Before a table, a tour, a ticket',
    default: 60,
    choices: LEAD_CHOICES
  },
  STAYS: {
    kind: 'LODGING',
    moment: 'START',
    label: 'Stays',
    detail: 'Before a check-in',
    default: LEAD_OFF,
    choices: LEAD_CHOICES
  }
};

export function categoriesFor(kind: SegmentKind): ReminderCategory[] {
  return REMINDER_CATEGORIES.filter((c) => REMINDER_CATEGORY[c].kind === kind);
}

/** Every category's lead time. Absent means the category's own default. */
export class ReminderPreferences {
  static readonly DEFAULT = new ReminderPreferences();

  constructor(private readonly leads: Readonly<Partial<Record<ReminderCategory, Lead>>> = {}) {}

  get(category: ReminderCategory): Lead {
    return this.leads[category] ?? REMINDER_CATEGORY[category].default;
  }

  with(category: ReminderCategory, lead: Lead): ReminderPreferences {
    return new ReminderPreferences({ ...this.leads, [category]: lead });
  }

  get anyOn(): boolean {
    return REMINDER_CATEGORIES.some((c) => isOn(this.get(c)));
  }

  /** The stored form: only what differs from the defaults is kept. */
  toJSON(): Partial<Record<ReminderCategory, Lead>> {
    return { ...this.leads };
  }

  /** For the menu row: "Flights 3h · Bookings 1h", or what is off. */
  summary(): string {
    const on = REMINDER_CATEGORIES.filter(
      (c) => isOn(this.get(c)) && REMINDER_CATEGORY[c].moment === 'START'
    );
    if (on.length === 0) return 'Off for everything';
    return on.map((c) => `${REMINDER_CATEGORY[c].label} ${duration(this.get(c))}`).join(' · ');
  }
}

/** One reminder that is ready to be posted. */
export interface DueReminder {
  segmentId: string;
  tripId: string;
  moment: ReminderMoment;
  /** What the booking is called, for the notification's own record. */
  label: string;
  /** Stable across runs, so a thing is announced once rather than every check. */
  signature: string;
  headline: string;
  detail: string;
  startEpochMillis: number;
}

/** Online check-in closes about an hour before departure. */
const CHECK_IN_CLOSES_MINUTES = 60;
/** "Time to leave" stays useful a little after the time has come. */
const LEAVE_GRACE_MINUTES = 30;
const AIRPORT_DOMESTIC_MINUTES = 120;
const AIRPORT_INTERNATIONAL_MINUTES = 180;
/** Farther than this from the airport is not a journey "to the airport". */
const MAX_AIRPORT_JOURNEY_KM = 150;

interface Plan {
  segment: Segment;
  category: ReminderCategory;
  fireAtMillis: number;
  usefulUntilMillis: number;
}

/**
 * Everything due at [nowMillis]. [itinerary] is every booking of the trips
 * concerned: the time to leave for an airport depends on where the trip is
 * before the flight.
 */
export function dueReminders(
  itinerary: readonly Segment[],
  preferences: ReminderPreferences,
  nowMillis: number
): DueReminder[] {
  return plans(itinerary, preferences)
    .filter((p) => nowMillis >= p.fireAtMillis && nowMillis < p.usefulUntilMillis)
    .map((p) => {
      const moment = REMINDER_CATEGORY[p.category].moment;
      return {
        segmentId: p.segment.id,
        tripId: p.segment.tripId,
        moment,
        label: labelOf(p.segment),
        signature: reminderSignature(moment, p.segment),
        headline: reminderHeadline(p.segment, moment, nowMillis, itinerary),
        detail: detail(p.segment, moment, itinerary),
        startEpochMillis: p.segment.startEpochMillis
      };
    });
}

/** When the next reminder falls due after [nowMillis], or null if none ever will. */
export function nextDueAt(
  itinerary: readonly Segment[],
  preferences: ReminderPreferences,
  nowMillis: number
): number | null {
  const upcoming = plans(itinerary, preferences)
    .map((p) => p.fireAtMillis)
    .filter((t) => t > nowMillis);
  return upcoming.length === 0 ? null : Math.min(...upcoming);
}

/** A reminder's text as of now — what the trip screen's banner says, live. */
export function reminderHeadline(
  segment: Segment,
  moment: ReminderMoment,
  nowMillis: number,
  itinerary: readonly Segment[] = [segment]
): string {
  switch (moment) {
    case 'START':
      return startHeadline(segment, minutesUntil(segment.startEpochMillis, nowMillis));
    case 'CHECK_IN':
      return `Check in online for ${labelOf(segment)}`;
    case 'LEAVE': {
      const by = leaveBy(segment, itinerary);
      const where = shortLabel(segment.origin);
      return by === null || nowMillis >= by
        ? `Time to leave for ${where}`
        : `Leave for ${where} in ${duration(minutesUntil(by, nowMillis))}`;
    }
  }
}

/** Which moment a stored reminder was about, from its signature. */
export function momentOf(signature: string): ReminderMoment {
  return REMINDER_MOMENTS.find((m) => signature.startsWith(`${SIGNATURE_PREFIX[m]}|`)) ?? 'START';
}

export function reminderSignature(moment: ReminderMoment, segment: Segment): string {
  return `${SIGNATURE_PREFIX[moment]}|${segment.id}|${segment.startEpochMillis}`;
}

function plans(itinerary: readonly Segment[], preferences: ReminderPreferences): Plan[] {
  const out: Plan[] = [];
  for (const segment of chronological(itinerary)) {
    for (const category of categoriesFor(segment.kind)) {
      const lead = preferences.get(category);
      if (!isOn(lead)) continue;
      const leadMillis = lead * 60_000;
      switch (REMINDER_CATEGORY[category].moment) {
        case 'START':
          out.push({
            segment,
            category,
            fireAtMillis: segment.startEpochMillis - leadMillis,
            usefulUntilMillis: segment.startEpochMillis + 1
          });
          break;
        case 'CHECK_IN':
          out.push({
            segment,
            category,
            fireAtMillis: segment.startEpochMillis - leadMillis,
            usefulUntilMillis: segment.startEpochMillis - CHECK_IN_CLOSES_MINUTES * 60_000
          });
          break;
        case 'LEAVE': {
          const by = leaveBy(segment, itinerary);
          if (by !== null) {
            out.push({
              segment,
              category,
              fireAtMillis: by - leadMillis,
              usefulUntilMillis: Math.min(by + LEAVE_GRACE_MINUTES * 60_000, segment.startEpochMillis)
            });
          }
          break;
        }
      }
    }
  }
  return out;
}

/**
 * When to set off for a flight's airport: the journey there from wherever
 * the trip is before it, plus time at the airport. Null when there is no
 * journey to make — a connection, or a place the app cannot locate.
 */
export function leaveBy(flight: Segment, itinerary: readonly Segment[]): number | null {
  if (flight.kind !== 'FLIGHT') return null;
  const from = departurePoint(flight, itinerary);
  if (!from || !hasCoordinates(from) || !hasCoordinates(flight.origin)) return null;
  if (from.code != null && from.code === flight.origin.code) return null;
  const km = distanceKm(from, flight.origin);
  if (km < 0.5 || km > MAX_AIRPORT_JOURNEY_KM) return null;
  const journey = totalMinutes(estimateBest(from, flight.origin));
  return flight.startEpochMillis - (airportMinutes(flight) + journey) * 60_000;
}

function airportMinutes(flight: Flight): number {
  const from = flight.origin.country;
  const to = flight.destination.country;
  const international = from.trim() !== '' && to.trim() !== '' && from !== to;
  return international ? AIRPORT_INTERNATIONAL_MINUTES : AIRPORT_DOMESTIC_MINUTES;
}

function minutesUntil(targetMillis: number, nowMillis: number): number {
  return Math.trunc(Math.max(0, targetMillis - nowMillis) / 60_000);
}

function labelOf(s: Segment): string {
  switch (s.kind) {
    case 'FLIGHT':
      return designator(s);
    case 'LODGING':
      return s.propertyName;
    case 'GROUND':
      return groundModeLabel(s.mode);
    case 'EXPERIENCE':
      return s.name;
  }
}

function startHeadline(s: Segment, minutesOut: number): string {
  const whenText = minutesOut < 1 ? 'now' : `in ${duration(minutesOut)}`;
  switch (s.kind) {
    case 'FLIGHT':
      return `${designator(s)} departs ${whenText}`;
    case 'LODGING':
      return `Check in at ${s.propertyName} ${whenText}`;
    case 'GROUND':
      return `${groundModeLabel(s.mode)} to ${shortLabel(s.destination)} ${whenText}`;
    case 'EXPERIENCE':
      return `${s.name} ${whenText}`;
  }
}

function detail(s: Segment, moment: ReminderMoment, itinerary: readonly Segment[]): string {
  switch (moment) {
    case 'START':
      return startDetail(s);
    case 'CHECK_IN': {
      const start = segmentStart(s);
      return `Departs ${dayCompact(start.toPlainDate())} at ${clock(start)} from ${shortLabel(s.origin)}`;
    }
    case 'LEAVE':
      return leaveDetail(s, itinerary);
  }
}

function leaveDetail(flight: Segment, itinerary: readonly Segment[]): string {
  const from = departurePoint(flight, itinerary);
  const airport = flight.kind === 'FLIGHT' ? airportMinutes(flight) : AIRPORT_DOMESTIC_MINUTES;
  let text = '';
  if (from) text += `${transitSummary(estimateBest(from, flight.origin))} from ${from.name}, `;
  return text + `to be there ${duration(airport)} before ${labelOf(flight)}`;
}

/** Where the traveler sets off from for a flight: wherever the trip was just before it. */
function departurePoint(flight: Segment, itinerary: readonly Segment[]) {
  return whereaboutsBefore(
    itinerary.filter((s) => s.id !== flight.id),
    flight.startEpochMillis - 1
  );
}

const orBlank = (s: string, fallback: string) => (s.trim() === '' ? fallback : s);

function startDetail(s: Segment): string {
  switch (s.kind) {
    case 'FLIGHT': {
      let text = shortLabel(s.origin);
      if (s.departureTerminal != null) text += `, terminal ${s.departureTerminal}`;
      if (s.departureGate != null) text += `, gate ${s.departureGate}`;
      return `${text} → ${shortLabel(s.destination)}`;
    }
    case 'LODGING':
      return [orBlank(s.origin.city, s.origin.name), s.roomDescription].filter((x) => x != null).join(' · ');
    case 'GROUND':
      return (
        `${shortLabel(s.origin)} → ${shortLabel(s.destination)}` +
        (s.provider != null ? ` · ${s.provider}` : '')
      );
    case 'EXPERIENCE':
      return [orBlank(s.origin.city, s.origin.name), s.category.trim() === '' ? null : s.category]
        .filter((x) => x != null)
        .join(' · ');
  }
}
