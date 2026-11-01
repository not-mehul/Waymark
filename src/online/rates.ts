// The day's reference rates, as the European Central Bank publishes them,
// read through Frankfurter: about thirty currencies, every working day, no
// key. Asked for only when the traveler has turned online extras on.

import type { CostRepository } from '../db/costs';
import { parseDate, type LocalDate } from '../domain/time';

export const RATES_URL = 'https://api.frankfurter.dev/v1/latest?base=EUR';

export interface LiveRates {
  /** Units of each currency per euro; the euro itself is 1. */
  perEuro: Map<string, number>;
  date: LocalDate;
}

export class RatesError extends Error {}

export function parseLiveRates(text: string): LiveRates {
  let root: unknown;
  try {
    root = JSON.parse(text);
  } catch {
    throw new RatesError('The rates service replied with something unreadable');
  }
  const reply = root as { rates?: unknown; base?: unknown; date?: unknown };
  if (typeof reply.rates !== 'object' || reply.rates === null) throw new RatesError('No rates in the reply');
  const base = typeof reply.base === 'string' ? reply.base : 'EUR';
  if (base !== 'EUR') throw new RatesError(`Rates against ${base}, not the euro`);
  const date = parseDate(typeof reply.date === 'string' ? reply.date : null);
  if (!date) throw new RatesError('No date in the reply');
  const perEuro = new Map<string, number>();
  for (const [code, value] of Object.entries(reply.rates as Record<string, unknown>)) {
    if (typeof value === 'number' && Number.isFinite(value)) perEuro.set(code, value);
  }
  perEuro.set('EUR', 1);
  return { perEuro, date };
}

/**
 * How many units of [settle] one unit of each of [currencies] is worth, for
 * those both sides of which the bank publishes.
 */
export function perUnitIn(
  live: LiveRates,
  settle: string,
  currencies: Iterable<string>
): Map<string, number> {
  const out = new Map<string, number>();
  const settlePerEuro = live.perEuro.get(settle);
  if (settlePerEuro === undefined) return out;
  for (const currency of currencies) {
    if (currency === settle) continue;
    const perEuro = live.perEuro.get(currency);
    if (perEuro !== undefined && perEuro > 0) out.set(currency, settlePerEuro / perEuro);
  }
  return out;
}

export type RatesOutcome =
  /** Updated; [missing] are the currencies the bank does not publish. */
  | { outcome: 'updated'; asOf: LocalDate; missing: string[] }
  | { outcome: 'notAllowed' }
  | { outcome: 'failed'; reason: string };

export type FetchText = (url: string) => Promise<string>;

export const fetchText: FetchText = async (url) => {
  const reply = await fetch(url, { headers: { Accept: 'application/json' } });
  if (!reply.ok) throw new RatesError(`HTTP ${reply.status}`);
  return reply.text();
};

/** Brings a trip's rates up to date, when the traveler has let the app go online. */
export async function refreshLiveRates(
  costs: CostRepository,
  tripId: string,
  settle: string,
  currencies: Iterable<string>,
  allowed: boolean,
  today: LocalDate,
  get: FetchText = fetchText
): Promise<RatesOutcome> {
  if (!allowed) return { outcome: 'notAllowed' };
  const foreign = [...new Set(currencies)].filter((c) => c !== settle);
  if (foreign.length === 0) return { outcome: 'updated', asOf: today, missing: [] };
  let live: LiveRates;
  try {
    live = parseLiveRates(await get(RATES_URL));
  } catch (error) {
    return {
      outcome: 'failed',
      reason: error instanceof RatesError ? error.message : 'No connection'
    };
  }
  const perUnit = perUnitIn(live, settle, foreign);
  await costs.applyLiveRates(tripId, perUnit, live.date);
  const missing = live.perEuro.has(settle) ? foreign.filter((c) => !perUnit.has(c)) : foreign;
  return { outcome: 'updated', asOf: live.date, missing };
}
