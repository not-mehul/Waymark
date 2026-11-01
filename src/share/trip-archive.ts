// Trips as a file: a backup of everything, or one trip sent to the rest of
// the party — the same "waymark" format, version 1, the Kotlin app writes.
//
// The format is its own, not the database's rows, so storage can change
// shape without making old files unreadable. Reading takes every trip in the
// file as the current version of that trip: one already here is replaced,
// one that is not is added.
//
// The JSON matches kotlinx.serialization's output as the Kotlin app was set
// up (nulls and defaults left out, unknown keys ignored), so a file written
// here opens in the old app and the other way round.

import { Unzlib, zlibSync } from 'fflate';
import {
  EXPENSE_CATEGORIES,
  GROUND_MODES,
  IDEA_KINDS,
  IDEA_STATUSES,
  PRICE_BANDS,
  SEAT_PREFERENCES,
  destinationOf,
  experience,
  flight,
  ground,
  idea,
  lodging,
  place,
  traveler,
  trip,
  tripCosts,
  type Idea,
  type Place,
  type Segment,
  type Traveler,
  type Trip,
  type TripCosts
} from '../domain/model';
import { parseDate } from '../domain/time';
import { airportPlace, findAirport } from '../catalog/airports';

export const FORMAT = 'waymark';
export const VERSION = 1;
export const MIME = 'application/json';

/** The most a QR code carries in byte mode at the lowest error correction, less the link prefix. */
export const MAX_CODE_CHARS = 2_900;
const MAX_INFLATED_BYTES = 1_000_000;

export class InvalidArchive extends Error {
  override name = 'InvalidArchive';
  constructor(message: string, options?: { cause?: unknown }) {
    super(message, options);
  }
}

/** One trip and everything on it, as the domain knows it. */
export interface TripBundle {
  trip: Trip;
  travelers: Traveler[];
  segments: Segment[];
  ideas: Idea[];
  /** Null when the file says nothing about costs, as one from an older Waymark does. */
  costs: TripCosts | null;
}

// — The file's own shapes ———————————————————————————————————————————————

export interface WaymarkArchive {
  format: string;
  version: number;
  exportedAt: number;
  travelers: ArchiveTraveler[];
  trips: ArchiveTrip[];
}

export interface ArchiveTrip {
  id: string;
  name: string;
  destinationSummary: string;
  start: number;
  end: number;
  homeZone: string;
  archived: boolean;
  /** Traveler ids, in the party's order. */
  party: string[];
  segments: ArchiveSegment[];
  ideas: ArchiveIdea[];
  costs: ArchiveCosts | null;
}

export interface ArchiveCosts {
  settleIn: string | null;
  expenses: ArchiveExpense[];
  rates: ArchiveRate[];
}

export interface ArchiveExpense {
  id: string;
  description: string;
  /** In the currency's smallest unit. */
  amount: number;
  currency: string;
  paidBy: string;
  /** Traveler ids; empty for the whole party. */
  among: string[];
  /** ISO date. */
  day: string;
  category: string;
  added: number;
}

export interface ArchiveRate {
  currency: string;
  perUnit: number;
  asOf: string;
  manual: boolean;
}

export interface ArchiveTraveler {
  id: string;
  fullName: string;
  nickname: string | null;
  seat: string;
  meal: string | null;
  loyalty: string[];
  phone: string | null;
}

export interface ArchivePlace {
  /** Empty only in a code, where a station travels as its code alone. */
  name: string;
  code: string | null;
  city: string;
  country: string;
  lat: number | null;
  lon: number | null;
  zone: string;
  address: string | null;
}

/** Every kind of booking in one flat shape; [kind] says which fields mean anything. */
export interface ArchiveSegment {
  id: string;
  kind: string;
  start: number;
  end: number;
  /** Empty only in a code, where it is the origin's zone. */
  startZone: string;
  endZone: string;
  travelers: string[];
  reference: string | null;
  bookedWith: string | null;
  note: string | null;
  origin: ArchivePlace;
  destination: ArchivePlace | null;
  carrier: string | null;
  number: string | null;
  departureTerminal: string | null;
  departureGate: string | null;
  arrivalTerminal: string | null;
  arrivalGate: string | null;
  aircraft: string | null;
  cabin: string | null;
  seats: Record<string, string>;
  tickets: Record<string, string>;
  operatedBy: string | null;
  /** Property name for a stay; the booking's name for an experience. */
  name: string | null;
  room: string | null;
  checkInNote: string | null;
  mode: string | null;
  provider: string | null;
  pickup: string | null;
  category: string | null;
  tips: string | null;
}

export interface ArchiveIdea {
  id: string;
  title: string;
  kind: string;
  city: string;
  place: ArchivePlace | null;
  note: string | null;
  price: string | null;
  minutes: number | null;
  bestTime: string | null;
  plannedDay: string | null;
  interested: string[];
  status: string;
  scheduledSegmentId: string | null;
  added: number;
}

// — Schema: field order, defaults and required fields, as kotlinx sees them ——

/** A nested object is its schema; a list of objects is `{ list: schema }`. */
type Kind = 'string' | 'number' | 'boolean' | 'strings' | 'map' | Schema | { list: Schema };
interface Field {
  name: string;
  kind: Kind;
  /** Absent means required; `null` means optional and null by default. */
  default?: unknown;
  nullable?: boolean;
}
type Schema = readonly Field[];

const req = (name: string, kind: Kind): Field => ({ name, kind });
const opt = (name: string, kind: Kind, value: unknown): Field => ({ name, kind, default: value });
const nul = (name: string, kind: Kind): Field => ({ name, kind, default: null, nullable: true });

const PLACE: Schema = [
  opt('name', 'string', ''),
  nul('code', 'string'),
  opt('city', 'string', ''),
  opt('country', 'string', ''),
  nul('lat', 'number'),
  nul('lon', 'number'),
  opt('zone', 'string', 'UTC'),
  nul('address', 'string')
];

const SEGMENT: Schema = [
  req('id', 'string'),
  req('kind', 'string'),
  req('start', 'number'),
  req('end', 'number'),
  opt('startZone', 'string', ''),
  opt('endZone', 'string', ''),
  opt('travelers', 'strings', []),
  nul('reference', 'string'),
  nul('bookedWith', 'string'),
  nul('note', 'string'),
  req('origin', PLACE),
  nul('destination', PLACE),
  nul('carrier', 'string'),
  nul('number', 'string'),
  nul('departureTerminal', 'string'),
  nul('departureGate', 'string'),
  nul('arrivalTerminal', 'string'),
  nul('arrivalGate', 'string'),
  nul('aircraft', 'string'),
  nul('cabin', 'string'),
  opt('seats', 'map', {}),
  opt('tickets', 'map', {}),
  nul('operatedBy', 'string'),
  nul('name', 'string'),
  nul('room', 'string'),
  nul('checkInNote', 'string'),
  nul('mode', 'string'),
  nul('provider', 'string'),
  nul('pickup', 'string'),
  nul('category', 'string'),
  nul('tips', 'string')
];

const IDEA: Schema = [
  req('id', 'string'),
  req('title', 'string'),
  req('kind', 'string'),
  opt('city', 'string', ''),
  nul('place', PLACE),
  nul('note', 'string'),
  nul('price', 'string'),
  nul('minutes', 'number'),
  nul('bestTime', 'string'),
  nul('plannedDay', 'string'),
  opt('interested', 'strings', []),
  opt('status', 'string', 'SAVED'),
  nul('scheduledSegmentId', 'string'),
  opt('added', 'number', 0)
];

const EXPENSE: Schema = [
  req('id', 'string'),
  req('description', 'string'),
  req('amount', 'number'),
  req('currency', 'string'),
  req('paidBy', 'string'),
  opt('among', 'strings', []),
  req('day', 'string'),
  opt('category', 'string', 'OTHER'),
  opt('added', 'number', 0)
];

const RATE: Schema = [
  req('currency', 'string'),
  req('perUnit', 'number'),
  req('asOf', 'string'),
  opt('manual', 'boolean', false)
];

const COSTS: Schema = [
  nul('settleIn', 'string'),
  opt('expenses', { list: EXPENSE }, []),
  opt('rates', { list: RATE }, [])
];

const TRAVELER: Schema = [
  req('id', 'string'),
  req('fullName', 'string'),
  nul('nickname', 'string'),
  opt('seat', 'string', 'NONE'),
  nul('meal', 'string'),
  opt('loyalty', 'strings', []),
  nul('phone', 'string')
];

const TRIP: Schema = [
  req('id', 'string'),
  req('name', 'string'),
  opt('destinationSummary', 'string', ''),
  req('start', 'number'),
  req('end', 'number'),
  req('homeZone', 'string'),
  opt('archived', 'boolean', false),
  opt('party', 'strings', []),
  opt('segments', { list: SEGMENT }, []),
  opt('ideas', { list: IDEA }, []),
  nul('costs', COSTS)
];

const ARCHIVE: Schema = [
  req('format', 'string'),
  req('version', 'number'),
  req('exportedAt', 'number'),
  opt('travelers', { list: TRAVELER }, []),
  opt('trips', { list: TRIP }, [])
];

const isList = (kind: Kind): kind is { list: Schema } => typeof kind === 'object' && 'list' in kind;

function isDefault(value: unknown, fallback: unknown): boolean {
  if (Array.isArray(fallback)) return Array.isArray(value) && value.length === 0;
  if (fallback !== null && typeof fallback === 'object') {
    return value !== null && typeof value === 'object' && Object.keys(value).length === 0;
  }
  return value === fallback;
}

/** The object as kotlinx writes it: declaration order, no nulls, no defaults. */
function encodeWith(schema: Schema, value: Record<string, unknown>): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const field of schema) {
    const v = value[field.name];
    if (v == null) continue;
    if ('default' in field && isDefault(v, field.default)) continue;
    const kind = field.kind;
    if (isList(kind)) out[field.name] = (v as Record<string, unknown>[]).map((x) => encodeWith(kind.list, x));
    else if (typeof kind === 'object') out[field.name] = encodeWith(kind, v as Record<string, unknown>);
    else if (field.kind === 'map') out[field.name] = { ...(v as Record<string, string>) };
    else out[field.name] = v;
  }
  return out;
}

const malformed = (path: string) => new InvalidArchive('This is not a Waymark file.', { cause: path });

/** The object as kotlinx reads it: unknown keys ignored, defaults filled, types checked. */
function decodeWith(schema: Schema, value: unknown, path: string): Record<string, unknown> {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) throw malformed(path);
  const input = value as Record<string, unknown>;
  const out: Record<string, unknown> = {};
  for (const field of schema) {
    const at = `${path}.${field.name}`;
    const v = input[field.name];
    if (v === undefined || (v === null && field.nullable)) {
      if (!('default' in field)) throw malformed(at);
      out[field.name] = Array.isArray(field.default)
        ? []
        : field.default && typeof field.default === 'object'
          ? {}
          : field.default;
      continue;
    }
    out[field.name] = decodeValue(field.kind, v, at);
  }
  return out;
}

function decodeValue(kind: Kind, v: unknown, at: string): unknown {
  if (isList(kind)) {
    if (!Array.isArray(v)) throw malformed(at);
    return v.map((x, i) => decodeWith(kind.list, x, `${at}[${i}]`));
  }
  if (typeof kind === 'object') return decodeWith(kind, v, at);
  switch (kind) {
    case 'string':
      if (typeof v !== 'string') throw malformed(at);
      return v;
    case 'number':
      if (typeof v !== 'number' || !Number.isFinite(v)) throw malformed(at);
      return v;
    case 'boolean':
      if (typeof v !== 'boolean') throw malformed(at);
      return v;
    case 'strings':
      if (!Array.isArray(v) || v.some((s) => typeof s !== 'string')) throw malformed(at);
      return [...v];
    case 'map':
      if (
        v === null ||
        typeof v !== 'object' ||
        Array.isArray(v) ||
        Object.values(v).some((s) => typeof s !== 'string')
      ) {
        throw malformed(at);
      }
      return { ...(v as Record<string, string>) };
  }
}

// — Building and reading archives ———————————————————————————————————————

export function archiveOf(bundles: readonly TripBundle[], nowMillis: number = Date.now()): WaymarkArchive {
  const seen = new Set<string>();
  const travelers: ArchiveTraveler[] = [];
  for (const t of bundles.flatMap((b) => b.travelers)) {
    if (seen.has(t.id)) continue;
    seen.add(t.id);
    travelers.push(travelerToArchive(t));
  }
  return {
    format: FORMAT,
    version: VERSION,
    exportedAt: nowMillis,
    travelers,
    trips: bundles.map((b) => ({
      id: b.trip.id,
      name: b.trip.name,
      destinationSummary: b.trip.destinationSummary,
      start: b.trip.startEpochMillis,
      end: b.trip.endEpochMillis,
      homeZone: b.trip.homeZoneId,
      archived: b.trip.archived,
      party: b.travelers.map((t) => t.id),
      segments: b.segments.map(segmentToArchive),
      ideas: b.ideas.map(ideaToArchive),
      costs: b.costs ? costsToArchive(b.costs) : null
    }))
  };
}

/** Pretty-printed, as the Kotlin app writes it: a person can open the file and read their trip. */
export function encodeArchive(archive: WaymarkArchive): string {
  return JSON.stringify(encodeWith(ARCHIVE, archive as unknown as Record<string, unknown>), null, 4);
}

function compactJson(archive: WaymarkArchive): string {
  return JSON.stringify(encodeWith(ARCHIVE, archive as unknown as Record<string, unknown>));
}

export function decodeArchive(text: string): WaymarkArchive {
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch (error) {
    throw new InvalidArchive('This is not a Waymark file.', { cause: error });
  }
  const archive = decodeWith(ARCHIVE, parsed, '$') as unknown as WaymarkArchive;
  if (archive.format !== FORMAT) throw new InvalidArchive('This is not a Waymark file.');
  if (archive.version > VERSION) {
    throw new InvalidArchive('This file is from a newer Waymark. Update the app to open it.');
  }
  return restore(archive);
}

/** The archive's trips as the domain knows them. */
export function bundlesOf(archive: WaymarkArchive): TripBundle[] {
  const travelers = new Map(archive.travelers.map((t) => [t.id, travelerFromArchive(t)]));
  return archive.trips.map((t) => ({
    trip: trip({
      id: t.id,
      name: t.name,
      destinationSummary: t.destinationSummary,
      startEpochMillis: t.start,
      endEpochMillis: t.end,
      homeZoneId: t.homeZone,
      archived: t.archived
    }),
    travelers: t.party.map((id) => travelers.get(id)).filter((x): x is Traveler => x !== undefined),
    segments: t.segments.map((s) => segmentFromArchive(s, t.id)).filter((x): x is Segment => x !== null),
    ideas: t.ideas.map((i) => ideaFromArchive(i, t.id)).filter((x): x is Idea => x !== null),
    costs: t.costs ? costsFromArchive(t.costs, t.id) : null
  }));
}

// — QR codes —————————————————————————————————————————————————————————————

/** How much of a trip a code carries: as much as fits, in this order. */
export type CodeDetail = 'EVERYTHING' | 'BOOKINGS_AND_NOTES' | 'BOOKINGS';
export const CODE_DETAILS: readonly CodeDetail[] = ['EVERYTHING', 'BOOKINGS_AND_NOTES', 'BOOKINGS'];
export const CODE_DETAIL_OMITS: Record<CodeDetail, string | null> = {
  EVERYTHING: null,
  BOOKINGS_AND_NOTES: 'the ideas list',
  BOOKINGS: 'the ideas list and long notes'
};

export interface TripCode {
  payload: string;
  detail: CodeDetail;
}

/**
 * The archive as a QR code's payload, carrying as much as fits. Null when
 * even the bookings alone are more than a code can carry.
 */
export function toCode(archive: WaymarkArchive): TripCode | null {
  for (const detail of CODE_DETAILS) {
    const payload = encodeCode(lean(trimmed(archive, detail)));
    if (payload !== null) return { payload, detail };
  }
  return null;
}

function trimmed(archive: WaymarkArchive, detail: CodeDetail): WaymarkArchive {
  switch (detail) {
    case 'EVERYTHING':
      return archive;
    case 'BOOKINGS_AND_NOTES':
      return { ...archive, trips: archive.trips.map((t) => ({ ...t, ideas: [] })) };
    case 'BOOKINGS':
      return {
        ...archive,
        trips: archive.trips.map((t) => ({
          ...t,
          ideas: [],
          segments: t.segments.map((s) => ({ ...s, note: null, checkInNote: null, pickup: null, tips: null }))
        }))
      };
  }
}

const samePlaceRow = (a: ArchivePlace, b: ArchivePlace) =>
  a.name === b.name &&
  a.code === b.code &&
  a.city === b.city &&
  a.country === b.country &&
  a.lat === b.lat &&
  a.lon === b.lon &&
  a.zone === b.zone &&
  a.address === b.address;

/**
 * Everything a code can leave out because the receiving phone can work it
 * out: a station is its code, a booking's zones are its places' zones, and a
 * stay's destination is its origin. Costs stay out of codes altogether.
 */
function lean(archive: WaymarkArchive): WaymarkArchive {
  return {
    ...archive,
    trips: archive.trips.map((t) => ({
      ...t,
      costs: null,
      segments: t.segments.map((row) => {
        const destination =
          row.destination && !samePlaceRow(row.destination, row.origin) ? row.destination : null;
        return {
          ...row,
          origin: leanPlace(row.origin),
          destination: destination ? leanPlace(destination) : null,
          startZone: row.startZone === row.origin.zone ? '' : row.startZone,
          endZone: row.endZone === (destination ?? row.origin).zone ? '' : row.endZone
        };
      })
    }))
  };
}

function leanPlace(p: ArchivePlace): ArchivePlace {
  if (p.code === null) return p;
  const found = findAirport(p.code);
  if (!found) return p;
  return samePlaceRow(placeToArchive(airportPlace(found)), p) ? archivePlace({ code: p.code }) : p;
}

/** The inverse of [lean], run on every read so a lean archive reads whole. */
function restore(archive: WaymarkArchive): WaymarkArchive {
  return {
    ...archive,
    trips: archive.trips.map((t) => ({
      ...t,
      segments: t.segments.map((row) => {
        const origin = restorePlace(row.origin);
        const destination = row.destination ? restorePlace(row.destination) : origin;
        return {
          ...row,
          origin,
          destination,
          startZone: row.startZone || origin.zone,
          endZone: row.endZone || destination.zone
        };
      })
    }))
  };
}

function restorePlace(p: ArchivePlace): ArchivePlace {
  if (p.code === null || p.name !== '') return p;
  const found = findAirport(p.code);
  return found ? placeToArchive(airportPlace(found)) : { ...p, name: p.code };
}

function encodeCode(archive: WaymarkArchive): string | null {
  const bytes = new TextEncoder().encode(compactJson(archive));
  const code = base64UrlEncode(zlibSync(bytes, { level: 9 }));
  return code.length <= MAX_CODE_CHARS ? code : null;
}

export function fromCode(code: string): WaymarkArchive {
  const bytes = base64UrlDecode(code.trim());
  if (bytes === null) throw new InvalidArchive('That code is not a Waymark trip.');
  const chunks: Uint8Array[] = [];
  let size = 0;
  let tooLarge = false;
  const inflater = new Unzlib((chunk) => {
    size += chunk.length;
    // A trip is kilobytes; a "trip" that inflates past a megabyte is not one.
    if (size > MAX_INFLATED_BYTES) tooLarge = true;
    else chunks.push(chunk);
  });
  try {
    inflater.push(bytes, true);
  } catch (error) {
    if (tooLarge) throw new InvalidArchive('That code is too large to be a trip.');
    // A cut-off stream yields what it had, as java.util.zip does; the JSON
    // check below then refuses it.
    if (!/unexpected EOF/i.test(String((error as Error)?.message))) {
      throw new InvalidArchive('That code is not a Waymark trip.', { cause: error });
    }
  }
  if (tooLarge) throw new InvalidArchive('That code is too large to be a trip.');
  const out = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) {
    out.set(chunk, offset);
    offset += chunk.length;
  }
  return decodeArchive(new TextDecoder().decode(out));
}

const B64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_';

/** Base64, URL alphabet, no padding (java.util.Base64.getUrlEncoder().withoutPadding()). */
export function base64UrlEncode(bytes: Uint8Array): string {
  let out = '';
  for (let i = 0; i < bytes.length; i += 3) {
    const n = (bytes[i]! << 16) | ((bytes[i + 1] ?? 0) << 8) | (bytes[i + 2] ?? 0);
    const chars = Math.min(4, Math.ceil(((bytes.length - i) * 8) / 6));
    for (let c = 0; c < chars; c++) out += B64[(n >> (18 - 6 * c)) & 63];
  }
  return out;
}

/** The URL decoder: padding optional, anything outside the alphabet refused. */
export function base64UrlDecode(text: string): Uint8Array | null {
  const body = text.replace(/=+$/, '');
  if (text.length - body.length > 2 || !/^[A-Za-z0-9_-]*$/.test(body) || body.length % 4 === 1) return null;
  if (text.length !== body.length && text.length % 4 !== 0) return null;
  const out = new Uint8Array(Math.floor((body.length * 6) / 8));
  let bits = 0;
  let value = 0;
  let at = 0;
  for (const c of body) {
    value = (value << 6) | B64.indexOf(c);
    bits += 6;
    if (bits >= 8) {
      bits -= 8;
      out[at++] = (value >> bits) & 0xff;
    }
  }
  return out;
}

// — Domain ⇄ file ———————————————————————————————————————————————————————

function archivePlace(fields: Partial<ArchivePlace>): ArchivePlace {
  return {
    name: '',
    code: null,
    city: '',
    country: '',
    lat: null,
    lon: null,
    zone: 'UTC',
    address: null,
    ...fields
  };
}

function placeToArchive(p: Place): ArchivePlace {
  return {
    name: p.name,
    code: p.code,
    city: p.city,
    country: p.country,
    lat: p.latitude,
    lon: p.longitude,
    zone: p.timeZoneId,
    address: p.address
  };
}

function placeFromArchive(p: ArchivePlace): Place {
  return place({
    name: p.name,
    code: p.code,
    city: p.city,
    country: p.country,
    latitude: p.lat,
    longitude: p.lon,
    timeZoneId: p.zone,
    address: p.address
  });
}

function costsToArchive(c: TripCosts): ArchiveCosts {
  return {
    settleIn: c.settleCurrency,
    expenses: c.expenses.map((e) => ({
      id: e.id,
      description: e.description,
      amount: e.amount.minor,
      currency: e.amount.currency,
      paidBy: e.paidBy,
      among: [...e.splitAmong].sort(),
      day: e.spentOn.toString(),
      category: e.category,
      added: e.addedAtMillis
    })),
    rates: c.rates.map((r) => ({
      currency: r.currency,
      perUnit: r.perUnit,
      asOf: r.asOf.toString(),
      manual: r.manual
    }))
  };
}

function costsFromArchive(c: ArchiveCosts, tripId: string): TripCosts {
  return tripCosts({
    settleCurrency: c.settleIn !== null && c.settleIn.length === 3 ? c.settleIn : null,
    expenses: c.expenses.flatMap((row) => {
      const day = parseDate(row.day);
      if (day === null || row.amount <= 0 || row.currency.length !== 3) return [];
      return [
        {
          id: row.id,
          tripId,
          description: row.description,
          amount: { minor: row.amount, currency: row.currency.toUpperCase() },
          paidBy: row.paidBy,
          splitAmong: [...new Set(row.among)],
          spentOn: day,
          category: (EXPENSE_CATEGORIES as readonly string[]).includes(row.category)
            ? (row.category as TripCosts['expenses'][number]['category'])
            : 'OTHER',
          addedAtMillis: row.added
        }
      ];
    }),
    rates: c.rates.flatMap((row) => {
      const asOf = parseDate(row.asOf);
      if (asOf === null || !(row.perUnit > 0) || !Number.isFinite(row.perUnit)) return [];
      return [{ currency: row.currency, perUnit: row.perUnit, asOf, manual: row.manual }];
    })
  });
}

function travelerToArchive(t: Traveler): ArchiveTraveler {
  return {
    id: t.id,
    fullName: t.fullName,
    nickname: t.nickname,
    seat: t.seatPreference,
    meal: t.mealPreference,
    loyalty: [...t.loyaltyRefs],
    phone: t.contactPhone
  };
}

function travelerFromArchive(t: ArchiveTraveler): Traveler {
  return traveler({
    id: t.id,
    fullName: t.fullName,
    nickname: t.nickname,
    seatPreference: (SEAT_PREFERENCES as readonly string[]).includes(t.seat)
      ? (t.seat as Traveler['seatPreference'])
      : 'NONE',
    mealPreference: t.meal,
    loyaltyRefs: [...t.loyalty],
    contactPhone: t.phone
  });
}

const EMPTY_SEGMENT_ROW = {
  carrier: null,
  number: null,
  departureTerminal: null,
  departureGate: null,
  arrivalTerminal: null,
  arrivalGate: null,
  aircraft: null,
  cabin: null,
  seats: {},
  tickets: {},
  operatedBy: null,
  name: null,
  room: null,
  checkInNote: null,
  mode: null,
  provider: null,
  pickup: null,
  category: null,
  tips: null
};

function segmentToArchive(s: Segment): ArchiveSegment {
  const base: ArchiveSegment = {
    id: s.id,
    kind: s.kind,
    start: s.startEpochMillis,
    end: s.endEpochMillis,
    startZone: s.startZoneId,
    endZone: s.endZoneId,
    travelers: [...s.travelerIds],
    reference: s.confirmationCode,
    bookedWith: s.bookedWith,
    note: s.note,
    origin: placeToArchive(s.origin),
    destination: placeToArchive(destinationOf(s)),
    ...EMPTY_SEGMENT_ROW
  };
  switch (s.kind) {
    case 'FLIGHT':
      return {
        ...base,
        carrier: s.carrierCode,
        number: s.flightNumber,
        departureTerminal: s.departureTerminal,
        departureGate: s.departureGate,
        arrivalTerminal: s.arrivalTerminal,
        arrivalGate: s.arrivalGate,
        aircraft: s.aircraft,
        cabin: s.cabin,
        seats: { ...s.seats },
        tickets: { ...s.ticketNumbers },
        operatedBy: s.operatedBy
      };
    case 'LODGING':
      return { ...base, name: s.propertyName, room: s.roomDescription, checkInNote: s.checkInNote };
    case 'GROUND':
      return { ...base, mode: s.mode, provider: s.provider, pickup: s.pickupInstruction };
    case 'EXPERIENCE':
      return { ...base, name: s.name, category: s.category, tips: s.curatedBy };
  }
}

/** Null for a kind this version does not know, rather than a guess at one. */
function segmentFromArchive(row: ArchiveSegment, tripId: string): Segment | null {
  const origin = placeFromArchive(row.origin);
  const destination = row.destination ? placeFromArchive(row.destination) : origin;
  const common = {
    id: row.id,
    tripId,
    startEpochMillis: row.start,
    endEpochMillis: row.end,
    startZoneId: row.startZone,
    endZoneId: row.endZone,
    travelerIds: [...new Set(row.travelers)],
    confirmationCode: row.reference,
    bookedWith: row.bookedWith,
    note: row.note
  };
  switch (row.kind) {
    case 'FLIGHT':
      return flight({
        ...common,
        carrierCode: row.carrier ?? '',
        flightNumber: row.number ?? '',
        origin,
        destination,
        ticketNumbers: { ...row.tickets },
        departureTerminal: row.departureTerminal,
        departureGate: row.departureGate,
        arrivalTerminal: row.arrivalTerminal,
        arrivalGate: row.arrivalGate,
        aircraft: row.aircraft,
        cabin: row.cabin,
        seats: { ...row.seats },
        operatedBy: row.operatedBy
      });
    case 'LODGING':
      return lodging({
        ...common,
        propertyName: row.name ?? origin.name,
        origin,
        roomDescription: row.room,
        checkInNote: row.checkInNote
      });
    case 'GROUND':
      return ground({
        ...common,
        mode: (GROUND_MODES as readonly string[]).includes(row.mode ?? '')
          ? (row.mode as (typeof GROUND_MODES)[number])
          : 'TAXI',
        origin,
        destination,
        provider: row.provider,
        pickupInstruction: row.pickup
      });
    case 'EXPERIENCE':
      return experience({
        ...common,
        name: row.name ?? origin.name,
        category: row.category ?? '',
        origin,
        curatedBy: row.tips
      });
    default:
      return null;
  }
}

function ideaToArchive(i: Idea): ArchiveIdea {
  return {
    id: i.id,
    title: i.title,
    kind: i.kind,
    city: i.city,
    place: i.place ? placeToArchive(i.place) : null,
    note: i.note,
    price: i.priceBand,
    minutes: i.typicalMinutes,
    bestTime: i.bestTime,
    plannedDay: i.plannedDate?.toString() ?? null,
    interested: [...i.interestedTravelerIds],
    status: i.status,
    scheduledSegmentId: i.scheduledSegmentId,
    added: i.addedAtMillis
  };
}

function ideaFromArchive(row: ArchiveIdea, tripId: string): Idea | null {
  if (!(IDEA_KINDS as readonly string[]).includes(row.kind)) return null;
  return idea({
    id: row.id,
    tripId,
    title: row.title,
    kind: row.kind as Idea['kind'],
    city: row.city,
    place: row.place ? placeFromArchive(row.place) : null,
    note: row.note,
    priceBand: (PRICE_BANDS as readonly string[]).includes(row.price ?? '')
      ? (row.price as Idea['priceBand'])
      : null,
    typicalMinutes: row.minutes,
    bestTime: row.bestTime,
    plannedDate: parseDate(row.plannedDay),
    interestedTravelerIds: [...new Set(row.interested)],
    status: (IDEA_STATUSES as readonly string[]).includes(row.status)
      ? (row.status as Idea['status'])
      : 'SAVED',
    scheduledSegmentId: row.scheduledSegmentId,
    addedAtMillis: row.added
  });
}
