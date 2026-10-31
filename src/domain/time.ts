// java.time, as Waymark's Kotlin code used it, in Temporal.
//
// The domain was written against ZonedDateTime, LocalDate and friends, and
// Temporal has the same model (an instant, a zone, a wall clock), so the port
// keeps the Kotlin structure instead of re-deriving it with offsets by hand.
// The polyfill gives every WebView the same behaviour; it can be dropped once
// every supported WebView ships Temporal.

import { Temporal } from 'temporal-polyfill';

export { Temporal };
export type ZonedDateTime = Temporal.ZonedDateTime;
export type LocalDate = Temporal.PlainDate;
export type LocalTime = Temporal.PlainTime;
export type LocalDateTime = Temporal.PlainDateTime;

const UTC = 'UTC';
const known = new Map<string, string>();

/** The zone named [id], or UTC when nobody knows that zone (Kotlin's `zoneOrUtc`). */
export function zoneOrUtc(id: string | null | undefined): string {
  if (!id) return UTC;
  const cached = known.get(id);
  if (cached !== undefined) return cached;
  let resolved: string;
  try {
    resolved = Temporal.Instant.fromEpochMilliseconds(0).toZonedDateTimeISO(id).timeZoneId;
  } catch {
    resolved = UTC;
  }
  known.set(id, resolved);
  return resolved;
}

export function isKnownZone(id: string): boolean {
  try {
    Temporal.Instant.fromEpochMilliseconds(0).toZonedDateTimeISO(id);
    return true;
  } catch {
    return false;
  }
}

/** `Instant.ofEpochMilli(ms).atZone(zone)`. */
export function zonedAt(epochMillis: number, zoneId: string): ZonedDateTime {
  return Temporal.Instant.fromEpochMilliseconds(epochMillis).toZonedDateTimeISO(zoneOrUtc(zoneId));
}

/** `ZonedDateTime.of(date, time, zone)`: a gap moves forward, an overlap takes the earlier offset. */
export function zonedOf(date: LocalDate, time: LocalTime, zoneId: string): ZonedDateTime {
  return date.toZonedDateTime({ timeZone: zoneOrUtc(zoneId), plainTime: time });
}

/** `ZonedDateTime.of(dateTime, zone)`. */
export function zonedOfDateTime(dateTime: LocalDateTime, zoneId: string): ZonedDateTime {
  return dateTime.toZonedDateTime(zoneOrUtc(zoneId), { disambiguation: 'compatible' });
}

/** `date.atStartOfDay(zone)`. */
export function startOfDay(date: LocalDate, zoneId: string): ZonedDateTime {
  return date.toZonedDateTime({ timeZone: zoneOrUtc(zoneId) });
}

export function localDate(year: number, month: number, day: number): LocalDate {
  return Temporal.PlainDate.from({ year, month, day }, { overflow: 'reject' });
}

export function localTime(hour: number, minute = 0, second = 0): LocalTime {
  return Temporal.PlainTime.from({ hour, minute, second }, { overflow: 'reject' });
}

export function localDateTime(year: number, month: number, day: number, hour = 0, minute = 0): LocalDateTime {
  return Temporal.PlainDateTime.from({ year, month, day, hour, minute }, { overflow: 'reject' });
}

/** `LocalDate.parse` for ISO dates; null for anything else. */
export function parseDate(text: string | null | undefined): LocalDate | null {
  if (!text || !/^\d{4}-\d{2}-\d{2}$/.test(text)) return null;
  try {
    return Temporal.PlainDate.from(text, { overflow: 'reject' });
  } catch {
    return null;
  }
}

/** `ChronoUnit.DAYS.between(a, b)`: whole days from [a] to [b]. */
export function daysBetween(a: LocalDate, b: LocalDate): number {
  return a.until(b, { largestUnit: 'day' }).days;
}

/** `Duration.between(a, b).toMinutes()`, truncated toward zero. */
export function minutesBetween(a: ZonedDateTime, b: ZonedDateTime): number {
  return Math.trunc((b.epochMilliseconds - a.epochMilliseconds) / 60_000);
}

export function compareDates(a: LocalDate, b: LocalDate): number {
  return Temporal.PlainDate.compare(a, b);
}

export function sameDate(a: LocalDate, b: LocalDate): boolean {
  return a.equals(b);
}

/** The later of two dates. */
export function maxDate(a: LocalDate, b: LocalDate): LocalDate {
  return compareDates(a, b) >= 0 ? a : b;
}

/** The earlier of two dates. */
export function minDate(a: LocalDate, b: LocalDate): LocalDate {
  return compareDates(a, b) <= 0 ? a : b;
}

/** Every date from [start] to [end], both included. */
export function datesThrough(start: LocalDate, end: LocalDate): LocalDate[] {
  const out: LocalDate[] = [];
  for (let d = start; compareDates(d, end) <= 0; d = d.add({ days: 1 })) out.push(d);
  return out;
}

/** The zone the device is set to. */
export function systemZone(): string {
  return Temporal.Now.timeZoneId();
}

/** Today in [zoneId]. */
export function todayIn(zoneId: string, nowMillis: number = Date.now()): LocalDate {
  return zonedAt(nowMillis, zoneId).toPlainDate();
}

/** Offset of [zoneId] at [epochMillis], in seconds east of UTC. */
export function offsetSeconds(zoneId: string, epochMillis: number): number {
  return zonedAt(epochMillis, zoneId).offsetNanoseconds / 1e9;
}

/** Kotlin's `Int` division: toward zero. */
export function idiv(a: number, b: number): number {
  return Math.trunc(a / b);
}
