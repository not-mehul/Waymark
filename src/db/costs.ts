// What a trip costs: its expenses, the currency they are settled in, and the
// rates they are converted at. A rate the traveler typed is theirs; a live
// rate fetched later never replaces it.

import type { Expense, TripCosts } from '../domain/model';
import type { LocalDate } from '../domain/time';
import { expenseOf, expenseRow, present, rateOf, rateRow, type WaymarkDb } from './database';

export class CostRepository {
  constructor(private readonly db: WaymarkDb) {}

  async forTrip(tripId: string): Promise<TripCosts> {
    const { db } = this;
    return db.transaction('r', db.costSettings, db.expenses, db.rates, async () => {
      const expenses = (await db.expenses.where('tripId').equals(tripId).toArray())
        .map(expenseOf)
        .filter(present)
        .sort(
          (a, b) =>
            a.spentOn.toString().localeCompare(b.spentOn.toString()) || a.addedAtMillis - b.addedAtMillis
        );
      const rates = (await db.rates.where('tripId').equals(tripId).sortBy('currency'))
        .map(rateOf)
        .filter(present);
      return { settleCurrency: (await db.costSettings.get(tripId))?.settleCurrency ?? null, expenses, rates };
    });
  }

  async save(expense: Expense): Promise<void> {
    await this.db.expenses.put(expenseRow(expense));
  }

  async delete(expenseId: string): Promise<void> {
    await this.db.expenses.delete(expenseId);
  }

  /**
   * Settle in [currency] from now on. Every rate the trip had was a price in
   * the old currency, so they go — live ones are fetched again, and the
   * traveler is asked again for any they typed.
   */
  async setSettleCurrency(tripId: string, currency: string): Promise<void> {
    const { db } = this;
    await db.transaction('rw', db.costSettings, db.rates, async () => {
      if ((await db.costSettings.get(tripId))?.settleCurrency === currency) return;
      await db.costSettings.put({ tripId, settleCurrency: currency });
      await db.rates.where('tripId').equals(tripId).delete();
    });
  }

  async setManualRate(tripId: string, currency: string, perUnit: number, today: LocalDate): Promise<void> {
    await this.db.rates.put(rateRow(tripId, { currency, perUnit, asOf: today, manual: true }));
  }

  /** Back to the live rate, or to none until one is fetched. */
  async clearRate(tripId: string, currency: string): Promise<void> {
    await this.db.rates.delete([tripId, currency]);
  }

  /** Rates fetched for [tripId]. Rates typed by hand are left as they are. */
  async applyLiveRates(tripId: string, perUnit: ReadonlyMap<string, number>, asOf: LocalDate): Promise<void> {
    const { db } = this;
    await db.transaction('rw', db.rates, async () => {
      const manual = new Set(
        (await db.rates.where('tripId').equals(tripId).toArray())
          .filter((r) => r.manual)
          .map((r) => r.currency)
      );
      await db.rates.bulkPut(
        [...perUnit]
          .filter(([currency]) => !manual.has(currency))
          .map(([currency, rate]) => rateRow(tripId, { currency, perUnit: rate, asOf, manual: false }))
      );
    });
  }
}
