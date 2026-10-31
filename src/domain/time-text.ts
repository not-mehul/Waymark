// Every string the app prints for a time, a duration, or a date. Kept in one
// place so the timeline, the map and the numbers never disagree.

import {
  daysBetween,
  isKnownZone,
  offsetSeconds,
  type LocalDate,
  type LocalTime,
  type ZonedDateTime
} from './time';

const DAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
const MONTHS = [
  'January',
  'February',
  'March',
  'April',
  'May',
  'June',
  'July',
  'August',
  'September',
  'October',
  'November',
  'December'
];

/**
 * The device's own choice of 12- or 24-hour clock, set at start-up so a
 * traveler who reads "4:20 PM" everywhere else on the phone reads it here too.
 */
export const clockSettings = { use24Hour: true };

const pad2 = (n: number) => String(n).padStart(2, '0');
const shortName = (name: string) => name.slice(0, 3);

export function clock(time: ZonedDateTime | LocalTime, use24Hour: boolean = clockSettings.use24Hour): string {
  if (use24Hour) return `${pad2(time.hour)}:${pad2(time.minute)}`;
  const hour12 = time.hour % 12 === 0 ? 12 : time.hour % 12;
  return `${hour12}:${pad2(time.minute)} ${time.hour < 12 ? 'AM' : 'PM'}`;
}

/** "Thursday 14 May". */
export function day(date: LocalDate): string {
  return `${DAYS[date.dayOfWeek - 1]} ${date.day} ${MONTHS[date.month - 1]}`;
}

/** "Thu 14 May". */
export function dayCompact(date: LocalDate): string {
  return `${shortName(DAYS[date.dayOfWeek - 1]!)} ${date.day} ${shortName(MONTHS[date.month - 1]!)}`;
}

/** "14 May". */
export function monthDay(date: LocalDate): string {
  return `${date.day} ${shortName(MONTHS[date.month - 1]!)}`;
}

/** The zone's short name at that moment: "BST", "CEST", else "GMT+9". */
export function zoneLabel(time: ZonedDateTime): string {
  const name = (locale: string) =>
    new Intl.DateTimeFormat(locale, { timeZone: time.timeZoneId, timeZoneName: 'short' })
      .formatToParts(time.epochMilliseconds)
      .find((p) => p.type === 'timeZoneName')?.value ?? time.timeZoneId;
  const us = name('en-US');
  if (!/^GMT[+-−]/.test(us)) return us;
  // en-US names only American zones; en-GB adds the European ones (BST, CEST).
  const gb = name('en-GB');
  return /^GMT[+-−]/.test(gb) ? us : gb;
}

export function dateRange(start: LocalDate, end: LocalDate): string {
  if (start.equals(end)) return dayCompact(start);
  if (start.year === end.year && start.month === end.month) {
    return `${start.day}–${end.day} ${shortName(MONTHS[start.month - 1]!)} ${start.year}`;
  }
  if (start.year === end.year) return `${monthDay(start)} – ${monthDay(end)} ${start.year}`;
  return `${monthDay(start)} ${start.year} – ${monthDay(end)} ${end.year}`;
}

const MINUTES_PER_DAY = 24 * 60;
const DAYS_PER_WEEK = 7;
/** Four weeks, not a calendar month: a duration has no particular month to be the length of. */
const DAYS_PER_MONTH = 28;

/**
 * A span, in the units a person would actually use for it: `2h 40m`, `3d 4h`,
 * `2w 3d`, `2mo 1w 3d`. Zero terms are dropped; negatives keep their sign.
 */
export function duration(minutes: number): string {
  const sign = minutes < 0 ? '−' : '';
  const total = Math.abs(Math.trunc(minutes));
  const days = Math.floor(total / MINUTES_PER_DAY);

  let terms: [number, string][];
  if (days >= DAYS_PER_MONTH) {
    terms = [
      [Math.floor(days / DAYS_PER_MONTH), 'mo'],
      [Math.floor((days % DAYS_PER_MONTH) / DAYS_PER_WEEK), 'w'],
      [days % DAYS_PER_WEEK, 'd']
    ];
  } else if (days >= DAYS_PER_WEEK) {
    terms = [
      [Math.floor(days / DAYS_PER_WEEK), 'w'],
      [days % DAYS_PER_WEEK, 'd']
    ];
  } else if (days >= 1) {
    terms = [
      [days, 'd'],
      [Math.floor((total % MINUTES_PER_DAY) / 60), 'h']
    ];
  } else {
    terms = [
      [Math.floor(total / 60), 'h'],
      [total % 60, 'm']
    ];
  }

  const written = terms
    .filter(([n]) => n > 0)
    .map(([n, unit]) => `${n}${unit}`)
    .join(' ');
  return sign + (written || `0${terms[terms.length - 1]![1]}`);
}

export function durationBetween(startMillis: number, endMillis: number): string {
  return duration(Math.trunc((endMillis - startMillis) / 60_000));
}

/** "in 3h 10m", "12m ago", "now". */
export function relative(targetMillis: number, nowMillis: number = Date.now()): string {
  const deltaMinutes = Math.trunc((targetMillis - nowMillis) / 60_000);
  if (Math.abs(deltaMinutes) < 1) return 'now';
  return deltaMinutes > 0 ? `in ${duration(deltaMinutes)}` : `${duration(-deltaMinutes)} ago`;
}

/** Day offset shown next to an arrival that lands on another date: "+1". */
export function dayOffsetSuffix(departure: ZonedDateTime, arrival: ZonedDateTime): string | null {
  const days = daysBetween(departure.toPlainDate(), arrival.toPlainDate());
  if (days === 0) return null;
  return days > 0 ? `+${days}` : `${days}`;
}

/** "Today", "Tomorrow", "Yesterday", else the long day. */
export function dayHeadline(date: LocalDate, today: LocalDate): string {
  if (date.equals(today)) return 'Today';
  if (date.equals(today.add({ days: 1 }))) return 'Tomorrow';
  if (date.equals(today.subtract({ days: 1 }))) return 'Yesterday';
  return day(date);
}

/** "UTC+1", "UTC−3:30", "UTC" — a zone's offset now, for naming a clock. */
export function utcOffset(zoneId: string, atMillis: number = Date.now()): string {
  const seconds = isKnownZone(zoneId) ? offsetSeconds(zoneId, atMillis) : 0;
  if (seconds === 0) return 'UTC';
  const sign = seconds > 0 ? '+' : '−';
  const minutes = Math.floor(Math.abs(seconds) / 60);
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return `UTC${sign}${hours}` + (rest === 0 ? '' : `:${pad2(rest)}`);
}

/** "+7h" — the shift a traveler's body is being asked to absorb. */
export function zoneShift(from: string, to: string, atMillis: number): string | null {
  const deltaMinutes = Math.trunc((offsetSeconds(to, atMillis) - offsetSeconds(from, atMillis)) / 60);
  if (deltaMinutes === 0) return null;
  return (deltaMinutes > 0 ? '+' : '−') + duration(Math.abs(deltaMinutes));
}
