import 'fake-indexeddb/auto';
import { describe, expect, it } from 'vitest';
import { CostRepository } from '../../src/db/costs';
import { openDatabase } from '../../src/db/database';
import { localDate } from '../../src/domain/time';
import { parseLiveRates, perUnitIn, RatesError, refreshLiveRates } from '../../src/online/rates';

const REPLY = JSON.stringify({
  amount: 1,
  base: 'EUR',
  date: '2026-10-30',
  rates: { GBP: 0.85, JPY: 160, USD: 1.1 }
});
const today = localDate(2026, 11, 1);

describe('parseLiveRates', () => {
  it('reads the euro rates and adds the euro itself', () => {
    const live = parseLiveRates(REPLY);
    expect(live.date).toEqual(localDate(2026, 10, 30));
    expect(live.perEuro.get('EUR')).toBe(1);
    expect(live.perEuro.get('JPY')).toBe(160);
  });

  it('refuses a reply that is not rates against the euro', () => {
    expect(() => parseLiveRates('<html>')).toThrow(RatesError);
    expect(() => parseLiveRates(JSON.stringify({ base: 'USD', date: '2026-10-30', rates: {} }))).toThrow(
      'Rates against USD, not the euro'
    );
    expect(() => parseLiveRates(JSON.stringify({ base: 'EUR', rates: {} }))).toThrow('No date in the reply');
  });

  it('works out what one unit of each is worth in the settling currency', () => {
    const units = perUnitIn(parseLiveRates(REPLY), 'GBP', ['JPY', 'GBP', 'EUR', 'THB']);
    expect(units.get('EUR')).toBeCloseTo(0.85);
    expect(units.get('JPY')).toBeCloseTo(0.85 / 160);
    expect(units.has('GBP')).toBe(false);
    expect(units.has('THB')).toBe(false);
  });
});

describe('refreshLiveRates', () => {
  const costs = new CostRepository(openDatabase('rates'));

  it('asks nobody when online extras are off', async () => {
    let asked = false;
    const result = await refreshLiveRates(costs, 't1', 'GBP', ['EUR'], false, today, () => {
      asked = true;
      return Promise.resolve(REPLY);
    });
    expect(result).toEqual({ outcome: 'notAllowed' });
    expect(asked).toBe(false);
  });

  it('stores the live rates, keeps a typed one, and names what the bank does not publish', async () => {
    await costs.setManualRate('t2', 'JPY', 0.006, today);
    const result = await refreshLiveRates(costs, 't2', 'GBP', ['EUR', 'JPY', 'THB'], true, today, () =>
      Promise.resolve(REPLY)
    );
    expect(result).toEqual({ outcome: 'updated', asOf: localDate(2026, 10, 30), missing: ['THB'] });
    const rates = (await costs.forTrip('t2')).rates;
    expect(rates.find((r) => r.currency === 'EUR')).toMatchObject({ manual: false, perUnit: 0.85 });
    expect(rates.find((r) => r.currency === 'JPY')).toMatchObject({ manual: true, perUnit: 0.006 });
  });

  it('keeps the last rates when the service cannot be reached', async () => {
    const result = await refreshLiveRates(costs, 't3', 'GBP', ['EUR'], true, today, () =>
      Promise.reject(new TypeError('Failed to fetch'))
    );
    expect(result).toEqual({ outcome: 'failed', reason: 'No connection' });
  });
});
