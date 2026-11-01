// Waymark's domain model: trips, travelers, bookings, ideas and costs.
// A port of core/domain/.../model, with the same names and the same rules.
// Kotlin's sealed Segment class is a union on `kind`; its computed
// properties are functions here, because these values go into IndexedDB as
// plain objects.

import { daysBetween, systemZone, zonedAt, zoneOrUtc, type LocalDate, type ZonedDateTime } from './time';

// — Place ———————————————————————————————————————————————————————————————

/** Latitude/longitude in degrees. */
export interface LatLon {
  latitude: number;
  longitude: number;
}

/**
 * A point on the earth with enough context to render it, sort it into a
 * local day, and estimate travel to or from it. [code] is the IATA station
 * code for airports and stations, null for anything without one.
 */
export interface Place {
  name: string;
  code: string | null;
  city: string;
  country: string;
  /** Null when nobody knows where it is — a hotel typed by name, say. */
  latitude: number | null;
  longitude: number | null;
  timeZoneId: string;
  address: string | null;
}

export function place(fields: Partial<Place> & { name: string }): Place {
  return {
    code: null,
    city: '',
    country: '',
    latitude: null,
    longitude: null,
    timeZoneId: 'UTC',
    address: null,
    ...fields
  };
}

export function placeZone(p: Place): string {
  return zoneOrUtc(p.timeZoneId);
}

/** Short label for dense rows: the code when there is one, else the city. */
export function shortLabel(p: Place): string {
  return p.code ?? (p.city.trim() === '' ? p.name : p.city);
}

export function positionOf(p: Place): LatLon | null {
  return p.latitude != null && p.longitude != null ? { latitude: p.latitude, longitude: p.longitude } : null;
}

export function hasCoordinates(p: Place): boolean {
  return positionOf(p) !== null;
}

export function samePlace(a: Place, b: Place): boolean {
  return (
    a.name === b.name &&
    a.code === b.code &&
    a.city === b.city &&
    a.country === b.country &&
    a.latitude === b.latitude &&
    a.longitude === b.longitude &&
    a.timeZoneId === b.timeZoneId &&
    a.address === b.address
  );
}

// — Trip ————————————————————————————————————————————————————————————————

export interface Trip {
  id: string;
  name: string;
  destinationSummary: string;
  startEpochMillis: number;
  endEpochMillis: number;
  homeZoneId: string;
  archived: boolean;
}

export function trip(
  fields: Omit<Trip, 'homeZoneId' | 'archived'> & Partial<Pick<Trip, 'homeZoneId' | 'archived'>>
): Trip {
  return { homeZoneId: systemZone(), archived: false, ...fields };
}

export type TripStatus = 'UPCOMING' | 'ACTIVE' | 'PAST';

export function tripStartDate(t: Trip): LocalDate {
  return zonedAt(t.startEpochMillis, t.homeZoneId).toPlainDate();
}

export function tripEndDate(t: Trip): LocalDate {
  return zonedAt(t.endEpochMillis, t.homeZoneId).toPlainDate();
}

export function tripStatus(t: Trip, nowMillis: number = Date.now()): TripStatus {
  if (nowMillis < t.startEpochMillis) return 'UPCOMING';
  if (nowMillis > t.endEpochMillis) return 'PAST';
  return 'ACTIVE';
}

/** Whole days until departure; negative once under way. */
export function daysUntilStart(t: Trip, nowMillis: number = Date.now()): number {
  const today = zonedAt(nowMillis, t.homeZoneId).toPlainDate();
  return daysBetween(today, tripStartDate(t));
}

/** A trip with everything needed to render it, assembled by the repository. */
export interface TripDossier {
  trip: Trip;
  party: TripParty;
  segments: Segment[];
}

// — Travelers ———————————————————————————————————————————————————————————

export type SeatPreference = 'NONE' | 'WINDOW' | 'AISLE' | 'EXIT_ROW' | 'BULKHEAD';
export const SEAT_PREFERENCES: readonly SeatPreference[] = [
  'NONE',
  'WINDOW',
  'AISLE',
  'EXIT_ROW',
  'BULKHEAD'
];

/**
 * A passenger profile. Profiles are global — the same person can be carried
 * across trips — and are attached to a trip through [TripParty].
 */
export interface Traveler {
  id: string;
  fullName: string;
  nickname: string | null;
  seatPreference: SeatPreference;
  mealPreference: string | null;
  /** Frequent-flyer and hotel programme numbers, as printed: "BA 12345678". */
  loyaltyRefs: string[];
  contactPhone: string | null;
}

export function traveler(fields: Partial<Traveler> & { id: string; fullName: string }): Traveler {
  return {
    nickname: null,
    seatPreference: 'NONE',
    mealPreference: null,
    loyaltyRefs: [],
    contactPhone: null,
    ...fields
  };
}

export function displayName(t: Traveler): string {
  return t.nickname ?? t.fullName;
}

/** Up to two letters, used for the party marks throughout the app. */
export function initials(t: Traveler): string {
  const parts = t.fullName
    .trim()
    .split(/\s+/)
    .filter((p) => p !== '');
  if (parts.length === 0) return '?';
  if (parts.length === 1) return [...parts[0]!].slice(0, 2).join('').toUpperCase();
  return `${[...parts[0]!][0]}${[...parts[parts.length - 1]!][0]}`.toUpperCase();
}

/** The set of travelers on a trip, in display order. */
export interface TripParty {
  tripId: string;
  travelers: Traveler[];
}

export function partyById(party: TripParty, id: string): Traveler | null {
  return party.travelers.find((t) => t.id === id) ?? null;
}

export function resolveParty(party: TripParty, ids: Iterable<string>): Traveler[] {
  const wanted = new Set(ids);
  return party.travelers.filter((t) => wanted.has(t.id));
}

// — Segments ————————————————————————————————————————————————————————————

export type SegmentKind = 'FLIGHT' | 'LODGING' | 'GROUND' | 'EXPERIENCE';
export const SEGMENT_KINDS: readonly SegmentKind[] = ['FLIGHT', 'LODGING', 'GROUND', 'EXPERIENCE'];

export type GroundMode =
  'TRAIN' | 'TRANSIT' | 'BUS' | 'FERRY' | 'TAXI' | 'RIDESHARE' | 'RENTAL_CAR' | 'SHUTTLE' | 'WALK';
export const GROUND_MODES: readonly GroundMode[] = [
  'TRAIN',
  'TRANSIT',
  'BUS',
  'FERRY',
  'TAXI',
  'RIDESHARE',
  'RENTAL_CAR',
  'SHUTTLE',
  'WALK'
];

export function groundModeLabel(mode: GroundMode): string {
  const words = mode.toLowerCase().replace(/_/g, ' ');
  return words.charAt(0).toUpperCase() + words.slice(1);
}

/**
 * What every kind of booking carries. Every segment has its own start and end
 * zone: a red-eye that leaves on the 3rd and lands on the 4th is only legible
 * if both ends keep their own local clock.
 */
interface SegmentCommon {
  id: string;
  tripId: string;
  startEpochMillis: number;
  endEpochMillis: number;
  startZoneId: string;
  endZoneId: string;
  /** Traveler ids on this segment — empty for the whole party, else a subset. */
  travelerIds: string[];
  /** The booking reference, as printed on whatever confirmed it. */
  confirmationCode: string | null;
  /** Who it was booked through: an airline, an agent, "Direct booking". */
  bookedWith: string | null;
  note: string | null;
  /** Where this segment begins — an airport, a hotel, a trailhead. */
  origin: Place;
}

export interface Flight extends SegmentCommon {
  kind: 'FLIGHT';
  carrierCode: string;
  flightNumber: string;
  destination: Place;
  /** Ticket number per traveler — an e-ticket is issued to a person. */
  ticketNumbers: Record<string, string>;
  departureTerminal: string | null;
  departureGate: string | null;
  arrivalTerminal: string | null;
  arrivalGate: string | null;
  aircraft: string | null;
  cabin: string | null;
  seats: Record<string, string>;
  operatedBy: string | null;
}

export interface Lodging extends SegmentCommon {
  kind: 'LODGING';
  propertyName: string;
  roomDescription: string | null;
  checkInNote: string | null;
}

export interface Ground extends SegmentCommon {
  kind: 'GROUND';
  mode: GroundMode;
  destination: Place;
  provider: string | null;
  pickupInstruction: string | null;
}

export interface Experience extends SegmentCommon {
  kind: 'EXPERIENCE';
  name: string;
  category: string;
  curatedBy: string | null;
}

export type Segment = Flight | Lodging | Ground | Experience;

type CommonDefaults = 'travelerIds' | 'confirmationCode' | 'bookedWith' | 'note';
type Init<T extends Segment, Defaults extends keyof T> = Omit<T, 'kind' | Defaults> &
  Partial<Pick<T, Defaults>>;

const COMMON_DEFAULTS = { travelerIds: [], confirmationCode: null, bookedWith: null, note: null };

export function flight(
  fields: Init<
    Flight,
    | CommonDefaults
    | 'ticketNumbers'
    | 'departureTerminal'
    | 'departureGate'
    | 'arrivalTerminal'
    | 'arrivalGate'
    | 'aircraft'
    | 'cabin'
    | 'seats'
    | 'operatedBy'
  >
): Flight {
  return {
    kind: 'FLIGHT',
    ...COMMON_DEFAULTS,
    ticketNumbers: {},
    departureTerminal: null,
    departureGate: null,
    arrivalTerminal: null,
    arrivalGate: null,
    aircraft: null,
    cabin: null,
    seats: {},
    operatedBy: null,
    ...fields
  };
}

export function lodging(fields: Init<Lodging, CommonDefaults | 'roomDescription' | 'checkInNote'>): Lodging {
  return { kind: 'LODGING', ...COMMON_DEFAULTS, roomDescription: null, checkInNote: null, ...fields };
}

export function ground(fields: Init<Ground, CommonDefaults | 'provider' | 'pickupInstruction'>): Ground {
  return { kind: 'GROUND', ...COMMON_DEFAULTS, provider: null, pickupInstruction: null, ...fields };
}

export function experience(fields: Init<Experience, CommonDefaults | 'curatedBy'>): Experience {
  return { kind: 'EXPERIENCE', ...COMMON_DEFAULTS, curatedBy: null, ...fields };
}

/** Where it ends. The origin for stationary segments (lodging, experiences). */
export function destinationOf(s: Segment): Place {
  return s.kind === 'FLIGHT' || s.kind === 'GROUND' ? s.destination : s.origin;
}

export function segmentStart(s: Segment): ZonedDateTime {
  return zonedAt(s.startEpochMillis, s.startZoneId);
}

export function segmentEnd(s: Segment): ZonedDateTime {
  return zonedAt(s.endEpochMillis, s.endZoneId);
}

export function durationMillis(s: Segment): number {
  return s.endEpochMillis - s.startEpochMillis;
}

export function designator(f: Flight): string {
  return `${f.carrierCode}${f.flightNumber}`;
}

/** One-line title used in the timeline rail. */
export function segmentTitle(s: Segment): string {
  switch (s.kind) {
    case 'FLIGHT':
      return `${designator(s)} · ${shortLabel(s.origin)} → ${shortLabel(s.destination)}`;
    case 'LODGING':
      return s.propertyName;
    case 'GROUND':
      return `${groundModeLabel(s.mode)} · ${shortLabel(s.origin)} → ${shortLabel(s.destination)}`;
    case 'EXPERIENCE':
      return s.name;
  }
}

/**
 * Calendar nights in the hotel's own zone, not whole 24-hour periods: a
 * 15:00 check-in and an 11:00 check-out four days later is four nights.
 * A day room still counts as one.
 */
export function nights(l: Lodging): number {
  return Math.max(1, daysBetween(segmentStart(l).toPlainDate(), segmentEnd(l).toPlainDate()));
}

/** Sorted by clock instant — the only ordering that survives time-zone changes. */
export function chronological<T extends Segment>(segments: readonly T[]): T[] {
  return [...segments].sort(
    (a, b) =>
      a.startEpochMillis - b.startEpochMillis ||
      a.endEpochMillis - b.endEpochMillis ||
      (a.id < b.id ? -1 : a.id > b.id ? 1 : 0)
  );
}

export function involves(s: Segment, travelerId: string): boolean {
  return s.travelerIds.length === 0 || s.travelerIds.includes(travelerId);
}

/** A copy with the fields every kind shares replaced. */
export function copyCommon<T extends Segment>(s: T, changes: Partial<Omit<SegmentCommon, 'origin'>>): T {
  return { ...s, ...changes };
}

/**
 * The party on a booking, in the form it is stored: everyone on the trip is
 * written as nobody in particular, so a traveler added later is on it too.
 * Ids of people no longer on the trip are dropped.
 */
export function normaliseParty(travelerIds: Iterable<string>, party: Iterable<string>): string[] {
  const all = new Set(party);
  const known = [...new Set(travelerIds)].filter((id) => all.has(id));
  return known.length === 0 || known.length === all.size ? [] : known;
}

// — Ideas ———————————————————————————————————————————————————————————————

/** The four things a trip list is ever made of. */
export type IdeaKind = 'SEE' | 'EAT' | 'DO' | 'SHOP';
export const IDEA_KINDS: readonly IdeaKind[] = ['SEE', 'EAT', 'DO', 'SHOP'];

export const IDEA_KIND_TEXT: Record<IdeaKind, { label: string; heading: string; hint: string }> = {
  SEE: { label: 'See', heading: 'Places to see', hint: 'Museums, views, landmarks, a walk' },
  EAT: { label: 'Eat', heading: 'Food and tables', hint: 'A restaurant, or a dish to look for' },
  DO: { label: 'Do', heading: 'Things to do', hint: 'Tours, classes, swimming, a show' },
  SHOP: { label: 'Shop', heading: 'Shops and markets', hint: 'Markets, bookshops, something to bring back' }
};

export type IdeaStatus = 'SAVED' | 'SCHEDULED' | 'DONE' | 'DISMISSED';
export const IDEA_STATUSES: readonly IdeaStatus[] = ['SAVED', 'SCHEDULED', 'DONE', 'DISMISSED'];

export const IDEA_STATUS_LABEL: Record<IdeaStatus, string> = {
  SAVED: 'Saved',
  SCHEDULED: 'On the timeline',
  DONE: 'Done',
  DISMISSED: 'Not this trip'
};

export type PriceBand = 'FREE' | 'LOW' | 'MEDIUM' | 'HIGH';
export const PRICE_BANDS: readonly PriceBand[] = ['FREE', 'LOW', 'MEDIUM', 'HIGH'];

/** Deliberately not currency: a band survives a change of country. */
export const PRICE_BAND_LABEL: Record<PriceBand, string> = {
  FREE: 'Free',
  LOW: 'Cheap',
  MEDIUM: 'Mid',
  HIGH: 'Splurge'
};

/**
 * Something the party wants to do, see or eat, which does not yet have a
 * time on it — and may never need one.
 */
export interface Idea {
  id: string;
  tripId: string;
  title: string;
  kind: IdeaKind;
  city: string;
  place: Place | null;
  note: string | null;
  priceBand: PriceBand | null;
  typicalMinutes: number | null;
  bestTime: string | null;
  /** A day without a time: "the market, Tuesday, sometime". */
  plannedDate: LocalDate | null;
  interestedTravelerIds: string[];
  status: IdeaStatus;
  scheduledSegmentId: string | null;
  addedAtMillis: number;
}

export function idea(fields: Partial<Idea> & Pick<Idea, 'id' | 'tripId' | 'title' | 'kind' | 'city'>): Idea {
  return {
    place: null,
    note: null,
    priceBand: null,
    typicalMinutes: null,
    bestTime: null,
    plannedDate: null,
    interestedTravelerIds: [],
    status: 'SAVED',
    scheduledSegmentId: null,
    addedAtMillis: Date.now(),
    ...fields
  };
}

export function ideaHasLocation(i: Idea): boolean {
  return i.place !== null && hasCoordinates(i.place);
}

// — Money and costs —————————————————————————————————————————————————————

/**
 * An amount of one currency, held in its smallest unit — cents, pence, yen —
 * so adding up a trip never meets a binary fraction.
 */
export interface Money {
  minor: number;
  currency: string;
}

const digitCache = new Map<string, number>();

/** How many decimal places [currency] is written with: 2 for EUR, 0 for JPY, 3 for KWD. */
export function currencyDigits(currency: string): number {
  const cached = digitCache.get(currency);
  if (cached !== undefined) return cached;
  let digits = 2;
  if (/^[A-Za-z]{3}$/.test(currency)) {
    try {
      digits = new Intl.NumberFormat('en', { style: 'currency', currency }).resolvedOptions()
        .maximumFractionDigits!;
    } catch {
      digits = 2;
    }
  }
  digits = Math.max(0, digits);
  digitCache.set(currency, digits);
  return digits;
}

/** The amount in whole units, as a decimal string: "12.50", "1200". */
export function moneyAmountText(m: Money): string {
  const places = currencyDigits(m.currency);
  const negative = m.minor < 0;
  const digits = String(Math.abs(m.minor)).padStart(places + 1, '0');
  const whole = places === 0 ? digits : digits.slice(0, -places);
  const fraction = places === 0 ? '' : `.${digits.slice(-places)}`;
  return `${negative ? '-' : ''}${whole}${fraction}`;
}

/** The amount in whole units, as a number (for charts and conversions, not sums). */
export function moneyAmount(m: Money): number {
  return m.minor / 10 ** currencyDigits(m.currency);
}

/** "€12.50", "¥1,200", in the reader's own conventions. */
export function formatMoney(m: Money, locale?: string): string {
  const places = currencyDigits(m.currency);
  try {
    return new Intl.NumberFormat(locale, {
      style: 'currency',
      currency: m.currency,
      minimumFractionDigits: places,
      maximumFractionDigits: places
    }).format(moneyAmount(m));
  } catch {
    return `${moneyAmountText(m)} ${m.currency}`;
  }
}

export function addMoney(a: Money, b: Money): Money {
  if (a.currency !== b.currency) throw new Error(`Adding ${a.currency} to ${b.currency}`);
  return { minor: a.minor + b.minor, currency: a.currency };
}

/**
 * What a person typed, read as an amount of [currency]: "12.5", "12,50",
 * "1 200", "1,200.00". Null for anything that is not a positive amount with
 * no more decimals than the currency has.
 */
export function parseMoney(text: string, currency: string): Money | null {
  const cleaned = [...text.trim()].filter((c) => c !== ' ' && c !== ' ' && c !== "'").join('');
  if (cleaned === '') return null;
  const places = currencyDigits(currency);
  const decimal = decimalSeparator(cleaned, places);
  const cut = decimal === null ? -1 : cleaned.lastIndexOf(decimal);
  const whole = decimal === null ? cleaned : cleaned.slice(0, cut);
  const fraction = decimal === null ? '' : cleaned.slice(cut + 1);
  const normalised = whole.replace(/[,.]/g, '') + (decimal === null ? '' : `.${fraction}`);
  const match = /^([+-]?)(\d*)(?:\.(\d*))?$/.exec(normalised);
  if (!match || (match[2] === '' && (match[3] ?? '') === '')) return null;
  const [, sign, intPart = '', fracPart = ''] = match;
  if (fracPart.length > places) return null;
  const minor = Number((intPart || '0') + fracPart.padEnd(places, '0'));
  if (sign === '-' || minor <= 0 || !Number.isSafeInteger(minor)) return null;
  return { minor, currency };
}

/**
 * Which of '.' and ',' marks the decimals in [text], if either does. With
 * both, the later one. With one, used once, it is the decimal mark unless
 * exactly three digits follow it in a currency that does not have three
 * decimals — "1,200" is twelve hundred, "12,50" twelve and a half. Used more
 * than once, it groups thousands.
 */
function decimalSeparator(text: string, places: number): '.' | ',' | null {
  const dot = text.lastIndexOf('.');
  const comma = text.lastIndexOf(',');
  if (dot >= 0 && comma >= 0) return dot > comma ? '.' : ',';
  const mark = dot >= 0 ? '.' : comma >= 0 ? ',' : null;
  if (mark === null) return null;
  if (text.split(mark).length - 1 > 1) return null;
  const after = text.length - text.lastIndexOf(mark) - 1;
  return after === 3 && places !== 3 ? null : mark;
}

/**
 * The currency of where the device is set, or euros when it has none.
 * [currencyOf] maps a region (ISO 3166-1 alpha-2) to its currency.
 */
export function localCurrency(
  currencyOf: (region: string) => string | null | undefined,
  locale: string = typeof navigator === 'undefined' ? 'en-US' : navigator.language
): string {
  try {
    const region = new Intl.Locale(locale).maximize().region;
    return (region && currencyOf(region)) || 'EUR';
  } catch {
    return 'EUR';
  }
}

export type ExpenseCategory = 'TRAVEL' | 'STAY' | 'FOOD' | 'ACTIVITY' | 'SHOPPING' | 'OTHER';
export const EXPENSE_CATEGORIES: readonly ExpenseCategory[] = [
  'TRAVEL',
  'STAY',
  'FOOD',
  'ACTIVITY',
  'SHOPPING',
  'OTHER'
];
export const EXPENSE_CATEGORY_LABEL: Record<ExpenseCategory, string> = {
  TRAVEL: 'Travel',
  STAY: 'Stays',
  FOOD: 'Food and drink',
  ACTIVITY: 'Things to do',
  SHOPPING: 'Shopping',
  OTHER: 'Other'
};

/**
 * Something one traveler paid for and the party shares. [splitAmong] names
 * who shares it; empty means the whole party as it is now.
 */
export interface Expense {
  id: string;
  tripId: string;
  description: string;
  amount: Money;
  paidBy: string;
  splitAmong: string[];
  spentOn: LocalDate;
  category: ExpenseCategory;
  addedAtMillis: number;
}

export function expense(
  fields: Partial<Expense> & Pick<Expense, 'id' | 'tripId' | 'description' | 'amount' | 'paidBy' | 'spentOn'>
): Expense {
  return { splitAmong: [], category: 'OTHER', addedAtMillis: 0, ...fields };
}

/** How many units of the trip's settling currency one unit of [currency] is worth. */
export interface ExchangeRate {
  currency: string;
  perUnit: number;
  asOf: LocalDate;
  /** Typed by the traveler, and so never replaced by a live rate. */
  manual: boolean;
}

/** A trip's spending: what was spent, what it is settled in, and at what rates. */
export interface TripCosts {
  /** Null until somebody chooses; the device's own currency is offered. */
  settleCurrency: string | null;
  expenses: Expense[];
  rates: ExchangeRate[];
}

export const EMPTY_COSTS: TripCosts = Object.freeze({ settleCurrency: null, expenses: [], rates: [] });

export function tripCosts(fields: Partial<TripCosts> = {}): TripCosts {
  return { settleCurrency: null, expenses: [], rates: [], ...fields };
}

// — Weather, reminders, theme ———————————————————————————————————————————

/** Where a day's weather comes from: a forecast, or the same date last year. */
export type WeatherBasis = 'FORECAST' | 'LAST_YEAR';

/** One day's weather at one place, in Celsius and millimetres. */
export interface WeatherDay {
  date: LocalDate;
  /** The WMO weather code. */
  code: number;
  highC: number;
  lowC: number;
  /** Chance of rain, in percent; forecasts only. */
  rainChance: number | null;
  /** Rain that fell, in millimetres; last year's record only. */
  rainMm: number | null;
  basis: WeatherBasis;
}

/**
 * A reminder the app has raised: something booked is about to start. Each is
 * raised once per booking and start time.
 */
export interface RaisedReminder {
  signature: string;
  segmentId: string;
  label: string;
  headline: string;
  detail: string;
  startEpochMillis: number;
  raisedAtMillis: number;
  posted: boolean;
  acknowledged: boolean;
}

/** Follow the system, or always Dusk, or always Dawn — the traveler's choice. */
export type ThemeMode = 'SYSTEM' | 'DUSK' | 'DAWN';
