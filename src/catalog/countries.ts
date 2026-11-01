// Every country, and the four things a traveler wants in the twenty minutes
// between landing and finding the train: the money, the plug, the number to
// call, and which side of the road to look down before crossing.

import type { Place } from '../domain/model';
import { COUNTRY_ROWS } from './countries-data';

export interface Country {
  /** ISO 3166-1 alpha-2, which is what the station directory carries. */
  code: string;
  name: string;
  currencyCode: string;
  currencyName: string;
  /** Socket letters, in the conventional A–O lettering. */
  plugTypes: string[];
  volts: number;
  hertz: number;
  emergencyNumber: string;
  drivesOnLeft: boolean;
}

export const countryCurrency = (c: Country) => `${c.currencyName} (${c.currencyCode})`;
export const countryPower = (c: Country) => `Type ${c.plugTypes.join(' / ')} · ${c.volts}V / ${c.hertz}Hz`;
export const countryDriving = (c: Country) => (c.drivesOnLeft ? 'Drives on the left' : 'Drives on the right');

const entries: Country[] = COUNTRY_ROWS.map(
  ([code, name, currencyCode, currencyName, plugs, volts, hertz, emergencyNumber, drivesOnLeft]) => ({
    code,
    name,
    currencyCode,
    currencyName,
    plugTypes: plugs.split(','),
    volts,
    hertz,
    emergencyNumber,
    drivesOnLeft
  })
);

const FOLD: Record<string, string> = {};
for (const [plain, accented] of [
  ['a', 'áàâäãå'],
  ['e', 'éèêë'],
  ['i', 'íìîï'],
  ['o', 'óòôöõ'],
  ['u', 'úùûü'],
  ['c', 'ç'],
  ['n', 'ñ'],
  ['s', 'ş']
] as const) {
  for (const c of accented) FOLD[c] = plain;
}
const LETTER_OR_DIGIT = /^[\p{L}\p{Nd}]$/u;

/** Case, a handful of accents and "the" vary between the station lists. */
function normalise(value: string): string {
  let text = value.trim().toLowerCase();
  if (text.startsWith('the ')) text = text.slice(4);
  let out = '';
  for (let i = 0; i < text.length; i++) {
    const c = FOLD[text.charAt(i)] ?? text.charAt(i);
    if (LETTER_OR_DIGIT.test(c)) out += c;
  }
  return out;
}

const byCode = new Map(entries.map((c) => [c.code, c]));
const byName = new Map(entries.map((c) => [normalise(c.name), c]));

export function countryForCode(code: string | null | undefined): Country | null {
  const key = code?.trim().toUpperCase();
  return key && key.length === 2 ? (byCode.get(key) ?? null) : null;
}

export function countryForName(name: string | null | undefined): Country | null {
  return name == null ? null : (byName.get(normalise(name)) ?? null);
}

/** A place carries whichever form its source used; try both. */
export function countryOf(p: Place | null | undefined): Country | null {
  const value = p?.country.trim() ?? '';
  if (value === '') return null;
  return countryForCode(value) ?? countryForName(value);
}

export function allCountries(): readonly Country[] {
  return entries;
}

/** The currency a region uses, for `localCurrency`. */
export function currencyOfRegion(region: string): string | null {
  return countryForCode(region)?.currencyCode ?? null;
}
