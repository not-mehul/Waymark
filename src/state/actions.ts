// What the shelf's buttons do, kept out of the components so each is one call.

import { encodeArchive, archiveOf, MIME } from '../share/trip-archive';
import { backupFileName, readTripCode, readTripFile, saveFile, type Incoming } from '../share/files';
import { readCode } from '../share/read-code';
import { seedSample } from '../db/seed';
import type { TripBundle } from '../share/trip-archive';
import { trips } from './app';
import { ui } from './ui.svelte';

/** Every trip, in one file the traveler keeps. */
export async function backUpEveryTrip(): Promise<void> {
  try {
    const bundles = await trips.bundles();
    saveFile(backupFileName(), encodeArchive(archiveOf(bundles)), MIME);
    ui.tell(bundles.length === 1 ? 'One trip backed up.' : `${bundles.length} trips backed up.`);
  } catch (error) {
    console.error('Backup failed', error);
    ui.tell('The backup could not be written there.');
  }
}

/** A backup or a shared trip, picked from the device: shown before anything changes. */
export async function openTripFile(file: File): Promise<void> {
  const incoming = file.type.startsWith('image/') ? await readCodePicture(file) : await readTripFile(file);
  if (incoming.kind === 'problem') {
    ui.open({ name: 'problem', title: incoming.title, detail: incoming.detail });
    return;
  }
  const existing = await trips.existingTripIds(incoming.bundles.map((b) => b.trip.id));
  ui.open({ name: 'import', bundles: incoming.bundles, existing });
}

/** A picture of a trip code: a screenshot of one, or a photo of a printout. */
async function readCodePicture(file: File): Promise<Incoming> {
  const code = await readCode(file);
  if (code.kind === 'unsupported') {
    return {
      kind: 'problem',
      title: 'This browser cannot read the code',
      detail: 'Scan it with the phone’s camera instead, or ask for the trip as a file.'
    };
  }
  if (code.kind === 'none') {
    return {
      kind: 'problem',
      title: 'No trip code in that picture',
      detail: 'Try a sharper picture, with the whole code in it.'
    };
  }
  return readTripCode(code.text);
}

/**
 * Writes the trips in. One trip opens, as in the Kotlin app; a whole backup
 * leaves the traveler on the shelf, where every trip in it now is.
 */
export async function importTrips(bundles: TripBundle[]): Promise<void> {
  await trips.importBundles(bundles);
  ui.close();
  if (bundles.length === 1) ui.go({ name: 'trip', tripId: bundles[0]!.trip.id });
  else ui.tell(`${bundles.length} trips opened.`);
}

export async function loadExample(): Promise<void> {
  ui.go({ name: 'trip', tripId: await seedSample(trips) });
}
