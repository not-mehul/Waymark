// Opening and writing trip files: a backup of every trip, or one somebody sent.

import { todayIn, systemZone, type LocalDate } from '../domain/time';
import { InvalidArchive, bundlesOf, decodeArchive, type TripBundle } from './trip-archive';

/** Larger than any real archive by far; a file this size is something else. */
export const MAX_FILE_BYTES = 16 * 1024 * 1024;

export type Incoming =
  { kind: 'trips'; bundles: TripBundle[] } | { kind: 'problem'; title: string; detail: string };

/** What is in a file the traveler picked: its trips, or why there are none. */
export async function readTripFile(file: Pick<Blob, 'size' | 'text'>): Promise<Incoming> {
  let text: string;
  try {
    if (file.size > MAX_FILE_BYTES) throw new Error('Too large');
    text = await file.text();
  } catch {
    return {
      kind: 'problem',
      title: 'That file could not be opened',
      detail: 'It may be too large, or no longer there.'
    };
  }
  return readTripText(text);
}

export function readTripText(text: string): Incoming {
  try {
    const bundles = bundlesOf(decodeArchive(text));
    if (bundles.length === 0) {
      return { kind: 'problem', title: 'Nothing to open', detail: 'That file has no trips in it.' };
    }
    return { kind: 'trips', bundles };
  } catch (error) {
    if (!(error instanceof InvalidArchive)) throw error;
    return { kind: 'problem', title: 'That is not a trip Waymark can open', detail: error.message };
  }
}

/** "waymark-backup-2026-11-01.json". */
export function backupFileName(today: LocalDate = todayIn(systemZone())): string {
  return `waymark-backup-${today.toString()}.json`;
}

/** Hands the browser a file to save. The Android shell's share sheet replaces this in phase 5. */
export function saveFile(name: string, text: string, type: string): void {
  const url = URL.createObjectURL(new Blob([text], { type }));
  const link = document.createElement('a');
  link.href = url;
  link.download = name;
  document.body.append(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
}
