// The page's side of the Android shell: what it hands over (the reminders to
// post, the journey to draw, the widget's bookings) and what comes in from it
// (a reminder or the widget tapped, an email or a trip shared in, the earlier
// app's trips on the first launch). In a browser none of this runs.

import { liveQuery } from 'dexie';
import { callNative, isNativeShell, onNativeEvent } from '../platform/native';
import type { HandoverSource } from '../share/handover';
import { readTripCode, readTripText } from '../share/files';
import { openSharedText, showIncoming } from './actions';
import { settings, trips } from './app';
import { shellSchedule, WIDGET_ENTRIES, widgetEntries } from './shell-data';
import { ui } from './ui.svelte';

/** The earlier app's last copy of every trip, kept in its private storage (HandoverSnapshot.kt). */
export const nativeHandover: HandoverSource = {
  read: () => callNative<string | null>('readHandover'),
  remove: () => callNative('removeHandover').then(() => undefined),
  stranded: () => callNative<boolean>('handoverStranded')
};

/** Writes settle for this long before the shell hears about them: an import is many writes. */
const SETTLE_MILLIS = 1_000;

/** Keeps the shell's reminders, journey and widget in step with the trips. */
export function startShellSync(): void {
  if (!isNativeShell()) return;
  let timer: ReturnType<typeof setTimeout> | null = null;
  const soon = () => {
    if (timer) clearTimeout(timer);
    timer = setTimeout(() => void push(), SETTLE_MILLIS);
  };

  // Any change to a trip or a booking; the value itself is read fresh in push().
  liveQuery(() => trips.reminderItinerary(Date.now())).subscribe({
    next: soon,
    error: (err: unknown) => console.error('Watching trips for the shell', err)
  });
  $effect.root(() => {
    $effect(() => {
      void settings.reminders;
      soon();
    });
  });
  // Leaving the app is when the shell next has to manage alone.
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') void push();
  });
}

async function push(): Promise<void> {
  const now = Date.now();
  try {
    const [itinerary, next] = await Promise.all([
      trips.reminderItinerary(now),
      trips.nextAcrossTrips(now, WIDGET_ENTRIES)
    ]);
    await callNative('schedule', { ...shellSchedule(itinerary, settings.reminders, now) });
    await callNative('updateWidget', { entries: widgetEntries(next) });
  } catch (err) {
    console.error('Could not update the shell', err);
  }
}

/**
 * Asks once for permission to post reminders, the first time a booking is
 * saved with reminders on: never on the way in, before there is anything to
 * remind about. Turned down, they can be allowed from system settings.
 */
export async function askForReminders(): Promise<void> {
  if (!isNativeShell() || !settings.reminders.anyOn || asked()) return;
  try {
    localStorage.setItem(ASKED, '1');
  } catch {
    // Private storage unavailable: the system's own limit on asking still holds.
  }
  try {
    await callNative('requestNotifications');
  } catch (err) {
    console.warn('Notification permission', err);
  }
}

const ASKED = 'waymark:asked-notifications';
function asked(): boolean {
  try {
    return localStorage.getItem(ASKED) !== null;
  } catch {
    return false;
  }
}

/** Requests from outside the page. The shell keeps each until this subscribes. */
export function listenToShell(): void {
  onNativeEvent('openBooking', (data: { tripId?: unknown; segmentId?: unknown }) => {
    if (typeof data.tripId !== 'string') return;
    ui.close();
    ui.go({
      name: 'trip',
      tripId: data.tripId,
      segmentId: typeof data.segmentId === 'string' ? data.segmentId : undefined
    });
  });
  onNativeEvent('sharedText', (data: { text?: unknown }) => {
    if (typeof data.text === 'string') void openSharedText(data.text);
  });
  onNativeEvent('openFile', (data: { text?: unknown; problem?: unknown }) => {
    if (typeof data.text === 'string') void showIncoming(readTripText(data.text));
    else
      ui.open({
        name: 'problem',
        title: 'That file could not be opened',
        detail: typeof data.problem === 'string' ? data.problem : 'It may be too large, or no longer there.'
      });
  });
  onNativeEvent('openCode', (data: { code?: unknown }) => {
    if (typeof data.code === 'string') void showIncoming(readTripCode(data.code));
  });
}
