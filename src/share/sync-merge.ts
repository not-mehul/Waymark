// Merging this phone's trips with the copy in a sync folder, trip by trip.
//
// Waymark keeps no edit times, so the merge is three-way against what both
// sides looked like at the last sync (a fingerprint of each trip, kept on
// the phone). A trip changed on one side only takes that side. A trip
// deleted on one side and untouched on the other stays deleted. A trip
// changed on both phones since they last met keeps this phone's copy, and
// the merge says so.

import { archiveOf, encodeArchive, type TripBundle } from './trip-archive';

/** Trip id → fingerprint, as both sides were after the last sync. */
export type SyncBase = Record<string, string>;

export interface SyncMerge {
  /** Trips to write into this phone: new or changed in the folder. */
  incoming: TripBundle[];
  /** Trip ids to delete from this phone: deleted in the folder and untouched here. */
  removed: string[];
  /** Every trip, as the folder should now hold it. */
  merged: TripBundle[];
  /** Trips changed on both sides, where this phone's copy was kept. */
  conflicts: string[];
  base: SyncBase;
}

/** FNV-1a over the trip as a file carries it, so a field nobody saved changes nothing. */
export function fingerprint(bundle: TripBundle): string {
  const text = encodeArchive(archiveOf([bundle], 0));
  let hash = 0x811c9dc5;
  for (let i = 0; i < text.length; i++) {
    hash ^= text.charCodeAt(i);
    hash = Math.imul(hash, 0x01000193) >>> 0;
  }
  return `${hash.toString(16)}:${text.length}`;
}

export function mergeTrips(
  local: readonly TripBundle[],
  remote: readonly TripBundle[],
  base: SyncBase
): SyncMerge {
  const mine = new Map(local.map((b) => [b.trip.id, b]));
  const theirs = new Map(remote.map((b) => [b.trip.id, b]));
  const ids = [...new Set([...mine.keys(), ...theirs.keys()])];
  const incoming: TripBundle[] = [];
  const removed: string[] = [];
  const merged: TripBundle[] = [];
  const conflicts: string[] = [];

  for (const id of ids) {
    const l = mine.get(id);
    const r = theirs.get(id);
    const was = base[id];
    if (l && r) {
      const fl = fingerprint(l);
      const fr = fingerprint(r);
      if (fl === fr || fr === was) merged.push(l);
      else if (fl === was) {
        incoming.push(r);
        merged.push(r);
      } else {
        conflicts.push(id);
        merged.push(l);
      }
    } else if (l) {
      // Only here: new on this phone, or deleted in the folder.
      if (was !== undefined && fingerprint(l) === was) removed.push(id);
      else merged.push(l);
    } else if (r) {
      // Only in the folder: new there, or deleted here.
      if (was !== undefined && fingerprint(r) === was) continue;
      incoming.push(r);
      merged.push(r);
    }
  }

  return {
    incoming,
    removed,
    merged,
    conflicts,
    base: Object.fromEntries(merged.map((b) => [b.trip.id, fingerprint(b)]))
  };
}
