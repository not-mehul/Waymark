// The Costs tab's arithmetic and wording that is not the split itself:
// what an expense sheet saves, which currency a day was spent in, when the
// rates want fetching, and how a rate reads. Ported from CostsModel.kt and
// ExpenseModal.kt.

import { countryOf, currencyOfRegion } from '../catalog/countries';
import {
  localCurrency,
  type ExchangeRate,
  type Expense,
  type ExpenseCategory,
  type Money,
  type TripCosts,
  type TripDossier
} from '../domain/model';
import { compareDates, localTime, zonedOf, zoneOrUtc, type LocalDate } from '../domain/time';
import { dayCompact } from '../domain/time-text';
import { whereaboutsAt } from '../domain/whereabouts';
import type { RatesOutcome } from '../online/rates';

/** A live rate older than this is fetched again: the bank publishes on working days. */
const STALE_DAYS = 3;

export interface ExpenseDraft {
  description: string;
  amount: Money;
  paidBy: string;
  /** Who shares it; the whole party when everyone is ticked. */
  among: string[];
  spentOn: LocalDate;
  category: ExpenseCategory;
}

/** The currency the device is set to, or euros. */
export const deviceCurrency = (): string => localCurrency(currencyOfRegion);

/** The settling currency: the trip's choice, or the device's until somebody makes one. */
export const settleCurrencyOf = (costs: TripCosts): string => costs.settleCurrency ?? deviceCurrency();

/**
 * The expense to store. Everyone ticked is stored as everyone, so a later
 * addition to the party shares it too.
 */
export function expenseFrom(
  draft: ExpenseDraft,
  id: string,
  tripId: string,
  partyIds: readonly string[],
  existing: Expense | null,
  nowMillis: number
): Expense {
  const everyone = partyIds.length === draft.among.length && partyIds.every((p) => draft.among.includes(p));
  return {
    id: existing?.id ?? id,
    tripId,
    description: draft.description.trim(),
    amount: draft.amount,
    paidBy: draft.paidBy,
    splitAmong: everyone ? [] : [...draft.among],
    spentOn: draft.spentOn,
    category: draft.category,
    addedAtMillis: existing?.addedAtMillis ?? nowMillis
  };
}

/** The currency of wherever the trip is on [date]; [fallback] when that is not known. */
export function currencyOn(
  dossier: TripDossier | null | undefined,
  date: LocalDate,
  fallback: string
): string {
  if (!dossier) return fallback;
  const noon = zonedOf(date, localTime(12), zoneOrUtc(dossier.trip.homeZoneId)).epochMilliseconds;
  return countryOf(whereaboutsAt(dossier.segments, noon))?.currencyCode ?? fallback;
}

/** Currencies spent in, other than the settling one. */
export function foreignCurrencies(costs: TripCosts, settle: string): string[] {
  return [...new Set(costs.expenses.map((e) => e.amount.currency))].filter((c) => c !== settle).sort();
}

/** A currency with no rate, or a live one more than a few days old. */
export function ratesAreStale(costs: TripCosts, settle: string, today: LocalDate): boolean {
  const byCurrency = new Map(costs.rates.map((r) => [r.currency, r]));
  const cutoff = today.subtract({ days: STALE_DAYS });
  return foreignCurrencies(costs, settle).some((c) => {
    const rate = byCurrency.get(c);
    return !rate || (!rate.manual && compareDates(rate.asOf, cutoff) < 0);
  });
}

/** What the last attempt at the rates says under them. */
export function ratesNote(result: RatesOutcome): string | null {
  switch (result.outcome) {
    case 'updated': {
      let text = `Rates of ${dayCompact(result.asOf)}, as the European Central Bank published them.`;
      if (result.missing.length > 0) {
        text += ` It publishes none for ${[...result.missing].sort().join(', ')}: type one in.`;
      }
      return text;
    }
    case 'notAllowed':
      return null;
    case 'failed':
      return `Could not update the rates (${result.reason}). The last ones are kept.`;
  }
}

/** A rate as people read one: four significant figures, no exponent. */
export function rateText(perUnit: number): string {
  if (!(perUnit > 0) || !Number.isFinite(perUnit)) return '';
  const digits = Math.max(0, 3 - Math.floor(Math.log10(perUnit)));
  return String(Number(perUnit.toFixed(Math.min(digits, 20))));
}

/** A rate typed by hand: a positive number, with a comma or a point. */
export function parseRate(text: string): number | null {
  const value = Number(text.trim().replace(',', '.'));
  return text.trim() !== '' && Number.isFinite(value) && value > 0 ? value : null;
}

const KNOWN_CURRENCIES: ReadonlySet<string> = new Set(
  typeof Intl.supportedValuesOf === 'function' ? Intl.supportedValuesOf('currency') : []
);

export function isCurrency(code: string): boolean {
  return /^[A-Z]{3}$/.test(code) && (KNOWN_CURRENCIES.size === 0 || KNOWN_CURRENCIES.has(code));
}

/** The rate for [currency], if the trip has one. */
export const rateFor = (costs: TripCosts, currency: string): ExchangeRate | null =>
  costs.rates.find((r) => r.currency === currency) ?? null;
