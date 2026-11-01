// Who paid, who owes, and the fewest payments that square it. Everything is
// worked in the smallest unit of the settling currency; the cents a split
// cannot share evenly go one each to the first people in the party.

import { decimalOf, multiply, setScale } from './decimal';
import {
  currencyDigits,
  EXPENSE_CATEGORIES,
  type ExchangeRate,
  type Expense,
  type ExpenseCategory,
  type Money
} from './model';

export interface Balance {
  travelerId: string;
  /** What they paid, converted. */
  paid: number;
  /** Their part of everything they share. */
  share: number;
}

/** Positive: they are owed. Negative: they owe. */
export function net(b: Balance): number {
  return b.paid - b.share;
}

/** One payment that settles part of the trip. */
export interface Transfer {
  from: string;
  to: string;
  amount: number;
}

export interface CostSummary {
  currency: string;
  total: number;
  balances: Balance[];
  transfers: Transfer[];
  /** In category order; only categories with spending. */
  byCategory: Map<ExpenseCategory, number>;
  /** Expenses in a currency the trip has no rate for: listed, not counted. */
  unconverted: Expense[];
}

export function isSettled(s: CostSummary): boolean {
  return s.transfers.length === 0;
}

/** [amount] in [settle], or null when there is no rate for its currency. */
export function convert(
  amount: Money,
  settle: string,
  rates: ReadonlyMap<string, ExchangeRate>
): number | null {
  if (amount.currency === settle) return amount.minor;
  const rate = rates.get(amount.currency)?.perUnit;
  if (rate === undefined || !(rate > 0)) return null;
  const value = multiply(
    { unscaled: BigInt(amount.minor), scale: currencyDigits(amount.currency) },
    decimalOf(rate)
  );
  return Number(setScale(value, currencyDigits(settle), 'HALF_EVEN').unscaled);
}

/**
 * The trip's spending, settled in [settle]. [party] is the trip's party in
 * its own order, which decides who carries an odd cent; anyone who paid or
 * shares an expense but has since left is still counted.
 */
export function summarizeCosts(
  expenses: readonly Expense[],
  party: readonly string[],
  settle: string,
  rates: readonly ExchangeRate[]
): CostSummary {
  const byCurrency = new Map(rates.map((r) => [r.currency, r]));
  const paid = new Map<string, number>();
  const share = new Map<string, number>();
  const byCategory = new Map<ExpenseCategory, number>();
  const unconverted: Expense[] = [];
  let total = 0;
  const add = <K>(map: Map<K, number>, key: K, n: number) => map.set(key, (map.get(key) ?? 0) + n);
  const rank = (id: string) => {
    const i = party.indexOf(id);
    return i < 0 ? Number.MAX_SAFE_INTEGER : i;
  };

  for (const expense of [...expenses].sort((a, b) => a.addedAtMillis - b.addedAtMillis)) {
    const amount = convert(expense.amount, settle, byCurrency);
    if (amount === null) {
      unconverted.push(expense);
      continue;
    }
    const among = [...new Set(expense.splitAmong.length > 0 ? expense.splitAmong : party)].sort(
      (a, b) => rank(a) - rank(b)
    );
    if (among.length === 0) {
      unconverted.push(expense);
      continue;
    }
    total += amount;
    add(paid, expense.paidBy, amount);
    add(byCategory, expense.category, amount);
    const each = Math.trunc(amount / among.length);
    const left = amount - each * among.length;
    among.forEach((id, index) => add(share, id, each + (index < left ? 1 : 0)));
  }

  const everyone = [...new Set([...party, ...paid.keys(), ...share.keys()])];
  const balances = everyone.map((id) => ({
    travelerId: id,
    paid: paid.get(id) ?? 0,
    share: share.get(id) ?? 0
  }));
  const ordered = new Map(
    EXPENSE_CATEGORIES.filter((c) => byCategory.has(c)).map((c) => [c, byCategory.get(c)!] as const)
  );
  return {
    currency: settle,
    total,
    balances,
    transfers: settleBalances(balances),
    byCategory: ordered,
    unconverted
  };
}

/**
 * The payments that bring every balance to zero: the largest debt paid to
 * the largest credit, repeatedly. Nobody both pays and is paid.
 */
export function settleBalances(balances: readonly Balance[]): Transfer[] {
  const owing: [string, number][] = balances.filter((b) => net(b) < 0).map((b) => [b.travelerId, -net(b)]);
  const owed: [string, number][] = balances.filter((b) => net(b) > 0).map((b) => [b.travelerId, net(b)]);
  const transfers: Transfer[] = [];
  while (owing.length > 0 && owed.length > 0) {
    owing.sort((a, b) => b[1] - a[1]);
    owed.sort((a, b) => b[1] - a[1]);
    const [debtor, debt] = owing[0]!;
    const [creditor, credit] = owed[0]!;
    const amount = Math.min(debt, credit);
    transfers.push({ from: debtor, to: creditor, amount });
    if (debt === amount) owing.shift();
    else owing[0] = [debtor, debt - amount];
    if (credit === amount) owed.shift();
    else owed[0] = [creditor, credit - amount];
  }
  return transfers;
}
