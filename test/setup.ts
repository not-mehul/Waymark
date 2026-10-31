// Temporal objects keep their fields in internal slots, so a structural
// comparison would call any two dates equal. Compare them by value instead,
// as the Kotlin tests did with java.time's equals().
import { expect } from 'vitest';
import { Temporal } from 'temporal-polyfill';

const temporalTypes = [
  Temporal.PlainDate,
  Temporal.PlainTime,
  Temporal.PlainDateTime,
  Temporal.ZonedDateTime,
  Temporal.Instant,
  Temporal.Duration
];

const isTemporal = (value: unknown) => temporalTypes.some((type) => value instanceof type);

expect.addEqualityTesters([
  function temporalEquals(a: unknown, b: unknown) {
    if (!isTemporal(a) && !isTemporal(b)) return undefined;
    if (!isTemporal(a) || !isTemporal(b) || a!.constructor !== b!.constructor) return false;
    return String(a) === String(b);
  }
]);
