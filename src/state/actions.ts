// What the shelf's buttons do, kept out of the components so each is one call.

import { encodeArchive, archiveOf, MIME } from '../share/trip-archive';
import { backupFileName, readTripFile, saveFile } from '../share/files';
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
  const incoming = await readTripFile(file);
  if (incoming.kind === 'problem') {
    ui.open({ name: 'problem', title: incoming.title, detail: incoming.detail });
    return;
  }
  const existing = await trips.existingTripIds(incoming.bundles.map((b) => b.trip.id));
  ui.open({ name: 'import', bundles: incoming.bundles, existing });
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
