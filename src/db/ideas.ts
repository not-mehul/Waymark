// The list of things a trip wants to do, and the bridge from that list onto
// the timeline.

import { scheduleIdea } from '../domain/ideas';
import {
  copyCommon,
  normaliseParty,
  type Idea,
  type IdeaKind,
  type IdeaStatus,
  type Place,
  type PriceBand
} from '../domain/model';
import type { LocalDate, LocalTime } from '../domain/time';
import { ideaOf, ideaRow, type WaymarkDb } from './database';
import { newId } from './trips';

export interface NewIdea {
  title: string;
  kind: IdeaKind;
  city: string;
  note?: string | null;
  place?: Place | null;
  typicalMinutes?: number | null;
  interestedTravelerIds?: string[];
  priceBand?: PriceBand | null;
  bestTime?: string | null;
}

const blankToNull = (text: string | null | undefined) => {
  const trimmed = text?.trim() ?? '';
  return trimmed === '' ? null : trimmed;
};

export class IdeaRepository {
  constructor(private readonly db: WaymarkDb) {}

  /** A trip's ideas, in the order they were added. */
  async forTrip(tripId: string): Promise<Idea[]> {
    return (await this.db.ideas.where('tripId').equals(tripId).sortBy('addedAtMillis')).map(ideaOf);
  }

  async find(ideaId: string): Promise<Idea | null> {
    const row = await this.db.ideas.get(ideaId);
    return row ? ideaOf(row) : null;
  }

  async save(idea: Idea): Promise<void> {
    await this.db.ideas.put(ideaRow(idea));
  }

  async saveAll(items: readonly Idea[]): Promise<void> {
    await this.db.ideas.bulkPut(items.map(ideaRow));
  }

  /** Returns what was removed, so the screen can offer it back. */
  async delete(ideaId: string): Promise<Idea | null> {
    const existing = await this.find(ideaId);
    if (!existing) return null;
    await this.db.ideas.delete(ideaId);
    return existing;
  }

  /** Add something the traveler typed rather than something the guide offered. */
  async add(tripId: string, fields: NewIdea, nowMillis = Date.now()): Promise<Idea> {
    const idea: Idea = {
      id: newId('idea'),
      tripId,
      title: fields.title.trim(),
      kind: fields.kind,
      city: fields.city.trim(),
      place: fields.place ?? null,
      note: blankToNull(fields.note),
      priceBand: fields.priceBand ?? null,
      typicalMinutes: fields.typicalMinutes ?? null,
      bestTime: blankToNull(fields.bestTime),
      plannedDate: null,
      interestedTravelerIds: fields.interestedTravelerIds ?? [],
      status: 'SAVED',
      scheduledSegmentId: null,
      addedAtMillis: nowMillis
    };
    await this.save(idea);
    return idea;
  }

  /**
   * Done, not yet, or set aside. An idea on the timeline stays tied to its
   * booking through all of it: "not yet" puts it back on the timeline where
   * it still is, rather than on the list as though it had never been scheduled.
   */
  async setStatus(ideaId: string, status: IdeaStatus): Promise<void> {
    const { db } = this;
    await db.transaction('rw', db.ideas, db.segments, async () => {
      const existing = await this.find(ideaId);
      if (!existing) return;
      const linked =
        existing.scheduledSegmentId !== null && (await db.segments.get(existing.scheduledSegmentId))
          ? existing.scheduledSegmentId
          : null;
      const next = status === 'SAVED' && linked !== null ? 'SCHEDULED' : status;
      await this.save({ ...existing, status: next, scheduledSegmentId: linked });
    });
  }

  /** Pencil an idea in for a day, or rub the day out. */
  async setPlannedDay(ideaId: string, date: LocalDate | null): Promise<void> {
    await this.db.ideas.update(ideaId, { plannedDate: date?.toString() ?? null });
  }

  async toggleInterest(ideaId: string, travelerId: string): Promise<void> {
    const existing = await this.find(ideaId);
    if (!existing) return;
    const ids = existing.interestedTravelerIds;
    const next = ids.includes(travelerId) ? ids.filter((id) => id !== travelerId) : [...ids, travelerId];
    await this.save({ ...existing, interestedTravelerIds: next });
  }

  /**
   * Promote an idea onto the timeline. The segment and the idea's new state
   * are written together: an idea marked scheduled with no segment behind it
   * would be a lie the traveler acts on.
   */
  async schedule(
    idea: Idea,
    date: LocalDate,
    startTime: LocalTime,
    zoneId: string,
    travelerIds: readonly string[],
    minutesOverride: number | null = null
  ): Promise<string> {
    const segment = scheduleIdea(idea, newId('seg'), date, startTime, zoneId, travelerIds, minutesOverride);
    const { db } = this;
    await db.transaction('rw', db.members, db.segments, db.ideas, async () => {
      const party = (await db.members.where('tripId').equals(idea.tripId).toArray()).map((m) => m.travelerId);
      await db.segments.put(copyCommon(segment, { travelerIds: normaliseParty(segment.travelerIds, party) }));
      await this.save({ ...idea, status: 'SCHEDULED', scheduledSegmentId: segment.id });
    });
    return segment.id;
  }
}
