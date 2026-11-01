// The move from the Kotlin app. Its Room database cannot be opened from a web
// page, so its last release keeps a full backup in private storage, rewritten
// after every change (HandoverSnapshot.kt). On the first launch of this app
// the native shell hands that file over; it is imported, the counts are
// checked against the file, and only then is the file deleted.

import type { TripRepository } from '../db/trips';
import { InvalidArchive, bundlesOf, decodeArchive } from './trip-archive';

/** Where the handover file comes from: the native shell, or nowhere in a browser. */
export interface HandoverSource {
  /** The file's text, or null when there is none. */
  read(): Promise<string | null>;
  /** Called only once every trip in it is safely in IndexedDB. */
  remove(): Promise<void>;
}

export type HandoverResult =
  /** No file: a fresh install, a browser, or a handover already done. */
  | { outcome: 'none' }
  | { outcome: 'imported'; trips: number; bookings: number }
  /** The file is kept, so nothing is lost and the next launch tries again. */
  | { outcome: 'failed'; reason: string };

export async function runHandover(source: HandoverSource, trips: TripRepository): Promise<HandoverResult> {
  const text = await source.read();
  if (text === null) return { outcome: 'none' };
  let bundles;
  try {
    bundles = bundlesOf(decodeArchive(text));
  } catch (error) {
    return { outcome: 'failed', reason: error instanceof InvalidArchive ? error.message : String(error) };
  }
  await trips.importBundles(bundles);

  // Every trip and every booking in the file has to be readable back before
  // the file goes: it is the only other copy.
  const stored = await trips.bundles(bundles.map((b) => b.trip.id));
  const expected = new Map(bundles.map((b) => [b.trip.id, b.segments.length]));
  const missing = [...expected].filter(
    ([id, count]) => stored.find((b) => b.trip.id === id)?.segments.length !== count
  );
  if (missing.length > 0) {
    return {
      outcome: 'failed',
      reason: `${missing.length} of ${bundles.length} trips did not read back whole.`
    };
  }
  await source.remove();
  return {
    outcome: 'imported',
    trips: bundles.length,
    bookings: [...expected.values()].reduce((a, b) => a + b, 0)
  };
}

/** No native shell: nothing to hand over. */
export const NO_HANDOVER: HandoverSource = {
  read: () => Promise.resolve(null),
  remove: () => Promise.resolve()
};
