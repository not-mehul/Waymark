// The country table is bundled reference data, so what can be tested is its
// shape rather than its truth: every row well formed, no duplicates, and every
// country a bundled station can be in has a row.
import { describe, expect, it } from 'vitest';
import { AIRPORTS_ASSET, parseAirportDirectory } from '../../src/catalog/airports';
import {
  allCountries,
  countryCurrency,
  countryDriving,
  countryForCode,
  countryForName,
  countryPower
} from '../../src/catalog/countries';
import { bundledLines } from '../helpers';

const all = allCountries();

describe('Countries', () => {
  it('every row is well formed', () => {
    expect(all.length).toBeGreaterThan(200);
    for (const c of all) {
      expect(c.code, `${c.code} is not an alpha-2 code`).toMatch(/^[A-Z]{2}$/);
      expect(c.name.trim()).not.toBe('');
      expect(c.currencyCode, `${c.code} currency`).toHaveLength(3);
      expect(c.currencyName.trim()).not.toBe('');
      expect(c.plugTypes.length, `${c.code} has no plug type`).toBeGreaterThan(0);
      expect(
        c.plugTypes.every((p) => /^[A-O]$/.test(p)),
        `${c.code} plug letters`
      ).toBe(true);
      expect(c.volts, `${c.code} volts`).toBeGreaterThanOrEqual(100);
      expect(c.volts, `${c.code} volts`).toBeLessThanOrEqual(250);
      expect([50, 60], `${c.code} hertz`).toContain(c.hertz);
      expect(c.emergencyNumber, `${c.code} emergency`).toMatch(/^\d+$/);
    }
  });

  it('codes and names are unique', () => {
    expect(new Set(all.map((c) => c.code)).size).toBe(all.length);
    expect(new Set(all.map((c) => c.name)).size).toBe(all.length);
  });

  // If a station can be entered, its country has notes.
  it('every country in the bundled directory is covered', () => {
    const uncovered = [...new Set(parseAirportDirectory(bundledLines(AIRPORTS_ASSET)).map((a) => a.country))]
      .filter((c) => countryForName(c) === null && countryForCode(c) === null)
      // The directory has one strip in Antarctica, which has no currency, no
      // emergency number and nobody to call one.
      .filter((c) => c.toUpperCase() !== 'AQ');
    expect(uncovered, `no notes for ${uncovered.join(', ')}`).toEqual([]);
  });

  it('lookup works by code and by name, and says no to nonsense', () => {
    const japan = countryForCode('JP')!;
    expect(japan.name).toBe('Japan');
    expect(japan.currencyCode).toBe('JPY');
    expect(japan.drivesOnLeft).toBe(true);
    expect(countryForName('Japan')).toEqual(japan);

    // The core station list writes names, the directory writes codes, and
    // neither is consistent about case or the definite article.
    expect(countryForName('united kingdom')).not.toBeNull();
    expect(countryForName('The United Kingdom')).not.toBeNull();
    expect(countryForName('Türkiye')).not.toBeNull();
    expect(countryForName('Turkiye')).not.toBeNull();

    expect(countryForCode('ZZ')).toBeNull();
    expect(countryForName('Atlantis')).toBeNull();
    expect(countryForName('')).toBeNull();
  });

  it('the rendered strings read as sentences rather than fields', () => {
    const uk = countryForCode('GB')!;
    expect(countryCurrency(uk)).toBe('Pound sterling (GBP)');
    expect(countryPower(uk)).toBe('Type G · 230V / 50Hz');
    expect(countryDriving(uk)).toBe('Drives on the left');

    const us = countryForCode('US')!;
    expect(countryPower(us)).toBe('Type A / B · 120V / 60Hz');
    expect(countryDriving(us)).toBe('Drives on the right');
  });
});
