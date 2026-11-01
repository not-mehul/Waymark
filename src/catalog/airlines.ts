// IATA airline designators and the names travelers know the airlines by.
// Hand-compiled for the carriers people actually book; a designator that is
// not here prints as itself, which is always correct if not always friendly.

import { AIRLINE_ROWS } from './airlines-data';

const names = new Map<string, string>(AIRLINE_ROWS);

/** "BA" → "British Airways"; an unknown designator comes back as itself. */
export function airlineName(code: string): string {
  const key = code.trim().toUpperCase();
  return names.get(key) ?? key;
}

export function knowsAirline(code: string): boolean {
  return names.has(code.trim().toUpperCase());
}

export const airlineCount = names.size;
