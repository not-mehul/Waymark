// Exact decimal arithmetic for the few places Kotlin used BigDecimal or
// Java's HALF_UP formatting, so results match to the cent and the digit.

/** A decimal: [unscaled] × 10^−[scale]. */
export interface Decimal {
  unscaled: bigint;
  scale: number;
}

/**
 * A double as Java's `BigDecimal.valueOf(double)` sees it: the shortest
 * decimal that reads back as the same double.
 */
export function decimalOf(value: number): Decimal {
  if (!Number.isFinite(value)) throw new RangeError(`Not a finite number: ${value}`);
  const [mantissa = '0', exponent = '0'] = String(value).toLowerCase().split('e');
  const negative = mantissa.startsWith('-');
  const [whole = '0', fraction = ''] = mantissa.replace('-', '').split('.');
  let unscaled = BigInt(whole + fraction);
  let scale = fraction.length - Number(exponent);
  if (scale < 0) {
    unscaled *= 10n ** BigInt(-scale);
    scale = 0;
  }
  return { unscaled: negative ? -unscaled : unscaled, scale };
}

export function multiply(a: Decimal, b: Decimal): Decimal {
  return { unscaled: a.unscaled * b.unscaled, scale: a.scale + b.scale };
}

export type Rounding = 'HALF_EVEN' | 'HALF_UP';

/** `setScale(scale, rounding)`: the decimal re-expressed with exactly [scale] places. */
export function setScale(d: Decimal, scale: number, rounding: Rounding): Decimal {
  if (scale >= d.scale) return { unscaled: d.unscaled * 10n ** BigInt(scale - d.scale), scale };
  const divisor = 10n ** BigInt(d.scale - scale);
  const negative = d.unscaled < 0n;
  const magnitude = negative ? -d.unscaled : d.unscaled;
  let quotient = magnitude / divisor;
  const twice = (magnitude % divisor) * 2n;
  if (twice > divisor || (twice === divisor && (rounding === 'HALF_UP' || quotient % 2n === 1n)))
    quotient += 1n;
  return { unscaled: negative ? -quotient : quotient, scale };
}

export function toPlainString(d: Decimal): string {
  const negative = d.unscaled < 0n;
  const digits = (negative ? -d.unscaled : d.unscaled).toString().padStart(d.scale + 1, '0');
  const text = d.scale === 0 ? digits : `${digits.slice(0, -d.scale)}.${digits.slice(-d.scale)}`;
  return negative ? `-${text}` : text;
}

/** Java's `String.format("%.Nf", value)`: HALF_UP on the shortest decimal form. */
export function formatFixed(value: number, places: number): string {
  return toPlainString(setScale(decimalOf(value), places, 'HALF_UP'));
}
