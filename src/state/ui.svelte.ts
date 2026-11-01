// Where the traveler is: which screen, which dialog over it, and the one-line
// notice the shelf shows after something finishes. Screens read and set this;
// it holds no trip data, which comes live from IndexedDB.

import type { TripBundle } from '../share/trip-archive';
import type { Traveler } from '../domain/model';
import { pushBackHandler } from '../platform/native';

export type Screen =
  | { name: 'shelf' }
  | { name: 'people' }
  /** A trip, and optionally one of its bookings open over it. */
  | { name: 'trip'; tripId: string; segmentId?: string; adding?: 'flight'; view?: TripView };

/** The trip's own screens beyond its tabs, reached from its menu. */
export type TripView = 'map' | 'numbers' | 'notes';

/** Where Back goes from a screen: a booking returns to its trip, anything else to the shelf. */
export function parentOf(screen: Screen): Screen | null {
  if (screen.name === 'shelf') return null;
  if (screen.name === 'trip' && (screen.segmentId || screen.adding || screen.view))
    return { name: 'trip', tripId: screen.tripId };
  return { name: 'shelf' };
}

export type Modal =
  | { name: 'newTrip' }
  | { name: 'shelfMenu' }
  | { name: 'about' }
  | { name: 'import'; bundles: TripBundle[]; existing: Set<string> }
  | { name: 'problem'; title: string; detail: string }
  | { name: 'traveler'; traveler: Traveler };

class Ui {
  screen = $state<Screen>({ name: 'shelf' });
  // Raw: a dialog carries domain objects on their way to IndexedDB, which
  // cannot store the proxies that deep $state would wrap them in.
  modal = $state.raw<Modal | null>(null);
  /** Bumped whenever a dialog replaces another, so the modal can reset its scroll and focus. */
  modalKey = $state(0);
  notice = $state<string | null>(null);

  private releaseBack: (() => void) | null = null;

  /** Off the shelf, Back climbs one level. */
  go(screen: Screen): void {
    this.screen = screen;
    this.releaseBack?.();
    const parent = parentOf(screen);
    this.releaseBack = parent ? pushBackHandler(() => this.go(parent)) : null;
    window.scrollTo({ top: 0 });
  }

  back(): void {
    const parent = parentOf(this.screen);
    if (parent) this.go(parent);
  }

  open(modal: Modal): void {
    this.modal = modal;
    this.modalKey++;
  }

  close(): void {
    this.modal = null;
  }

  tell(notice: string): void {
    this.notice = notice;
  }
}

export const ui = new Ui();
