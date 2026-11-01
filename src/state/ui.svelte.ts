// Where the traveler is: which screen, which dialog over it, and the one-line
// notice the shelf shows after something finishes. Screens read and set this;
// it holds no trip data, which comes live from IndexedDB.

import type { TripBundle } from '../share/trip-archive';
import type { Traveler } from '../domain/model';
import { pushBackHandler } from '../platform/native';

export type Screen = { name: 'shelf' } | { name: 'people' } | { name: 'trip'; tripId: string };

export type Modal =
  | { name: 'newTrip' }
  | { name: 'shelfMenu' }
  | { name: 'about' }
  | { name: 'import'; bundles: TripBundle[]; existing: Set<string> }
  | { name: 'problem'; title: string; detail: string }
  | { name: 'traveler'; traveler: Traveler }
  | { name: 'deleteTrip'; tripId: string };

class Ui {
  screen = $state<Screen>({ name: 'shelf' });
  // Raw: a dialog carries domain objects on their way to IndexedDB, which
  // cannot store the proxies that deep $state would wrap them in.
  modal = $state.raw<Modal | null>(null);
  /** Bumped whenever a dialog replaces another, so the modal can reset its scroll and focus. */
  modalKey = $state(0);
  notice = $state<string | null>(null);

  private releaseBack: (() => void) | null = null;

  /** Off the shelf, Back returns to it. */
  go(screen: Screen): void {
    this.screen = screen;
    this.releaseBack?.();
    this.releaseBack = screen.name === 'shelf' ? null : pushBackHandler(() => this.go({ name: 'shelf' }));
    window.scrollTo({ top: 0 });
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
