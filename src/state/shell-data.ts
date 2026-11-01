// What the page hands the Android shell so that it can act while the app is
// closed: the reminders to post and when, the journeys to draw as a Live
// Update, and the bookings the home-screen widget lists. Worked out here, from
// the same logic the screens use; the shell only keeps time.

import { journeyWindows, type JourneyWindow } from '../domain/live-journey';
import { segmentStart, segmentTitle, shortLabel, type Segment, type Trip } from '../domain/model';
import { scheduledReminders, type ReminderPreferences, type ScheduledReminder } from '../domain/reminders';
import { clock, dayCompact } from '../domain/time-text';

export interface ShellSchedule {
  reminders: ScheduledReminder[];
  journeys: JourneyWindow[];
}

/** Far more than any trip has pending; it keeps the bridge message small whatever happens. */
export const MAX_SCHEDULED = 200;

export function shellSchedule(
  itinerary: readonly Segment[],
  preferences: ReminderPreferences,
  nowMillis: number
): ShellSchedule {
  return {
    reminders: scheduledReminders(itinerary, preferences, nowMillis).slice(0, MAX_SCHEDULED),
    journeys: journeyWindows(itinerary, preferences, nowMillis).slice(0, MAX_SCHEDULED)
  };
}

/** One booking as the widget lists it. The shell works out "now", "next" and "in 3h" as time passes. */
export interface WidgetEntry {
  tripId: string;
  tripName: string;
  segmentId: string;
  title: string;
  startMillis: number;
  endMillis: number;
  /** "Thu 14 May", in the booking's own time zone. */
  day: string;
  /** "09:40", in the booking's own time zone and the phone's clock style. */
  clock: string;
  /** Where it starts: "London Heathrow". */
  place: string;
}

/**
 * Enough bookings that the widget still has a next one when the app has not
 * been opened for a while: it drops each as it ends.
 */
export const WIDGET_ENTRIES = 12;

export function widgetEntries(next: readonly (readonly [Trip, Segment])[]): WidgetEntry[] {
  return next.slice(0, WIDGET_ENTRIES).map(([trip, segment]) => {
    const start = segmentStart(segment);
    return {
      tripId: trip.id,
      tripName: trip.name,
      segmentId: segment.id,
      title: segmentTitle(segment),
      startMillis: segment.startEpochMillis,
      endMillis: segment.endEpochMillis,
      day: dayCompact(start.toPlainDate()),
      clock: clock(start),
      place: shortLabel(segment.origin)
    };
  });
}
