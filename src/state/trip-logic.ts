// The parts of the open trip that are worked out rather than stored, kept
// apart from the reactive model so they can be tested on their own. Ported
// from TripViewModel's companion object.

import { ideasThatFit } from '../domain/ideas';
import {
  destinationOf,
  segmentEnd,
  segmentStart,
  type Idea,
  type RaisedReminder,
  type Segment,
  type Trip,
  type TripDossier
} from '../domain/model';
import { momentOf, reminderHeadline } from '../domain/reminders';
import { localTime, minDate, maxDate, startOfDay, zoneOrUtc, zonedOf, type LocalDate } from '../domain/time';
import type { TimelineEntry } from '../domain/timeline';

/** The reminder banner at the top of the trip, counting down rather than frozen. */
export interface ReminderBanner {
  segmentId: string;
  headline: string;
  detail: string;
}

/**
 * The newest reminder not dismissed for a booking that has not yet started,
 * its headline worked out again from the clock: "departs in 40m" an hour
 * later, rather than the "in 2h 55m" it was raised with.
 */
export function reminderBanner(
  dossier: TripDossier,
  raised: readonly RaisedReminder[],
  nowMillis: number
): ReminderBanner | null {
  const byId = new Map(dossier.segments.map((s) => [s.id, s]));
  let best: [RaisedReminder, Segment] | null = null;
  for (const reminder of raised) {
    if (reminder.acknowledged) continue;
    const segment = byId.get(reminder.segmentId);
    if (!segment || segment.startEpochMillis <= nowMillis) continue;
    if (!best || reminder.raisedAtMillis > best[0].raisedAtMillis) best = [reminder, segment];
  }
  if (!best) return null;
  const [reminder, segment] = best;
  return {
    segmentId: segment.id,
    headline: reminderHeadline(segment, momentOf(reminder.signature), nowMillis, dossier.segments),
    detail: reminder.detail
  };
}

/**
 * For each stretch of free time still ahead, the saved ideas in that place
 * that fit inside it: the timeline already names the gap; this says what
 * could go in it. Keyed by the link's key.
 */
export function freeTimeIdeas(
  timeline: readonly TimelineEntry[],
  ideas: readonly Idea[],
  nowMillis: number
): Map<string, Idea[]> {
  const fits = new Map<string, Idea[]>();
  for (const entry of timeline) {
    if (
      entry.type !== 'link' ||
      entry.nature !== 'FREE_TIME' ||
      entry.toSegment.startEpochMillis <= nowMillis
    ) {
      continue;
    }
    const where = destinationOf(entry.fromSegment);
    const local = ideas.filter(
      (idea) => idea.city === '' || where.city === '' || idea.city.toLowerCase() === where.city.toLowerCase()
    );
    const found = ideasThatFit(local, where, entry.gapMinutes, 3);
    if (found.length > 0) fits.set(entry.key, found);
  }
  return fits;
}

/**
 * A trip renamed, restated or moved. The window is two instants in the trip's
 * own zone: the first day from midnight, the last day to 23:59, so a trip that
 * ends on the 14th still contains a flight at 22:00 on the 14th.
 */
export function editedTrip(
  trip: Trip,
  name: string,
  destinationSummary: string,
  start: LocalDate,
  end: LocalDate
): Trip {
  const zone = zoneOrUtc(trip.homeZoneId);
  const first = minDate(start, end);
  const last = maxDate(start, end);
  return {
    ...trip,
    name: name.trim() || destinationSummary.trim() || trip.name,
    destinationSummary: destinationSummary.trim(),
    startEpochMillis: startOfDay(first, zone).epochMilliseconds,
    endEpochMillis: zonedOf(last, localTime(23, 59), zone).epochMilliseconds
  };
}

/** The first and last day anything is booked, each on its own clock; null with nothing booked. */
export function bookedSpan(segments: readonly Segment[]): [LocalDate, LocalDate] | null {
  if (segments.length === 0) return null;
  let first = segmentStart(segments[0]!).toPlainDate();
  let last = segmentEnd(segments[0]!).toPlainDate();
  for (const s of segments) {
    first = minDate(first, segmentStart(s).toPlainDate());
    last = maxDate(last, segmentEnd(s).toPlainDate());
  }
  return [first, last];
}
