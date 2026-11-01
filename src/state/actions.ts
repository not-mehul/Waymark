// What the shelf's buttons do, kept out of the components so each is one call.

import { encodeArchive, archiveOf, MIME } from '../share/trip-archive';
import { systemZone, todayIn, type LocalDate } from '../domain/time';
import { newId } from '../db/trips';
import { readBookingText, toSegment, type BookingReading } from '../share/booking-text';
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
  await showIncoming(file.type.startsWith('image/') ? await readCodePicture(file) : await readTripFile(file));
}

/** Trips from anywhere (a file, a code, a link): shown before anything changes. */
export async function showIncoming(incoming: Incoming): Promise<void> {
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

/**
 * Text shared into the app from another one: a trip code's link, or a
 * confirmation email to read the flights out of.
 */
export async function openSharedText(text: string, today: LocalDate = todayIn(systemZone())): Promise<void> {
  const link = TRIP_CODE_LINK.exec(text);
  if (link) {
    await showIncoming(readTripCode(link[0]));
    return;
  }
  const reading = readBookingText(text, today);
  if (reading.flights.length === 0) {
    ui.open({
      name: 'problem',
      title: 'No flights found in that',
      detail: 'Waymark looks for a flight number with airport codes beside it: “BA 286 SFO to LHR”.'
    });
    return;
  }
  ui.open({ name: 'sharedFlights', reading });
}

const TRIP_CODE_LINK = /waymark:\/\/import\?d=[A-Za-z0-9_-]+/;

/** Saves every complete flight in [reading] to a trip, and opens it. */
export async function addSharedFlights(reading: BookingReading, tripId: string): Promise<number> {
  const flights = reading.flights
    .map((found) => toSegment(found, tripId, newId('seg'), reading.reference))
    .filter((f) => f !== null);
  await trips.saveSegments(flights);
  ui.close();
  ui.go({ name: 'trip', tripId });
  return flights.length;
}
