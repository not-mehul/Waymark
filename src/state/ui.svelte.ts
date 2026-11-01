// Where the traveler is: which screen, which dialog over it, and the toast
// that says something finished. Screens read and set this;
// it holds no trip data, which comes live from IndexedDB.

import type { BookingReading } from '../share/booking-text';
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

export type ToastKind = 'success' | 'error' | 'info';

export interface ToastAction {
  label: string;
  run: () => void;
  /** Called when the toast goes away without the action being taken. */
  dismissed?: () => void;
}

export interface Toast {
  message: string;
  kind: ToastKind;
  key: number;
  action?: ToastAction;
  /** Fading out. */
  leaving?: boolean;
}

/** How long a toast stays (style.md §8.12): longer when it offers an action. */
export const TOAST_MS = 4000;
export const TOAST_ACTION_MS = 6000;
/** --dur-exit */
const TOAST_EXIT_MS = 160;

export type Modal =
  | { name: 'newTrip' }
  | { name: 'shelfMenu' }
  | { name: 'about' }
  | { name: 'import'; bundles: TripBundle[]; existing: Set<string> }
  | { name: 'problem'; title: string; detail: string }
  | { name: 'traveler'; traveler: Traveler }
  | { name: 'sharedFlights'; reading: BookingReading };

class Ui {
  screen = $state<Screen>({ name: 'shelf' });
  // Raw: a dialog carries domain objects on their way to IndexedDB, which
  // cannot store the proxies that deep $state would wrap them in.
  modal = $state.raw<Modal | null>(null);
  /** Bumped whenever a dialog replaces another, so the modal can reset its scroll and focus. */
  modalKey = $state(0);
  toast = $state.raw<Toast | null>(null);

  private releaseBack: (() => void) | null = null;
  private toastTimer: ReturnType<typeof setTimeout> | undefined;
  private toastKey = 0;

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

  /** Shows one toast, replacing any current one. */
  notify(message: string, kind: ToastKind = 'info', action?: ToastAction): void {
    if (!this.toast?.leaving) this.toast?.action?.dismissed?.();
    this.toast = { message, kind, key: ++this.toastKey, action };
    this.resumeToast();
  }

  /** Holds the toast on screen while it is touched or hovered. */
  pauseToast(): void {
    clearTimeout(this.toastTimer);
  }

  resumeToast(): void {
    clearTimeout(this.toastTimer);
    const toast = this.toast;
    if (!toast) return;
    this.toastTimer = setTimeout(
      () => this.dismissToast(toast.key),
      toast.action ? TOAST_ACTION_MS : TOAST_MS
    );
  }

  dismissToast(key: number): void {
    const toast = this.toast;
    if (toast?.key !== key || toast.leaving) return;
    clearTimeout(this.toastTimer);
    toast.action?.dismissed?.();
    this.toast = { ...toast, action: undefined, leaving: true };
    setTimeout(() => {
      if (this.toast?.key === key) this.toast = null;
    }, TOAST_EXIT_MS);
  }

  /** Runs the toast's action and closes it. */
  actOnToast(): void {
    const toast = this.toast;
    if (!toast?.action) return;
    clearTimeout(this.toastTimer);
    this.toast = null;
    toast.action.run();
  }
}

export const ui = new Ui();
