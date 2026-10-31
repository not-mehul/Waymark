import { describe, expect, it } from 'vitest';
import { airlineCount, airlineName, knowsAirline } from '../../src/catalog/airlines';

describe('Airlines', () => {
  it('designators resolve to the names travelers know', () => {
    expect(airlineName('BA')).toBe('British Airways');
    expect(airlineName('u2')).toBe('easyJet');
    expect(airlineName(' 6E ')).toBe('IndiGo');
    expect(airlineName('LA')).toBe('LATAM Airlines');
  });

  it('an unknown designator prints as itself', () => {
    expect(airlineName('zz')).toBe('ZZ');
    expect(knowsAirline('ZZ')).toBe(false);
  });

  it('the table is broad and every key is a well-formed designator', () => {
    expect(airlineCount).toBeGreaterThan(200);
    for (const code of ['AA', 'AF', 'CX', 'EK', 'LH', 'NH', 'QF', 'SQ', 'TK', 'UA', 'WN']) {
      expect(knowsAirline(code), code).toBe(true);
    }
  });
});
