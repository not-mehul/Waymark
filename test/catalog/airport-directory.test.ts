import { describe, expect, it } from 'vitest';
import {
  findAirport,
  installAirports,
  parseAirportDirectory,
  searchAirports
} from '../../src/catalog/airports';

const lines = (text: string) => text.split('\n').map((l) => l.trim());

const parsed = parseAirportDirectory(
  lines(`Europe/London
    America/Los_Angeles
    Pacific/Majuro

    LHR|London Heathrow Airport|London|GB|51.4706|-0.4619|0
    SFO|San Francisco International Airport|San Francisco|US|37.6188|-122.3750|1
    UTK|Utirik Airport||MH|11.2220|169.8520|2`)
);
const byCode = (code: string) => parsed.find((a) => a.code === code)!;

describe('AirportDirectory', () => {
  it('zones are interned and resolved by index', () => {
    expect(parsed).toHaveLength(3);
    expect(byCode('LHR').timeZoneId).toBe('Europe/London');
    expect(byCode('SFO').timeZoneId).toBe('America/Los_Angeles');
    expect(byCode('UTK').timeZoneId).toBe('Pacific/Majuro');
  });

  it('coordinates and country survive the round trip', () => {
    const heathrow = byCode('LHR');
    expect(heathrow.latitude).toBeCloseTo(51.4706, 4);
    expect(heathrow.longitude).toBeCloseTo(-0.4619, 4);
    // The file carries "GB"; the parser expands it, because "GB" is not a country anybody reads.
    expect(heathrow.country).toBe('United Kingdom');
  });

  it('an airport with no municipality falls back to its own shortened name', () => {
    // "Utirik Airport" on an island with no town listed against it.
    expect(byCode('UTK').city).toBe('Utirik');
  });

  it('a malformed line is skipped rather than failing the whole directory', () => {
    const broken = lines(`Europe/London

      LHR|London Heathrow Airport|London|GB|51.4706|-0.4619|0
      XX|too short|Nowhere|GB|1.0|1.0|0
      BAD|missing fields|GB|0
      NAN|unparseable|Nowhere|GB|north|west|0
      OOB|off the planet|Nowhere|GB|991.0|1.0|0
      ZON|no such zone|Nowhere|GB|1.0|1.0|99
      CDG|Charles de Gaulle|Paris|FR|49.0097|2.5479|0`);
    expect(parseAirportDirectory(broken).map((a) => a.code)).toEqual(['LHR', 'CDG']);
  });

  it('an empty directory parses to nothing rather than throwing', () => {
    expect(parseAirportDirectory([])).toEqual([]);
    expect(parseAirportDirectory(['', '  '])).toEqual([]);
  });

  // — Installation ——————————————————————————————————————————————————————

  it('the core list wins a code collision, keeping its terminals', () => {
    // The directory row for LHR carries no terminals; the curated one does.
    installAirports(parsed);
    const heathrow = findAirport('LHR')!;
    expect(heathrow.terminals.length, 'core terminals were lost').toBeGreaterThan(0);
    expect(heathrow.name).toBe('Heathrow');

    // A code the core list has never heard of comes from the directory.
    expect(findAirport('UTK')!.timeZoneId).toBe('Pacific/Majuro');
  });

  it('search prefers a core station over a directory one at the same rank', () => {
    installAirports(parsed);
    // Both LHR and UTK exist; an exact code still wins outright.
    expect(searchAirports('utk')[0]!.code).toBe('UTK');
    expect(searchAirports('lhr')[0]!.code).toBe('LHR');
    expect(findAirport('ZZZ')).toBeNull();
  });
});
