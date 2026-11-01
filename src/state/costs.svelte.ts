// One trip's costs, for the Costs tab: the expenses, the balances and the
// payments that settle them, and the rates behind any conversion. The
// counterpart of CostsModel.kt; it lives as long as the trip is open.

import { liveQuery, type Subscription } from 'dexie';
import { summarizeCosts } from '../domain/costs';
import {
  displayName,
  EMPTY_COSTS,
  type Expense,
  type Traveler,
  type TripCosts,
  type TripDossier
} from '../domain/model';
import { systemZone, todayIn } from '../domain/time';
import { newId } from '../db/trips';
import { refreshLiveRates, type FetchText, fetchText } from '../online/rates';
import { costs as repository, settings } from './app';
import {
  expenseFrom,
  foreignCurrencies,
  ratesAreStale,
  ratesNote,
  settleCurrencyOf,
  type ExpenseDraft
} from './costs-logic';

export class CostsModel {
  private readonly dossier: () => TripDossier | null | undefined;
  private readonly get: FetchText;
  data = $state.raw<TripCosts>(EMPTY_COSTS);
  loaded = $state(false);
  note = $state<string | null>(null);
  updating = $state(false);

  readonly settle = $derived(settleCurrencyOf(this.data));
  /** Whether the settling currency was chosen, rather than taken from the device. */
  readonly settleChosen = $derived(this.data.settleCurrency !== null);
  /** Newest day first. */
  readonly expenses = $derived(
    [...this.data.expenses].sort(
      (a, b) => b.spentOn.toString().localeCompare(a.spentOn.toString()) || b.addedAtMillis - a.addedAtMillis
    )
  );
  readonly summary = $derived(
    summarizeCosts(
      this.data.expenses,
      this.party.map((t) => t.id),
      this.settle,
      this.data.rates
    )
  );
  readonly foreign = $derived(foreignCurrencies(this.data, this.settle));

  private readonly subscription: Subscription;

  get party(): Traveler[] {
    return this.dossier()?.party.travelers ?? [];
  }

  constructor(
    readonly tripId: string,
    dossier: () => TripDossier | null | undefined,
    get: FetchText = fetchText
  ) {
    this.dossier = dossier;
    this.get = get;
    this.subscription = liveQuery(() => repository.forTrip(tripId)).subscribe((c) => {
      this.data = c;
      this.loaded = true;
    });
  }

  destroy(): void {
    this.subscription.unsubscribe();
  }

  nameOf(travelerId: string): string {
    const t = this.party.find((p) => p.id === travelerId);
    return t ? displayName(t) : 'Someone no longer on the trip';
  }

  async save(draft: ExpenseDraft, existing: Expense | null): Promise<void> {
    const expense = expenseFrom(
      draft,
      newId('exp'),
      this.tripId,
      this.party.map((t) => t.id),
      existing,
      Date.now()
    );
    // The first expense fixes the settling currency, so a device later set
    // to another region does not quietly change what is owed.
    if (!this.settleChosen) await repository.setSettleCurrency(this.tripId, this.settle);
    await repository.save(expense);
    if (expense.amount.currency !== this.settle) await this.refreshRates();
  }

  delete(expenseId: string): Promise<void> {
    return repository.delete(expenseId);
  }

  async settleIn(currency: string): Promise<void> {
    await repository.setSettleCurrency(this.tripId, currency);
    this.note = null;
    await this.refreshRates();
  }

  setRate(currency: string, perUnit: number): Promise<void> {
    return repository.setManualRate(this.tripId, currency, perUnit, todayIn(systemZone()));
  }

  async clearRate(currency: string): Promise<void> {
    await repository.clearRate(this.tripId, currency);
    await this.refreshRates();
  }

  async setOnline(enabled: boolean): Promise<void> {
    settings.setOnlineExtras(enabled);
    if (enabled) await this.refreshRates(true);
    else this.note = null;
  }

  /**
   * Fetch today's rates when there is a currency without one, or with a live
   * one more than a few days old; a weekend's rate is Friday's.
   */
  async refreshRates(force = false): Promise<void> {
    if (this.updating) return;
    const current = await repository.forTrip(this.tripId);
    const settle = settleCurrencyOf(current);
    const today = todayIn(systemZone());
    const used = foreignCurrencies(current, settle);
    if (used.length === 0 || (!force && !ratesAreStale(current, settle, today))) return;
    if (!settings.onlineExtras) return;
    this.updating = true;
    try {
      this.note = ratesNote(
        await refreshLiveRates(repository, this.tripId, settle, used, settings.onlineExtras, today, this.get)
      );
    } finally {
      this.updating = false;
    }
  }
}
