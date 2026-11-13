// A trip's before-you-go list: packing, and the things to do before leaving.

import type { ChecklistItem } from '../domain/model';
import type { WaymarkDb } from './database';
import { newId } from './trips';

export class ChecklistRepository {
  constructor(private readonly db: WaymarkDb) {}

  async forTrip(tripId: string): Promise<ChecklistItem[]> {
    return this.db.checklist.where('tripId').equals(tripId).sortBy('position');
  }

  /** Adds lines at the end, skipping any already on the list (case aside). */
  async add(tripId: string, texts: readonly string[]): Promise<void> {
    const { db } = this;
    await db.transaction('rw', db.checklist, async () => {
      const items = await this.forTrip(tripId);
      const have = new Set(items.map((i) => i.text.trim().toLowerCase()));
      let position = items.reduce((max, i) => Math.max(max, i.position), -1);
      const fresh: ChecklistItem[] = [];
      for (const raw of texts) {
        const text = raw.trim();
        if (text === '' || have.has(text.toLowerCase())) continue;
        have.add(text.toLowerCase());
        fresh.push({ id: newId('chk'), tripId, text, done: false, position: ++position });
      }
      await db.checklist.bulkPut(fresh);
    });
  }

  async setDone(id: string, done: boolean): Promise<void> {
    await this.db.checklist.update(id, { done });
  }

  async rename(id: string, text: string): Promise<void> {
    const trimmed = text.trim();
    if (trimmed !== '') await this.db.checklist.update(id, { text: trimmed });
  }

  async remove(id: string): Promise<void> {
    await this.db.checklist.delete(id);
  }

  async restore(item: ChecklistItem): Promise<void> {
    await this.db.checklist.put(item);
  }

  /** Unticks everything, for the next trip that reuses the list. */
  async clearTicks(tripId: string): Promise<void> {
    await this.db.checklist.where('tripId').equals(tripId).modify({ done: false });
  }
}
