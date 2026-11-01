import { describe, expect, it } from 'vitest';
import { normalisedDesignator, parseFlightDesignator } from '../../src/domain/flight-designator';

const normalised = (text: string) => {
  const d = parseFlightDesignator(text);
  return d ? normalisedDesignator(d) : null;
};

describe('FlightDesignator', () => {
  it('parses the way airlines print it', () => {
    expect(normalised('BA286')).toBe('BA286');
    expect(normalised('ba 286')).toBe('BA286');
    expect(normalised(' BA-286 ')).toBe('BA286');
    expect(normalised('BA0286')).toBe('BA286');
  });

  it('handles carrier codes with digits and three-letter ICAO codes', () => {
    expect(normalised('3U8888')).toBe('3U8888');
    expect(normalised('U2 1234')).toBe('U21234');
    expect(normalised('BAW286')).toBe('BAW286');
  });

  it('keeps an operational suffix', () => {
    const parsed = parseFlightDesignator('LH1234A');
    expect(parsed?.carrier).toBe('LH');
    expect(parsed?.number).toBe(1234);
    expect(parsed?.suffix).toBe('A');
  });

  it('rejects what cannot be a flight number', () => {
    for (const text of ['', 'BA', '286', 'BA12345', 'BA0', 'HEATHROW']) {
      expect(parseFlightDesignator(text), text).toBeNull();
    }
  });
});
