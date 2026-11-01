// The one place that assembles a trip out of its tables. Screens read a
// TripDossier and never see a row.
//
// Every write that touches more than one row happens in a transaction: a
// booking saved without the trip widened around it, or a traveler removed from
// the party but left on their bookings, is a state the traveler would act on.

import {
  copyCommon,
  normaliseParty,
  type Segment,
  type Traveler,
  type Trip,
  type TripCosts,
  type TripDossier
} from '../domain/model';
import type { TripBundle } from '../share/trip-archive';
import {
  expenseOf,
  expenseRow,
  ideaOf,
  ideaRow,
  present,
  rateOf,
  rateRow,
  segmentOf,
  type WaymarkDb
} from './database';

const DAY_MILLIS = 24 * 60 * 60 * 1000;

/** Full 128-bit ids: rows are put, not inserted, so a collision would overwrite silently. */
export function newId(prefix: string): string {
  return `${prefix}-${crypto.randomUUID()}`;
}

const byStart = (a: Segment, b: Segment) => a.startEpochMillis - b.startEpochMillis;

export class TripRepository {
  constructor(private readonly db: WaymarkDb) {}

  /** Every trip, latest first, as the shelf lists them before grouping. */
  async trips(): Promise<Trip[]> {
    return (await this.db.trips.toArray()).sort((a, b) => b.startEpochMillis - a.startEpochMillis);
  }

  async trip(tripId: string): Promise<Trip | null> {
    return (await this.db.trips.get(tripId)) ?? null;
  }

  /** The party of a trip, in its own order. */
  async party(tripId: string): Promise<Traveler[]> {
    const members = (await this.db.members.where('tripId').equals(tripId).toArray()).sort(
      (a, b) => a.position - b.position
    );
    const people = await this.db.travelers.bulkGet(members.map((m) => m.travelerId));
    return people.filter((t): t is Traveler => t !== undefined);
  }

  async segments(tripId: string): Promise<Segment[]> {
    const rows = await this.db.segments.where('tripId').equals(tripId).toArray();
    return rows.map(segmentOf).filter(present).sort(byStart);
  }

  /** Everything one trip screen needs. */
  async dossier(tripId: string): Promise<TripDossier | null> {
    return this.db.transaction(
      'r',
      this.db.trips,
      this.db.members,
      this.db.travelers,
      this.db.segments,
      async () => {
        const trip = await this.trip(tripId);
        if (!trip) return null;
        return {
          trip,
          party: { tripId, travelers: await this.party(tripId) },
          segments: await this.segments(tripId)
        };
      }
    );
  }

  async saveTrip(trip: Trip): Promise<void> {
    await this.db.trips.put(trip);
  }

  /** Off the shelf and back again; nothing on the trip changes. */
  async setArchived(tripId: string, archived: boolean): Promise<void> {
    await this.db.trips.update(tripId, { archived });
  }

  /**
   * Remove a trip and everything hanging off it: its bookings, ideas, costs,
   * the reminders raised for its bookings, and profiles of people who were on
   * this trip and no other.
   */
  async deleteTrip(tripId: string): Promise<void> {
    const { db } = this;
    await db.transaction('rw', [...this.everyTable()], async () => {
      await db.trips.delete(tripId);
      await db.members.where('tripId').equals(tripId).delete();
      await db.segments.where('tripId').equals(tripId).delete();
      await db.ideas.where('tripId').equals(tripId).delete();
      await db.expenses.where('tripId').equals(tripId).delete();
      await db.costSettings.delete(tripId);
      await db.rates.where('tripId').equals(tripId).delete();
      await this.pruneAlerts();
      await this.pruneTravelers();
    });
  }

  async createTrip(
    name: string,
    destinationSummary: string,
    startEpochMillis: number,
    endEpochMillis: number,
    homeZoneId: string
  ): Promise<string> {
    const trip: Trip = {
      id: newId('trip'),
      name,
      destinationSummary,
      startEpochMillis,
      endEpochMillis,
      homeZoneId,
      archived: false
    };
    await this.db.trips.put(trip);
    return trip.id;
  }

  saveSegment(segment: Segment): Promise<void> {
    return this.saveSegments([segment]);
  }

  /**
   * Write bookings, in the stored form of their party, and widen each trip
   * so its dates never exclude something booked inside it.
   */
  async saveSegments(items: readonly Segment[]): Promise<void> {
    if (items.length === 0) return;
    const { db } = this;
    await db.transaction('rw', db.trips, db.members, db.segments, async () => {
      const parties = new Map<string, string[]>();
      for (const tripId of new Set(items.map((s) => s.tripId)))
        parties.set(tripId, await this.memberIds(tripId));
      const normalised = items.map((s) =>
        copyCommon(s, { travelerIds: normaliseParty(s.travelerIds, parties.get(s.tripId)!) })
      );
      await db.segments.bulkPut(normalised);
      for (const [tripId] of parties)
        await this.widenTripToFit(
          tripId,
          normalised.filter((s) => s.tripId === tripId)
        );
    });
  }

  /** Remove one booking, and everything that only existed because of it. */
  async deleteSegment(segmentId: string): Promise<void> {
    const { db } = this;
    await db.transaction('rw', db.segments, db.ideas, db.alerts, async () => {
      // An idea promoted onto the timeline goes back to the list rather than
      // vanishing with the segment.
      await this.releaseSegment(segmentId);
      await db.alerts.where('segmentId').equals(segmentId).modify({ acknowledged: true });
      await db.segments.delete(segmentId);
    });
  }

  async addTraveler(tripId: string, traveler: Traveler): Promise<void> {
    const { db } = this;
    await db.transaction('rw', db.travelers, db.members, async () => {
      await db.travelers.put(traveler);
      await this.addMember(tripId, traveler.id);
    });
  }

  /** Someone already travelling with you, brought onto another trip. */
  async addExistingTraveler(tripId: string, travelerId: string): Promise<void> {
    const { db } = this;
    await db.transaction('rw', db.travelers, db.members, async () => {
      if (!(await db.travelers.get(travelerId))) return;
      await this.addMember(tripId, travelerId);
    });
  }

  /** A profile edit: preferences, loyalty numbers, a phone number. Every trip sees it. */
  async saveTraveler(traveler: Traveler): Promise<void> {
    await this.db.travelers.put(traveler);
  }

  /** Everyone you travel with, by name. */
  async allTravelers(): Promise<Traveler[]> {
    return this.db.travelers.orderBy('fullName').toArray();
  }

  /** Traveler id to the number of trips they are on. */
  async tripCounts(): Promise<Map<string, number>> {
    const counts = new Map<string, number>();
    await this.db.members.each((m) => counts.set(m.travelerId, (counts.get(m.travelerId) ?? 0) + 1));
    return counts;
  }

  /**
   * Take someone off a trip, cleanly. Bookings only they were on go with
   * them; bookings shared with others lose their name; ideas forget their
   * interest; and a profile on no other trip is removed.
   */
  async removeTraveler(tripId: string, travelerId: string): Promise<void> {
    const { db } = this;
    await db.transaction('rw', [...this.everyTable()], async () => {
      const remaining = (await this.memberIds(tripId)).filter((id) => id !== travelerId);
      for (const segment of await this.segments(tripId)) {
        const ids = segment.travelerIds;
        if (ids.length === 0 || !ids.includes(travelerId)) continue;
        if (ids.length === 1) {
          await this.releaseSegment(segment.id);
          await db.segments.delete(segment.id);
        } else {
          const left = ids.filter((id) => id !== travelerId);
          await db.segments.put(copyCommon(segment, { travelerIds: normaliseParty(left, remaining) }));
        }
      }
      await db.ideas
        .where('tripId')
        .equals(tripId)
        .modify((idea) => {
          idea.interestedTravelerIds = idea.interestedTravelerIds.filter((id) => id !== travelerId);
        });
      await db.members.delete([tripId, travelerId]);
      if ((await db.members.where('travelerId').equals(travelerId).count()) === 0) {
        await db.travelers.delete(travelerId);
      }
      await this.pruneAlerts();
    });
  }

  /**
   * Pull a trip's dates in to exactly the span of its bookings — offered on
   * the edit sheet, because a trip only ever widens on its own.
   */
  async fitTripToBookings(tripId: string): Promise<void> {
    const { db } = this;
    await db.transaction('rw', db.trips, db.segments, async () => {
      const trip = await db.trips.get(tripId);
      const rows = await db.segments.where('tripId').equals(tripId).toArray();
      if (!trip || rows.length === 0) return;
      await db.trips.put({
        ...trip,
        startEpochMillis: Math.min(...rows.map((r) => r.startEpochMillis)),
        endEpochMillis: Math.max(...rows.map((r) => r.endEpochMillis))
      });
    });
  }

  /**
   * Housekeeping at start-up: reminders whose booking has gone, profiles on
   * no trip, and booking parties naming people no longer on the trip.
   */
  async repair(): Promise<void> {
    const { db } = this;
    await db.transaction('rw', db.alerts, db.travelers, db.members, db.segments, async () => {
      await this.pruneAlerts();
      await this.pruneTravelers();
      const members = new Map<string, string[]>();
      await db.members.each((m) => members.set(m.tripId, [...(members.get(m.tripId) ?? []), m.travelerId]));
      for (const row of await db.segments.toArray()) {
        const segment = segmentOf(row);
        if (!segment) continue;
        const party = members.get(segment.tripId) ?? [];
        const ids = segment.travelerIds;
        // A booking whose people have all left is left alone: making it
        // everyone's would put it on travelers who were never on it.
        if (ids.length === 0 || !ids.some((id) => party.includes(id))) continue;
        const stored = normaliseParty(ids, party);
        if (stored.join('\u0000') !== ids.join('\u0000')) {
          await db.segments.put(copyCommon(segment, { travelerIds: stored }));
        }
      }
    });
  }

  /**
   * Every booking of every trip with something still to come — what
   * reminders are worked out from. Whole trips, because the time to leave
   * for an airport depends on where the trip is before the flight. Archived
   * trips are put away and stay quiet.
   */
  async reminderItinerary(nowMillis: number): Promise<Segment[]> {
    const { db } = this;
    return db.transaction('r', db.trips, db.segments, async () => {
      const soon = await db.segments
        .where('startEpochMillis')
        .above(nowMillis - DAY_MILLIS)
        .toArray();
      const out: Segment[] = [];
      for (const tripId of new Set(soon.map((s) => s.tripId))) {
        if ((await db.trips.get(tripId))?.archived !== false) continue;
        out.push(...(await this.segments(tripId)));
      }
      return out;
    });
  }

  /** What is happening next across every trip, soonest first, for the home-screen widget. */
  async nextAcrossTrips(nowMillis: number, limit = 1): Promise<[Trip, Segment][]> {
    const { db } = this;
    return db.transaction('r', db.trips, db.segments, async () => {
      const trips = new Map((await db.trips.toArray()).filter((t) => !t.archived).map((t) => [t.id, t]));
      const out: [Trip, Segment][] = [];
      await db.segments
        .orderBy('startEpochMillis')
        .until(() => out.length >= limit)
        .each((row) => {
          const trip = trips.get(row.tripId);
          const segment = segmentOf(row);
          if (trip && segment && segment.endEpochMillis >= nowMillis) out.push([trip, segment]);
        });
      return out;
    });
  }

  /** Whole trips as the archive format carries them: every trip, or the ones named. */
  async bundles(tripIds: readonly string[] | null = null): Promise<TripBundle[]> {
    const { db } = this;
    return db.transaction('r', [...this.everyTable()], async () => {
      const trips = (await db.trips.orderBy('startEpochMillis').toArray()).filter(
        (t) => tripIds === null || tripIds.includes(t.id)
      );
      const out: TripBundle[] = [];
      for (const trip of trips) {
        out.push({
          trip,
          travelers: await this.party(trip.id),
          segments: await this.segments(trip.id),
          ideas: (await db.ideas.where('tripId').equals(trip.id).sortBy('addedAtMillis')).map(ideaOf),
          costs: await this.costs(trip.id)
        });
      }
      return out;
    });
  }

  /** Which of these trips are already on this device — an import replaces them. */
  async existingTripIds(tripIds: readonly string[]): Promise<Set<string>> {
    const found = await this.db.trips.bulkGet([...tripIds]);
    return new Set(found.filter((t): t is Trip => t !== undefined).map((t) => t.id));
  }

  /**
   * Write trips from a backup or a shared trip file, all or nothing.
   *
   * A trip is matched by its id, so opening the same file twice replaces it
   * rather than leaving two. Its party, bookings and ideas are replaced
   * wholesale — a booking the sender removed must not survive here.
   * Travelers are matched by id too, so someone on several shared trips
   * stays one person.
   */
  async importBundles(bundles: readonly TripBundle[]): Promise<void> {
    if (bundles.length === 0) return;
    const { db } = this;
    await db.transaction('rw', [...this.everyTable()], async () => {
      for (const bundle of bundles) {
        const tripId = bundle.trip.id;
        await db.trips.put(bundle.trip);
        await db.members.where('tripId').equals(tripId).delete();
        await db.travelers.bulkPut(bundle.travelers);
        await db.members.bulkPut(
          bundle.travelers.map((t, position) => ({ tripId, travelerId: t.id, position }))
        );
        const party = bundle.travelers.map((t) => t.id);
        await db.segments.where('tripId').equals(tripId).delete();
        await db.segments.bulkPut(
          bundle.segments.map((s) =>
            copyCommon(s, { tripId, travelerIds: normaliseParty(s.travelerIds, party) })
          )
        );
        await db.ideas.where('tripId').equals(tripId).delete();
        await db.ideas.bulkPut(bundle.ideas.map((i) => ideaRow({ ...i, tripId })));
        // A file from a Waymark that did not know about costs says nothing
        // about them, which is not the same as "there are none".
        if (bundle.costs) await this.replaceCosts(tripId, bundle.costs);
      }
      await this.pruneAlerts();
      await this.pruneTravelers();
    });
  }

  // — Internals ———————————————————————————————————————————————————————————

  private everyTable() {
    const { db } = this;
    return [
      db.trips,
      db.travelers,
      db.members,
      db.segments,
      db.ideas,
      db.expenses,
      db.costSettings,
      db.rates,
      db.alerts
    ];
  }

  private async memberIds(tripId: string): Promise<string[]> {
    return (await this.db.members.where('tripId').equals(tripId).sortBy('position')).map((m) => m.travelerId);
  }

  private async addMember(tripId: string, travelerId: string): Promise<void> {
    const { db } = this;
    if (await db.members.get([tripId, travelerId])) return;
    const position = await db.members.where('tripId').equals(tripId).count();
    await db.members.put({ tripId, travelerId, position });
  }

  private async costs(tripId: string): Promise<TripCosts> {
    const { db } = this;
    const expenses = (await db.expenses.where('tripId').equals(tripId).toArray())
      .map(expenseOf)
      .filter(present)
      .sort(
        (a, b) =>
          a.spentOn.toString().localeCompare(b.spentOn.toString()) || a.addedAtMillis - b.addedAtMillis
      );
    const rates = (await db.rates.where('tripId').equals(tripId).toArray())
      .map(rateOf)
      .filter(present)
      .sort((a, b) => (a.currency < b.currency ? -1 : a.currency > b.currency ? 1 : 0));
    return { settleCurrency: (await db.costSettings.get(tripId))?.settleCurrency ?? null, expenses, rates };
  }

  private async replaceCosts(tripId: string, incoming: TripCosts): Promise<void> {
    const { db } = this;
    await db.expenses.where('tripId').equals(tripId).delete();
    await db.expenses.bulkPut(incoming.expenses.map((e) => expenseRow({ ...e, tripId })));
    if (incoming.settleCurrency)
      await db.costSettings.put({ tripId, settleCurrency: incoming.settleCurrency });
    await db.rates.where('tripId').equals(tripId).delete();
    await db.rates.bulkPut(incoming.rates.map((r) => rateRow(tripId, r)));
  }

  /** An idea on the timeline returns to the list when its booking goes. */
  private async releaseSegment(segmentId: string): Promise<void> {
    await this.db.ideas
      .where('scheduledSegmentId')
      .equals(segmentId)
      .modify({ status: 'SAVED', scheduledSegmentId: null });
  }

  /** Raised reminders whose booking has gone. */
  private async pruneAlerts(): Promise<void> {
    const { db } = this;
    const alerts = await db.alerts.toArray();
    if (alerts.length === 0) return;
    const found = await db.segments.bulkGet(alerts.map((a) => a.segmentId));
    const orphans = alerts.filter((_, i) => found[i] === undefined).map((a) => a.signature);
    await db.alerts.bulkDelete(orphans);
  }

  /** Profiles on no trip at all. */
  private async pruneTravelers(): Promise<void> {
    const { db } = this;
    const onATrip = new Set<string>();
    await db.members.each((m) => onATrip.add(m.travelerId));
    const orphans = (await db.travelers.toCollection().primaryKeys()).filter((id) => !onATrip.has(id));
    await db.travelers.bulkDelete(orphans);
  }

  /** A trip's dates should never exclude something booked inside it. */
  private async widenTripToFit(tripId: string, saved: readonly Segment[]): Promise<void> {
    const trip = await this.db.trips.get(tripId);
    if (!trip || saved.length === 0) return;
    const start = Math.min(trip.startEpochMillis, ...saved.map((s) => s.startEpochMillis));
    const end = Math.max(trip.endEpochMillis, ...saved.map((s) => s.endEpochMillis));
    if (start !== trip.startEpochMillis || end !== trip.endEpochMillis) {
      await this.db.trips.put({ ...trip, startEpochMillis: start, endEpochMillis: end });
    }
  }
}
