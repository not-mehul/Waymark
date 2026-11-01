// Opening and writing trip files: a backup of every trip, or one somebody sent.

import { todayIn, systemZone, type LocalDate } from '../domain/time';
import { callNative, isNativeShell } from '../platform/native';
import { InvalidArchive, bundlesOf, decodeArchive, fromCode, type TripBundle } from './trip-archive';

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

/** What a trip code says: a link the phone's camera hands to Waymark. */
export const TRIP_CODE_PREFIX = 'waymark://import?d=';

/** The text in a scanned trip code, with or without the link around it. */
export function readTripCode(text: string): Incoming {
  const trimmed = text.trim();
  const payload = trimmed.startsWith(TRIP_CODE_PREFIX) ? trimmed.slice(TRIP_CODE_PREFIX.length) : trimmed;
  return readArchive(() => fromCode(payload));
}

export function readTripText(text: string): Incoming {
  return readArchive(() => decodeArchive(text));
}

function readArchive(decode: () => ReturnType<typeof decodeArchive>): Incoming {
  try {
    const bundles = bundlesOf(decode());
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

/** Hands the browser a file to save; in the Android app, the share sheet, where Files is one choice. */
export function saveFile(name: string, text: string, type: string): void {
  if (isNativeShell()) {
    void shareFile(name, text, type, name);
    return;
  }
  const url = URL.createObjectURL(new Blob([text], { type }));
  const link = document.createElement('a');
  link.href = url;
  link.download = name;
  document.body.append(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
}

/**
 * Hand a file to whatever the traveler wants to keep it in: the system share
 * sheet where there is one, a download otherwise. Nothing is uploaded by
 * Waymark itself. Returns how it went, so the caller can say so.
 */
export async function shareFile(
  name: string,
  text: string,
  type: string,
  title: string
): Promise<'shared' | 'saved' | 'cancelled'> {
  if (isNativeShell()) {
    await callNative('shareFile', { name, text, type, title });
    return 'shared';
  }
  const file = new File([text], name, { type });
  if (typeof navigator.canShare === 'function' && navigator.canShare({ files: [file] })) {
    try {
      await navigator.share({ files: [file], title });
      return 'shared';
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') return 'cancelled';
    }
  }
  saveFile(name, text, type);
  return 'saved';
}
