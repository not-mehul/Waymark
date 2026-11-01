/**
 * A parsed flight number. Airlines are inconsistent about how they print
 * these — "BA 286", "ba286", "BAW286", "LH1234A" — so the app parses loosely
 * and normalises hard.
 */
export interface FlightDesignator {
  carrier: string;
  number: number;
  suffix: string | null;
}

// Two-letter IATA codes ("BA"), mixed codes with a leading digit ("3U") or a
// trailing digit ("U2"), and three-letter ICAO codes ("BAW").
const PATTERN = /^([A-Z0-9][A-Z]|[A-Z][A-Z0-9]|[A-Z]{3})(\d{1,4})([A-Z])?$/;

/** Canonical form, always uppercase, never zero-padded. */
export function normalisedDesignator(d: FlightDesignator): string {
  return `${d.carrier}${d.number}${d.suffix ?? ''}`;
}

/** Null when the text cannot be a flight number at all. */
export function parseFlightDesignator(input: string): FlightDesignator | null {
  const cleaned = input
    .trim()
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, '');
  if (cleaned.length < 3) return null;
  const match = PATTERN.exec(cleaned);
  if (!match) return null;
  const number = Number.parseInt(match[2]!, 10);
  if (!(number > 0)) return null;
  return { carrier: match[1]!, number, suffix: match[3] || null };
}
