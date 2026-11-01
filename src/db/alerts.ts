// The record of reminders raised, which is what stops a booking being
// announced on every check — and what the trip screen's banner reads.

import type { RaisedReminder } from '../domain/model';
import type { WaymarkDb } from './database';

const RETENTION_MILLIS = 30 * 24 * 60 * 60 * 1000;

export class AlertRepository {
  constructor(private readonly db: WaymarkDb) {}

  /** The latest fifty, newest first. */
  async recent(): Promise<RaisedReminder[]> {
    return this.db.alerts.orderBy('raisedAtMillis').reverse().limit(50).toArray();
  }

  /**
   * The stored reminder for [reminder]'s signature, recording it first if
   * this is the first time it has fallen due. A reminder raised while
   * notifications are blocked is still raised — the banner shows it.
   */
  async raise(reminder: RaisedReminder, nowMillis = Date.now()): Promise<RaisedReminder> {
    const { db } = this;
    return db.transaction('rw', db.alerts, async () => {
      const existing = await db.alerts.get(reminder.signature);
      if (existing) return existing;
      const fresh = { ...reminder, posted: false, acknowledged: false };
      await db.alerts.put(fresh);
      await db.alerts
        .where('raisedAtMillis')
        .below(nowMillis - RETENTION_MILLIS)
        .delete();
      return fresh;
    });
  }

  async markPosted(signature: string): Promise<void> {
    await this.db.alerts.update(signature, { posted: true });
  }

  /** Dismissed from the trip screen. */
  async acknowledge(segmentId: string): Promise<void> {
    await this.db.alerts.where('segmentId').equals(segmentId).modify({ acknowledged: true });
  }
}
