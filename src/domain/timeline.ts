// Turns a pile of bookings into the one thing a traveler actually reads: a
// single column of days, in order, with the awkward bits between them named.

import { assessConnection, assessGap, type ConnectionVerdict } from './connection-risk';
import { distanceKm } from './geo';
import {
  chronological,
  destinationOf,
  hasCoordinates,
  involves,
  segmentEnd,
  segmentStart,
  type Flight,
  type Place,
  type Segment,
  type TripDossier
} from './model';
import { zonedAt, type LocalDate, type ZonedDateTime } from './time';
import { dayHeadline, dayOffsetSuffix, relative, zoneLabel } from './time-text';
import { estimateBest, type TransitEstimate } from './transit';

export type EventRole = 'WHOLE' | 'CHECK_IN' | 'CHECK_OUT';
const ROLE_ORDER: Record<EventRole, number> = { WHOLE: 0, CHECK_IN: 1, CHECK_OUT: 2 };

export type EventState = 'PAST' | 'ACTIVE' | 'FUTURE';

/** CONNECTION: plane to plane at one airport. TRANSFER: any other move. FREE_TIME: nowhere to be. */
export type LinkNature = 'CONNECTION' | 'TRANSFER' | 'FREE_TIME';

export interface DayBreak {
  type: 'day';
  date: LocalDate;
  headline: string;
  zoneLabel: string;
  sortKey: number;
  /** Unique within one timeline: a date can head the column twice across the date line. */
  key: string;
}

export interface TimelineEvent {
  type: 'event';
  segment: Segment;
  role: EventRole;
  state: EventState;
  localTime: ZonedDateTime;
  arrivalDayOffset: string | null;
  sortKey: number;
  id: string;
  key: string;
}

export interface TimelineLink {
  type: 'link';
  fromSegment: Segment;
  toSegment: Segment;
  nature: LinkNature;
  verdict: ConnectionVerdict | null;
  estimate: TransitEstimate | null;
  gapMinutes: number;
  sortKey: number;
  id: string;
  key: string;
}

export interface TimelineNow {
  type: 'now';
  label: string;
  sortKey: number;
  key: 'now';
}

export type TimelineEntry = DayBreak | TimelineEvent | TimelineLink | TimelineNow;

/** Gaps longer than this are the traveler's own time, not a transfer. */
const FREE_TIME_THRESHOLD_MINUTES = 240;

export function buildTimeline(
  dossier: TripDossier,
  options: { travelerFilter?: string | null; nowMillis?: number; checkedBags?: boolean } = {}
): TimelineEntry[] {
  const { travelerFilter = null, nowMillis = Date.now(), checkedBags = true } = options;
  const segments = chronological(
    dossier.segments.filter((s) => travelerFilter === null || involves(s, travelerFilter))
  );
  if (segments.length === 0) return [];

  const entries: TimelineEntry[] = [];
  const instances: [number, EventRole, Segment][] = [];
  for (const s of segments) {
    instances.push([s.startEpochMillis, s.kind === 'LODGING' ? 'CHECK_IN' : 'WHOLE', s]);
    if (s.kind === 'LODGING' && !segmentStart(s).toPlainDate().equals(segmentEnd(s).toPlainDate())) {
      instances.push([s.endEpochMillis, 'CHECK_OUT', s]);
    }
  }
  instances.sort(
    (a, b) =>
      a[0] - b[0] ||
      (a[2].id < b[2].id ? -1 : a[2].id > b[2].id ? 1 : 0) ||
      ROLE_ORDER[a[1]] - ROLE_ORDER[b[1]]
  );

  const linksBefore = new Map(timelineLinks(segments, dossier, checkedBags).map((l) => [l.toSegment.id, l]));

  let lastDate: LocalDate | null = null;
  let nowEmitted = false;

  for (const [millis, role, segment] of instances) {
    const localTime = role === 'CHECK_OUT' ? segmentEnd(segment) : segmentStart(segment);
    const date = localTime.toPlainDate();

    if (!nowEmitted && nowMillis < millis) {
      const label = relative(millis, nowMillis);
      entries.push({
        type: 'now',
        label: label.charAt(0).toUpperCase() + label.slice(1),
        sortKey: nowMillis,
        key: 'now'
      });
      nowEmitted = true;
    }

    if (lastDate === null || !date.equals(lastDate)) {
      // Ordered ahead of the link that may precede the day's first event.
      const sortKey = millis - 3;
      entries.push({
        type: 'day',
        date,
        headline: dayHeadline(date, zonedAt(nowMillis, localTime.timeZoneId).toPlainDate()),
        zoneLabel: zoneLabel(localTime),
        sortKey,
        key: `day:${date.toString()}@${sortKey}`
      });
      lastDate = date;
    }

    if (role !== 'CHECK_OUT') {
      const link = linksBefore.get(segment.id);
      if (link) entries.push(link);
    }

    const id = `${segment.id}:${role}`;
    entries.push({
      type: 'event',
      segment,
      role,
      state: stateOf(segment, nowMillis),
      localTime,
      arrivalDayOffset:
        role === 'CHECK_OUT' ? null : dayOffsetSuffix(segmentStart(segment), segmentEnd(segment)),
      sortKey: millis,
      id,
      key: id
    });
  }

  if (!nowEmitted) entries.push({ type: 'now', label: 'Trip complete', sortKey: nowMillis, key: 'now' });
  return entries;
}

function stateOf(s: Segment, nowMillis: number): EventState {
  if (nowMillis < s.startEpochMillis) return 'FUTURE';
  if (nowMillis > s.endEpochMillis) return 'PAST';
  return 'ACTIVE';
}

/**
 * The bits between the bookings — the part that goes wrong. Each traveler's
 * own itinerary is walked separately and the results merged: where the party
 * agrees on what comes before a booking, that link is drawn; where they
 * disagree (a split), there is no single connection to judge.
 */
export function timelineLinks(
  segments: readonly Segment[],
  dossier: TripDossier,
  checkedBags = true
): TimelineLink[] {
  const built = new Map<string, TimelineLink | null>();
  const predecessors = new Map<string, Set<string>>();

  for (const own of itineraries(segments, dossier)) {
    for (let i = 1; i < own.length; i++) {
      const previous = own[i - 1]!;
      const next = own[i]!;
      let before = predecessors.get(next.id);
      if (!before) predecessors.set(next.id, (before = new Set()));
      before.add(previous.id);
      const key = JSON.stringify([previous.id, next.id]);
      if (!built.has(key)) built.set(key, link(previous, next, checkedBags));
    }
  }

  return [...built.values()]
    .filter((l): l is TimelineLink => l !== null)
    .filter((l) => predecessors.get(l.toSegment.id)?.size === 1)
    .sort((a, b) => a.sortKey - b.sortKey);
}

/** One list per traveler, or one list for the whole trip when there is no party. */
function itineraries(segments: readonly Segment[], dossier: TripDossier): Segment[][] {
  const travelers = dossier.party.travelers;
  if (travelers.length === 0) return [chronological(segments)];
  return travelers.map((t) => chronological(segments.filter((s) => involves(s, t.id))));
}

/** The link between two consecutive bookings, or null where there is nothing to say. */
function link(previous: Segment, next: Segment, checkedBags: boolean): TimelineLink | null {
  const gapMinutes = Math.trunc((next.startEpochMillis - previous.endEpochMillis) / 60_000);
  const from = destinationOf(previous);
  const same = isSamePlace(from, next.origin);
  const bothFlights = previous.kind === 'FLIGHT' && next.kind === 'FLIGHT';

  // Stationary bookings (a hotel) overlap everything else by design.
  if (previous.kind === 'LODGING' || next.kind === 'LODGING') return null;
  if (same && gapMinutes >= 0 && gapMinutes <= FREE_TIME_THRESHOLD_MINUTES && !bothFlights) return null;

  const base = {
    type: 'link' as const,
    fromSegment: previous,
    toSegment: next,
    gapMinutes,
    sortKey: next.startEpochMillis - 2,
    id: `link:${previous.id}:${next.id}`,
    key: `link:${previous.id}:${next.id}`
  };

  if (bothFlights) {
    return {
      ...base,
      nature: same ? 'CONNECTION' : 'TRANSFER',
      verdict: assessConnection(previous as Flight, next as Flight, checkedBags),
      estimate: same ? null : estimateBest(from, next.origin)
    };
  }
  if (gapMinutes > FREE_TIME_THRESHOLD_MINUTES && same) {
    return { ...base, nature: 'FREE_TIME', verdict: null, estimate: null };
  }
  return {
    ...base,
    nature: 'TRANSFER',
    verdict: assessGap(from, next.origin, gapMinutes),
    estimate: estimateBest(from, next.origin)
  };
}

function isSamePlace(a: Place, b: Place): boolean {
  if (a.code != null && b.code != null) return a.code === b.code;
  if (hasCoordinates(a) && hasCoordinates(b)) return distanceKm(a, b) < 0.4;
  return a.name.toLowerCase() === b.name.toLowerCase();
}
