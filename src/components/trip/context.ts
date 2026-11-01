// What every screen and dialog inside an open trip shares: the trip itself,
// and the one dialog slot over it.

import { getContext, setContext } from 'svelte';
import type { Segment } from '../../domain/model';
import type { TripModel } from '../../state/trip.svelte';

export type TripSheet =
  | { name: 'menu' }
  | { name: 'edit' }
  | { name: 'reminders' }
  | { name: 'online' }
  | { name: 'delete' }
  | { name: 'removeSegment'; segment: Segment }
  | { name: 'addPlan' };

export interface TripContext {
  model: TripModel;
  open(sheet: TripSheet): void;
  close(): void;
}

const KEY = Symbol('trip');

export const provideTrip = (context: TripContext) => setContext(KEY, context);
export const useTrip = () => getContext<TripContext>(KEY);
