import { describe, expect, it } from 'vitest';
import { isSettled, net, summarizeCosts } from '../../src/domain/costs';
import {
  expense,
  formatMoney,
  parseMoney,
  type ExchangeRate,
  type ExpenseCategory
} from '../../src/domain/model';
import { date } from '../helpers';

const day = date(2026, 5, 14);
const party = ['mara', 'julian', 'ines'];
let added = 0;

function spent(
  amount: number,
  paidBy: string,
  {
    currency = 'EUR',
    among = [] as string[],
    category = 'OTHER' as ExpenseCategory
  }: { currency?: string; among?: string[]; category?: ExpenseCategory } = {}
) {
  const id = `e${added++}`;
  return expense({
    id,
    tripId: 't',
    description: 'something',
    amount: { minor: amount, currency },
    paidBy,
    splitAmong: among,
    spentOn: day,
    category,
    addedAtMillis: added
  });
}

describe('CostSplitter', () => {
  it('an even split leaves the payer owed by everyone else', () => {
    const summary = summarizeCosts([spent(9_000, 'mara')], party, 'EUR', []);
    expect(summary.total).toBe(9_000);
    expect(summary.balances.map(net)).toEqual([6_000, -3_000, -3_000]);
    expect(summary.transfers).toHaveLength(2);
    expect(summary.transfers).toEqual(
      expect.arrayContaining([
        { from: 'julian', to: 'mara', amount: 3_000 },
        { from: 'ines', to: 'mara', amount: 3_000 }
      ])
    );
  });

  it('the cent that cannot be shared goes to the first in the party, and the shares add up', () => {
    const summary = summarizeCosts([spent(1_000, 'julian')], party, 'EUR', []);
    expect(summary.balances.map((b) => b.share)).toEqual([334, 333, 333]);
    expect(summary.balances.reduce((sum, b) => sum + b.share, 0)).toBe(1_000);
    expect(summary.balances.reduce((sum, b) => sum + net(b), 0)).toBe(0);
  });

  it('a split among some of the party leaves the others out of it', () => {
    const summary = summarizeCosts([spent(4_000, 'mara', { among: ['mara', 'ines'] })], party, 'EUR', []);
    expect(summary.balances.map(net)).toEqual([2_000, 0, -2_000]);
    expect(summary.transfers).toEqual([{ from: 'ines', to: 'mara', amount: 2_000 }]);
  });

  it('everybody paying their share settles with no transfers', () => {
    const summary = summarizeCosts(
      [spent(3_000, 'mara'), spent(3_000, 'julian'), spent(3_000, 'ines')],
      party,
      'EUR',
      []
    );
    expect(isSettled(summary)).toBe(true);
  });

  it('settling never takes more transfers than people less one', () => {
    const expenses = [
      spent(12_000, 'mara'),
      spent(2_500, 'julian'),
      spent(700, 'ines', { among: ['ines', 'julian'] }),
      spent(5_100, 'julian', { among: ['mara', 'julian'] })
    ];
    const summary = summarizeCosts(expenses, party, 'EUR', []);
    expect(summary.transfers.length).toBeLessThanOrEqual(party.length - 1);
    // Applying the transfers brings everyone to zero.
    const after = new Map(summary.balances.map((b) => [b.travelerId, net(b)]));
    for (const t of summary.transfers) {
      after.set(t.from, after.get(t.from)! + t.amount);
      after.set(t.to, after.get(t.to)! - t.amount);
    }
    expect([...after.values()].every((v) => v === 0)).toBe(true);
  });

  it("a foreign expense is converted once, at the trip's rate", () => {
    const rates: ExchangeRate[] = [{ currency: 'JPY', perUnit: 0.0062, asOf: day, manual: true }];
    const summary = summarizeCosts([spent(12_000, 'mara', { currency: 'JPY' })], party, 'EUR', rates);
    // ¥12,000 at 0.0062 is €74.40.
    expect(summary.total).toBe(7_440);
  });

  it('an expense with no rate is listed, not counted', () => {
    const foreign = spent(5_000, 'mara', { currency: 'CHF' });
    const summary = summarizeCosts([foreign, spent(3_000, 'ines')], party, 'EUR', []);
    expect(summary.unconverted).toEqual([foreign]);
    expect(summary.total).toBe(3_000);
  });

  it('somebody who left the party is still owed what they paid', () => {
    const summary = summarizeCosts([spent(3_000, 'olu', { among: ['olu', 'mara'] })], party, 'EUR', []);
    const olu = summary.balances.filter((b) => b.travelerId === 'olu');
    expect(olu).toHaveLength(1);
    expect(net(olu[0]!)).toBe(1_500);
    expect(summary.transfers).toEqual([{ from: 'mara', to: 'olu', amount: 1_500 }]);
  });

  it('spending is totalled by category', () => {
    const summary = summarizeCosts(
      [
        spent(1_000, 'mara', { category: 'FOOD' }),
        spent(500, 'ines', { category: 'FOOD' }),
        spent(9_000, 'julian', { category: 'STAY' })
      ],
      party,
      'EUR',
      []
    );
    expect(summary.byCategory).toEqual(
      new Map<ExpenseCategory, number>([
        ['STAY', 9_000],
        ['FOOD', 1_500]
      ])
    );
  });

  it('typed amounts are read the way people write them', () => {
    expect(parseMoney('12.5', 'EUR')?.minor).toBe(1_250);
    expect(parseMoney('12,50', 'EUR')?.minor).toBe(1_250);
    expect(parseMoney('1,200', 'EUR')?.minor).toBe(120_000);
    expect(parseMoney('1.200,50', 'EUR')?.minor).toBe(120_050);
    expect(parseMoney('1,200.50', 'USD')?.minor).toBe(120_050);
    expect(parseMoney('1 200', 'EUR')?.minor).toBe(120_000);
    expect(parseMoney('1200', 'JPY')?.minor).toBe(1_200);
    expect(parseMoney('1.250', 'KWD')?.minor).toBe(1_250);
    // Three digits after a lone separator group thousands, as much of Europe writes them.
    expect(parseMoney('12.345', 'EUR')?.minor).toBe(1_234_500);
    expect(parseMoney('12.3456', 'EUR')).toBeNull();
    expect(parseMoney('0', 'EUR')).toBeNull();
    expect(parseMoney('-5', 'EUR')).toBeNull();
    expect(parseMoney('lunch', 'EUR')).toBeNull();
    expect(parseMoney('12.5', 'JPY')).toBeNull();
  });

  it("an amount is written in its currency's own decimals", () => {
    expect(formatMoney({ minor: 1_250, currency: 'EUR' }, 'en-GB')).toBe('€12.50');
    expect(formatMoney({ minor: 1_200, currency: 'JPY' }, 'ja-JP').replace('￥', '¥')).toBe('¥1,200');
  });
});
