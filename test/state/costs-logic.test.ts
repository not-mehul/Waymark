import { describe, expect, it } from 'vitest';
import { buildSampleItinerary } from '../../src/catalog/sample-itinerary';
import { expense, type TripDossier } from '../../src/domain/model';
import { localDate } from '../../src/domain/time';
import {
  currencyOn,
  expenseFrom,
  isCurrency,
  parseRate,
  rateText,
  ratesAreStale,
  ratesNote
} from '../../src/state/costs-logic';

const bundle = buildSampleItinerary(localDate(2026, 5, 14));
const dossier: TripDossier = {
  trip: bundle.trip,
  party: { tripId: bundle.trip.id, travelers: bundle.travelers },
  segments: bundle.segments
};

describe('expenseFrom', () => {
  const draft = {
    description: ' Dinner ',
    amount: { minor: 4200, currency: 'GBP' },
    paidBy: 'a',
    among: ['b', 'a'],
    spentOn: localDate(2026, 5, 15),
    category: 'FOOD' as const
  };

  it('stores everyone ticked as everyone, so a later traveler shares it too', () => {
    const saved = expenseFrom(draft, 'exp1', 't', ['a', 'b'], null, 99);
    expect(saved).toMatchObject({ id: 'exp1', description: 'Dinner', splitAmong: [], addedAtMillis: 99 });
    expect(expenseFrom(draft, 'exp1', 't', ['a', 'b', 'c'], null, 99).splitAmong).toEqual(['b', 'a']);
  });

  it('keeps an edited expense’s id and when it was added', () => {
    const existing = expense({ ...draft, id: 'old', tripId: 't', addedAtMillis: 5 });
    expect(expenseFrom(draft, 'new', 't', ['a', 'b'], existing, 99)).toMatchObject({
      id: 'old',
      addedAtMillis: 5
    });
  });
});

describe('currencyOn', () => {
  it('is the currency of wherever the trip is that day', () => {
    expect(currencyOn(dossier, localDate(2026, 5, 15), 'USD')).toBe('GBP');
    expect(currencyOn(dossier, localDate(2026, 5, 19), 'USD')).toBe('EUR');
    expect(currencyOn(null, localDate(2026, 5, 19), 'USD')).toBe('USD');
  });
});

describe('rates', () => {
  const today = localDate(2026, 11, 1);
  const costsWith = (rates: { currency: string; asOf: ReturnType<typeof localDate>; manual: boolean }[]) => ({
    settleCurrency: 'GBP',
    expenses: [
      expense({
        id: 'e',
        tripId: 't',
        description: 'x',
        amount: { minor: 100, currency: 'EUR' },
        paidBy: 'a',
        spentOn: today
      })
    ],
    rates: rates.map((r) => ({ ...r, perUnit: 0.85 }))
  });

  it('wants fetching with no rate, or a live one older than three days, but never over a typed one', () => {
    expect(ratesAreStale(costsWith([]), 'GBP', today)).toBe(true);
    expect(
      ratesAreStale(
        costsWith([{ currency: 'EUR', asOf: localDate(2026, 10, 30), manual: false }]),
        'GBP',
        today
      )
    ).toBe(false);
    expect(
      ratesAreStale(
        costsWith([{ currency: 'EUR', asOf: localDate(2026, 10, 1), manual: false }]),
        'GBP',
        today
      )
    ).toBe(true);
    expect(
      ratesAreStale(costsWith([{ currency: 'EUR', asOf: localDate(2026, 1, 1), manual: true }]), 'GBP', today)
    ).toBe(false);
  });

  it('reads and writes a rate as people do', () => {
    expect(rateText(0.856432)).toBe('0.8564');
    expect(rateText(160.123)).toBe('160.1');
    expect(rateText(0.0053125)).toBe('0.005313');
    expect(parseRate('0,85')).toBe(0.85);
    expect(parseRate('-1')).toBeNull();
    expect(parseRate('')).toBeNull();
    expect(isCurrency('CHF')).toBe(true);
    expect(isCurrency('XYZ')).toBe(false);
  });

  it('says where the rates came from, or why they did not', () => {
    expect(ratesNote({ outcome: 'updated', asOf: localDate(2026, 10, 30), missing: ['THB'] })).toMatch(
      /European Central Bank published them\. It publishes none for THB: type one in\.$/
    );
    expect(ratesNote({ outcome: 'failed', reason: 'HTTP 500' })).toBe(
      'Could not update the rates (HTTP 500). The last ones are kept.'
    );
    expect(ratesNote({ outcome: 'notAllowed' })).toBeNull();
  });
});
