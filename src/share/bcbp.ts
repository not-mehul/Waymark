// The text inside a boarding pass's barcode (IATA Resolution 792, "BCBP"):
// the passenger, then per flight the booking reference, the two airports,
// the airline, the flight number, the day of the year, the cabin and the
// seat. Everything Add flight asks for except the clock times.

import { daysBetween, Temporal, type LocalDate } from '../domain/time';

export interface BcbpLeg {
  designator: string;
  date: LocalDate;
  origin: string;
  destination: string;
  seat: string | null;
  cabin: string | null;
  reference: string | null;
  passenger: string;
}

const HEADER = 23;
const LEG = 37;
const LETTER = /^\p{L}$/u;
const DIGIT = /^[0-9]$/;

/** Every flight on the pass, or empty when the text is not a boarding pass. */
export function parseBoardingPass(text: string, today: LocalDate = Temporal.Now.plainDateISO()): BcbpLeg[] {
  const data = text.replace(/[\n\r]+$/, '');
  if (data.length < HEADER + LEG || data[0] !== 'M') return [];
  const legs = DIGIT.test(data[1]!) ? Number(data[1]) : 0;
  if (legs < 1 || legs > 9) return [];
  const passenger = passengerName(data.substring(2, 22));

  const found: BcbpLeg[] = [];
  let at = HEADER;
  for (let i = 0; i < legs; i++) {
    if (data.length < at + LEG) return found;
    const record = data.substring(at, at + LEG);
    const leg = parseLeg(record, passenger, today);
    if (!leg) return found;
    found.push(leg);
    const size = record.substring(35, 37).trim();
    const variable = /^[0-9a-fA-F]+$/.test(size) ? Number.parseInt(size, 16) : 0;
    at += LEG + variable;
  }
  return found;
}

function parseLeg(record: string, passenger: string, today: LocalDate): BcbpLeg | null {
  const reference = record.substring(0, 7).trim() || null;
  const origin = record.substring(7, 10).trim();
  const destination = record.substring(10, 13).trim();
  const carrier = record.substring(13, 16).trim();
  const flightField = record.substring(16, 21).trim();
  const dayText = record.substring(21, 24).trim();
  const dayOfYear = /^[+-]?\d+$/.test(dayText) ? Number(dayText) : null;
  const compartment = record[24]!;
  const seat = record.substring(25, 29).trim().replace(/^0+/, '') || null;

  if (origin.length !== 3 || destination.length !== 3 || ![...origin].every((c) => LETTER.test(c)))
    return null;
  if (carrier.length < 2 || carrier.length > 3 || dayOfYear === null || dayOfYear < 1 || dayOfYear > 366)
    return null;
  const leading = /^\d*/.exec(flightField)![0];
  const digits = leading.replace(/^0+/, '');
  if (digits === '') return null;
  const suffix = flightField.slice(leading.length).trim();

  return {
    designator: carrier + digits + suffix,
    date: dateOf(dayOfYear, today),
    origin,
    destination,
    seat,
    cabin: cabinOf(compartment),
    reference,
    passenger
  };
}

/** The pass gives a day of the year; the year is whichever puts it closest to today. */
function dateOf(dayOfYear: number, today: LocalDate): LocalDate {
  let best: LocalDate | null = null;
  for (const year of [today.year - 1, today.year, today.year + 1]) {
    const date = Temporal.PlainDate.from({ year, month: 1, day: 1 }).add({ days: dayOfYear - 1 });
    if (date.year !== year) continue;
    if (best === null || Math.abs(daysBetween(today, date)) < Math.abs(daysBetween(today, best))) best = date;
  }
  return best!;
}

const capitalised = (part: string) => {
  const lower = part.toLowerCase();
  return lower.charAt(0).toUpperCase() + lower.slice(1);
};

/** "SMITH/JANE MRS" → "Jane Smith". */
function passengerName(field: string): string {
  const raw = field.trim();
  const slash = raw.indexOf('/');
  const surname = (slash < 0 ? raw : raw.slice(0, slash)).trim();
  let given = slash < 0 ? '' : raw.slice(slash + 1).trim();
  for (const title of [' MR', ' MRS', ' MS', ' MISS', 'MR', 'MRS', 'MS']) {
    if (given.endsWith(title)) given = given.slice(0, -title.length);
  }
  return [given.trim(), surname]
    .filter((p) => p !== '')
    .map(capitalised)
    .join(' ');
}

function cabinOf(code: string): string | null {
  switch (code.toUpperCase()) {
    case 'F':
    case 'A':
    case 'P':
      return 'First';
    case 'J':
    case 'C':
    case 'D':
    case 'I':
    case 'Z':
    case 'R':
      return 'Business';
    case 'W':
      return 'Premium economy';
    case ' ':
      return null;
    default:
      return 'Economy';
  }
}
