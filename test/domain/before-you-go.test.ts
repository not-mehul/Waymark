import { describe, expect, it } from 'vitest';
import { citiesVisited } from '../../src/domain/after-trip';
import { documentWarnings, suggestedChecklist, warningText } from '../../src/domain/before-you-go';
import type { IdentityDoc } from '../../src/domain/model';
import { date, dossierOf, sample } from '../helpers';

const bundle = sample(date(2026, 5, 14));
const withDocs = (docs: IdentityDoc[]) => {
  const d = dossierOf(bundle);
  const [first, ...rest] = d.party.travelers;
  return { ...d, party: { ...d.party, travelers: [{ ...first!, identityDocs: docs }, ...rest] } };
};

describe('documentWarnings', () => {
  it('warns about a passport with under six months left after the trip', () => {
    const [w] = documentWarnings(
      withDocs([{ kind: 'PASSPORT', label: 'UK passport', expires: '2026-09-01' }])
    );
    expect(w!.problem).toBe('UNDER_SIX_MONTHS');
    expect(warningText(w!)).toContain('six months');
  });

  it('calls one that runs out before or during the trip what it is', () => {
    expect(
      documentWarnings(withDocs([{ kind: 'VISA', label: 'ESTA', expires: '2026-01-01' }]))[0]!.problem
    ).toBe('EXPIRED_BEFORE');
    const trip = dossierOf(bundle).trip;
    const during = new Date(trip.startEpochMillis + 86_400_000).toISOString().slice(0, 10);
    expect(documentWarnings(withDocs([{ kind: 'VISA', label: 'ESTA', expires: during }]))[0]!.problem).toBe(
      'EXPIRES_DURING'
    );
  });

  it('says nothing about a visa that lasts the trip, or a passport good for years', () => {
    expect(
      documentWarnings(
        withDocs([
          { kind: 'VISA', label: 'ESTA', expires: '2026-12-01' },
          { kind: 'PASSPORT', label: '', expires: '2031-01-01' }
        ])
      )
    ).toEqual([]);
  });
});

describe('suggestedChecklist', () => {
  it('asks for an adapter for the sockets abroad, and the money', () => {
    const lines = suggestedChecklist(dossierOf(bundle));
    expect(lines).toContain('Passport');
    expect(lines.some((l) => l.startsWith('Plug adapter (Type'))).toBe(true);
    expect(lines.some((l) => l.includes('GBP'))).toBe(true);
  });
});

describe('citiesVisited', () => {
  it('lists the cities stayed in, in the order they were reached, without connections', () => {
    const cities = citiesVisited(dossierOf(bundle)).map((c) => c.city);
    expect(cities).toEqual(['London', 'Paris']);
    expect(citiesVisited(dossierOf(bundle))[0]!.country).toBe('GB');
  });
});
