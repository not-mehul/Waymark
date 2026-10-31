// A flight or a ground leg as it happens: what the ongoing notification (a
// Live Update on Android 16+) shows from a little before departure until
// arrival. Shown only for kinds with departure reminders on.

import { durationMillis, type Segment, type SegmentKind } from './model';
import { isOn, REMINDER_CATEGORIES, REMINDER_CATEGORY, type ReminderPreferences } from './reminders';
import { duration } from './time-text';

export type JourneyPhase = 'DEPARTING' | 'UNDER_WAY';

export interface LiveJourney {
  segment: Segment;
  phase: JourneyPhase;
  /** How far along, out of [PROGRESS_MAX]; zero until departure. */
  progress: number;
  /** The few characters the status bar has room for: "40m", "6h 5m". */
  chip: string;
  /** "Departs in 40m", "Lands in 6h 5m". */
  headline: string;
  /** The moment the chip is counting down to. */
  targetMillis: number;
}

export const PROGRESS_MAX = 1_000;
/** How often the notification is brought up to date while it is showing. */
export const TICK_MILLIS = 15 * 60_000;
const MINUTE = 60_000;

/** How long before departure the journey starts showing. */
function leadMillis(kind: SegmentKind): number {
  return kind === 'FLIGHT' ? 3 * 60 * MINUTE : 30 * MINUTE;
}

function shown(s: Segment, preferences: ReminderPreferences): boolean {
  return (
    (s.kind === 'FLIGHT' || s.kind === 'GROUND') &&
    s.endEpochMillis > s.startEpochMillis &&
    REMINDER_CATEGORIES.some((c) => {
      const info = REMINDER_CATEGORY[c];
      return info.kind === s.kind && info.moment === 'START' && isOn(preferences.get(c));
    })
  );
}

/** [first, end) */
function windowOf(s: Segment): [number, number] {
  return [s.startEpochMillis - leadMillis(s.kind), s.endEpochMillis];
}

const inWindow = (s: Segment, now: number) => {
  const [first, end] = windowOf(s);
  return now >= first && now < end;
};

/** Whole minutes until [targetMillis], rounded up so "1m" means not yet. */
function minutesUntil(targetMillis: number, nowMillis: number): number {
  return Math.trunc((targetMillis - nowMillis + MINUTE - 1) / MINUTE);
}

/** The journey to show now, if any: the earliest-starting one in its window. */
export function liveJourneyOf(
  itinerary: readonly Segment[],
  preferences: ReminderPreferences,
  nowMillis: number
): LiveJourney | null {
  let segment: Segment | null = null;
  for (const s of itinerary) {
    if (shown(s, preferences) && inWindow(s, nowMillis)) {
      if (segment === null || s.startEpochMillis < segment.startEpochMillis) segment = s;
    }
  }
  if (segment === null) return null;
  const departed = nowMillis >= segment.startEpochMillis;
  const target = departed ? segment.endEpochMillis : segment.startEpochMillis;
  const left = duration(minutesUntil(target, nowMillis));
  return {
    segment,
    phase: departed ? 'UNDER_WAY' : 'DEPARTING',
    progress: departed
      ? Math.trunc(((nowMillis - segment.startEpochMillis) * PROGRESS_MAX) / durationMillis(segment))
      : 0,
    chip: left,
    headline: !departed
      ? `Departs in ${left}`
      : segment.kind === 'FLIGHT'
        ? `Lands in ${left}`
        : `Arrives in ${left}`,
    targetMillis: target
  };
}

/**
 * When the notification next needs redrawing: the next tick while one is
 * showing, its departure or arrival if sooner, or when the next window opens.
 */
export function nextJourneyChangeAt(
  itinerary: readonly Segment[],
  preferences: ReminderPreferences,
  nowMillis: number
): number | null {
  const candidates = itinerary.filter((s) => shown(s, preferences));
  const live = liveJourneyOf(candidates, preferences, nowMillis);
  const openings = candidates.map((s) => windowOf(s)[0]).filter((t) => t > nowMillis);
  const opening = openings.length === 0 ? null : Math.min(...openings);
  const turning = live
    ? Math.min(nowMillis + TICK_MILLIS, Math.max(live.targetMillis, nowMillis + MINUTE))
    : null;
  const all = [opening, turning].filter((t): t is number => t !== null);
  return all.length === 0 ? null : Math.min(...all);
}
