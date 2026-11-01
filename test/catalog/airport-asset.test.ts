// The generated asset itself, read straight off disk.
//
// A directory this size is only useful if it is *right*, and the failure modes
// are quiet ones: a transposed coordinate puts an airport in the sea, a bad
// time-zone name moves a flight to the wrong day and only shows up in the
// timeline weeks later. These are the checks that catch a bad regeneration
// before it ships.
import { describe, expect, it } from 'vitest';
import { AIRPORTS_ASSET, coreAirports, parseAirportDirectory } from '../../src/catalog/airports';
import { isKnownZone, zonedAt } from '../../src/domain/time';
import { bundledLines, instant } from '../helpers';

const directory = parseAirportDirectory(bundledLines(AIRPORTS_ASSET));
const byCode = new Map(directory.map((a) => [a.code, a]));

describe('AirportAsset', () => {
  it('the whole world is present', () => {
    expect(directory.length).toBeGreaterThan(8_000);
    expect(byCode.size).toBe(directory.length);
  });

  it('every time zone is one the platform actually knows', () => {
    const zones = [...new Set(directory.map((a) => a.timeZoneId))];
    expect(zones.length).toBeGreaterThan(300);
    for (const zone of zones) expect(isKnownZone(zone), zone).toBe(true);
  });

  it('every station has a code, a name, something to call it, and a position', () => {
    for (const a of directory) {
      expect(a.code, `bad code ${a.code}`).toMatch(/^[A-Z]{3}$/);
      expect(a.name.trim(), `${a.code} has no name`).not.toBe('');
      expect(a.city.trim(), `${a.code} has no city`).not.toBe('');
      expect(Math.abs(a.latitude), `${a.code} lat`).toBeLessThanOrEqual(90);
      expect(Math.abs(a.longitude), `${a.code} lon`).toBeLessThanOrEqual(180);
    }
  });

  // The check that catches a transposed latitude and longitude: a station's
  // zone should agree with its longitude to within a few hours.
  it("each station's zone agrees with its longitude", () => {
    const at = instant('2026-01-15T12:00:00Z');
    const offsets = new Map<string, number>();
    const offsetHours = (zone: string) => {
      let hours = offsets.get(zone);
      if (hours === undefined) {
        hours = zonedAt(at, zone).offsetNanoseconds / 3.6e12;
        offsets.set(zone, hours);
      }
      return hours;
    };
    const wrong = directory.filter((a) => {
      let delta = offsetHours(a.timeZoneId) - a.longitude / 15;
      while (delta > 12) delta -= 24;
      while (delta < -12) delta += 24;
      return Math.abs(delta) > 4;
    });
    // A handful of genuine outliers exist — western China on Beijing time,
    // Spain on central European time — so this is a ceiling, not zero.
    expect(
      wrong.length,
      `${wrong.length} stations disagree with their longitude: ${wrong
        .slice(0, 5)
        .map((a) => `${a.code} ${a.timeZoneId}`)
        .join(', ')}`
    ).toBeLessThan(directory.length / 50);
  });

  it('the stations a traveler is most likely to type are correct', () => {
    const expected: [string, string, number, number][] = [
      ['LHR', 'Europe/London', 51.47, -0.46],
      ['JFK', 'America/New_York', 40.64, -73.78],
      ['NRT', 'Asia/Tokyo', 35.76, 140.39],
      ['SYD', 'Australia/Sydney', -33.95, 151.18],
      ['GRU', 'America/Sao_Paulo', -23.44, -46.47],
      ['DXB', 'Asia/Dubai', 25.25, 55.36],
      ['JNB', 'Africa/Johannesburg', -26.14, 28.25],
      ['KTM', 'Asia/Kathmandu', 27.7, 85.36]
    ];
    for (const [code, zone, lat, lon] of expected) {
      const a = byCode.get(code);
      expect(a, `${code} missing`).toBeDefined();
      expect(a!.timeZoneId, code).toBe(zone);
      expect(Math.abs(a!.latitude - lat), `${code} latitude`).toBeLessThanOrEqual(0.1);
      expect(Math.abs(a!.longitude - lon), `${code} longitude`).toBeLessThanOrEqual(0.1);
    }
  });

  // Half-hour and three-quarter-hour zones are the ones a lazy table gets wrong.
  it('stations in offset zones keep their odd offsets', () => {
    expect(byCode.get('BOM')!.timeZoneId).toBe('Asia/Kolkata');
    expect(byCode.get('KTM')!.timeZoneId).toBe('Asia/Kathmandu');
    expect(byCode.get('ADL')!.timeZoneId).toBe('Australia/Adelaide');
    expect(byCode.get('IKA')!.timeZoneId).toBe('Asia/Tehran');
  });

  it('the core list and the directory agree on where the core stations are', () => {
    for (const curated of coreAirports()) {
      const row = byCode.get(curated.code);
      if (!row) continue;
      expect(row.timeZoneId, `${curated.code} zone`).toBe(curated.timeZoneId);
      expect(Math.abs(row.latitude - curated.latitude), `${curated.code} latitude`).toBeLessThanOrEqual(0.5);
      expect(Math.abs(row.longitude - curated.longitude), `${curated.code} longitude`).toBeLessThanOrEqual(
        0.5
      );
    }
  });
});
