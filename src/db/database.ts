// Where trips live: one IndexedDB database, through Dexie, in place of the
// Kotlin app's Room tables.
//
// A row is the domain object itself wherever the domain object is plain data,
// which is almost everywhere: places, bookings, travelers and trips are
// strings, numbers, lists and records. The only exceptions are dates — a
// Temporal object cannot be stored, so a row holds the ISO date and the
// mappers below turn it back. Screens never see a row; the repositories in
// this folder hand out domain objects.

import Dexie, { type EntityTable, type Table } from 'dexie';
import {
  SEGMENT_KINDS,
  type ExchangeRate,
  type Expense,
  type Idea,
  type RaisedReminder,
  type Segment,
  type Traveler,
  type Trip
} from '../domain/model';
import { parseDate } from '../domain/time';

/** A trip's party, in order: the join that Room's trip_members was. */
export interface MemberRow {
  tripId: string;
  travelerId: string;
  position: number;
}

export type IdeaRow = Omit<Idea, 'plannedDate'> & { plannedDate: string | null };
export type ExpenseRow = Omit<Expense, 'spentOn'> & { spentOn: string };
export type RateRow = Omit<ExchangeRate, 'asOf'> & { tripId: string; asOf: string };

/** The currency a trip settles in, once somebody has chosen one. */
export interface CostSettingsRow {
  tripId: string;
  settleCurrency: string;
}

/** One day's weather at one place, cached so the forecast reads offline. */
export interface WeatherRow {
  placeKey: string;
  date: string;
  code: number;
  highC: number;
  lowC: number;
  rainChance: number | null;
  rainMm: number | null;
  basis: 'FORECAST' | 'LAST_YEAR';
  fetchedAtMillis: number;
}

export type WaymarkDb = Dexie & {
  trips: EntityTable<Trip, 'id'>;
  travelers: EntityTable<Traveler, 'id'>;
  members: Table<MemberRow, [string, string]>;
  segments: EntityTable<Segment, 'id'>;
  ideas: EntityTable<IdeaRow, 'id'>;
  expenses: EntityTable<ExpenseRow, 'id'>;
  costSettings: EntityTable<CostSettingsRow, 'tripId'>;
  rates: Table<RateRow, [string, string]>;
  alerts: EntityTable<RaisedReminder, 'signature'>;
  weather: Table<WeatherRow, [string, string]>;
};

/** Every table a trip file carries: what a change to the handover copy watches. */
export const ARCHIVE_TABLES = [
  'trips',
  'travelers',
  'members',
  'segments',
  'ideas',
  'expenses',
  'costSettings',
  'rates'
] as const;

export function openDatabase(name = 'waymark'): WaymarkDb {
  const db = new Dexie(name) as WaymarkDb;
  // v1. Only indexed fields are listed; everything else on a row is stored
  // as it is, so a new field needs no migration — only a new index does.
  db.version(1).stores({
    trips: 'id, startEpochMillis',
    travelers: 'id, fullName',
    members: '[tripId+travelerId], tripId, travelerId',
    segments: 'id, tripId, startEpochMillis',
    ideas: 'id, tripId, scheduledSegmentId',
    expenses: 'id, tripId',
    costSettings: 'tripId',
    rates: '[tripId+currency], tripId',
    alerts: 'signature, segmentId, raisedAtMillis',
    weather: '[placeKey+date], date'
  });
  return db;
}

// — Rows ⇄ domain ————————————————————————————————————————————————————————

const KNOWN_KINDS: ReadonlySet<string> = new Set(SEGMENT_KINDS);

/** A booking of a kind this build does not know is skipped, not a crash. */
export function segmentOf(row: Segment): Segment | null {
  return KNOWN_KINDS.has(row.kind) ? row : null;
}

export function ideaRow(idea: Idea): IdeaRow {
  return { ...idea, plannedDate: idea.plannedDate?.toString() ?? null };
}

export function ideaOf(row: IdeaRow): Idea {
  return { ...row, plannedDate: row.plannedDate === null ? null : parseDate(row.plannedDate) };
}

export function expenseRow(expense: Expense): ExpenseRow {
  return { ...expense, spentOn: expense.spentOn.toString() };
}

/** Null for a row whose date cannot be read: better left out than shown on the wrong day. */
export function expenseOf(row: ExpenseRow): Expense | null {
  const spentOn = parseDate(row.spentOn);
  return spentOn === null ? null : { ...row, spentOn };
}

export function rateRow(tripId: string, rate: ExchangeRate): RateRow {
  return { ...rate, tripId, asOf: rate.asOf.toString() };
}

export function rateOf(row: RateRow): ExchangeRate | null {
  const asOf = parseDate(row.asOf);
  return asOf === null ? null : { currency: row.currency, perUnit: row.perUnit, asOf, manual: row.manual };
}

export const present = <T>(value: T | null): value is T => value !== null;
