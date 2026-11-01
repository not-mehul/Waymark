// One open trip: what every screen inside it reads, and what they do to it.
// The counterpart of TripViewModel. Its subscriptions are live queries on
// IndexedDB, so a change made anywhere (another tab of the trip, an import)
// shows here without asking; the clock ticks once a minute for the "now"
// line and the countdowns.

import { liveQuery, type Subscription } from 'dexie';
import { partyWarnings, splitWindows } from '../domain/party-split';
import type { Idea, RaisedReminder, Segment, Traveler, TripDossier } from '../domain/model';
import { buildTimeline } from '../domain/timeline';
import type { LocalDate } from '../domain/time';
import { alerts, ideas, settings, trips } from './app';
import { editedTrip, freeTimeIdeas, reminderBanner } from './trip-logic';

export type TripTab = 'TIMELINE' | 'IDEAS' | 'PARTY' | 'COSTS';
export const TRIP_TABS: readonly { tab: TripTab; label: string }[] = [
  { tab: 'TIMELINE', label: 'Timeline' },
  { tab: 'IDEAS', label: 'Ideas' },
  { tab: 'PARTY', label: 'Party' },
  { tab: 'COSTS', label: 'Costs' }
];

const MINUTE = 60_000;

export class TripModel {
  /** Undefined while reading; null once the trip turns out not to exist. */
  dossier = $state.raw<TripDossier | null | undefined>(undefined);
  ideas = $state.raw<Idea[]>([]);
  raised = $state.raw<RaisedReminder[]>([]);
  everyone = $state.raw<Traveler[]>([]);
  now = $state(Date.now());

  tab = $state<TripTab>('TIMELINE');
  travelerFilter = $state<string | null>(null);

  readonly timeline = $derived(
    this.dossier
      ? buildTimeline(this.dossier, { travelerFilter: this.travelerFilter, nowMillis: this.now })
      : []
  );
  readonly freeTime = $derived(freeTimeIdeas(this.timeline, this.ideas, this.now));
  readonly banner = $derived(this.dossier ? reminderBanner(this.dossier, this.raised, this.now) : null);
  readonly splits = $derived(this.dossier ? splitWindows(this.dossier) : []);
  readonly partyNotes = $derived(this.dossier ? partyWarnings(this.dossier) : []);
  readonly party = $derived(this.dossier?.party.travelers ?? []);
  /** People from other trips who could be brought onto this one. */
  readonly otherTravelers = $derived.by(() => {
    const here = this.party.map((t) => t.id);
    return this.everyone.filter((t) => !here.includes(t.id));
  });

  private readonly subscriptions: Subscription[];
  private readonly ticker: ReturnType<typeof setInterval>;

  constructor(readonly tripId: string) {
    this.subscriptions = [
      liveQuery(() => trips.dossier(tripId)).subscribe((d) => (this.dossier = d)),
      liveQuery(() => ideas.forTrip(tripId)).subscribe((list) => (this.ideas = list)),
      liveQuery(() => alerts.recent()).subscribe((list) => (this.raised = list)),
      liveQuery(() => trips.allTravelers()).subscribe((list) => (this.everyone = list))
    ];
    this.ticker = setInterval(() => (this.now = Date.now()), MINUTE);
  }

  destroy(): void {
    for (const s of this.subscriptions) s.unsubscribe();
    clearInterval(this.ticker);
  }

  segment(segmentId: string): Segment | null {
    return this.dossier?.segments.find((s) => s.id === segmentId) ?? null;
  }

  /** Tapping the traveler already shown clears the filter. */
  filterBy(travelerId: string | null): void {
    this.travelerFilter = this.travelerFilter === travelerId ? null : travelerId;
  }

  get reminders() {
    return settings.reminders;
  }

  // — The trip ———————————————————————————————————————————————————————————

  async updateTrip(name: string, destination: string, start: LocalDate, end: LocalDate): Promise<void> {
    const trip = this.dossier?.trip;
    if (trip) await trips.saveTrip(editedTrip(trip, name, destination, start, end));
  }

  fitDatesToBookings(): Promise<void> {
    return trips.fitTripToBookings(this.tripId);
  }

  setArchived(archived: boolean): Promise<void> {
    return trips.setArchived(this.tripId, archived);
  }

  deleteTrip(): Promise<void> {
    return trips.deleteTrip(this.tripId);
  }

  dismissBanner(segmentId: string): Promise<void> {
    return alerts.acknowledge(segmentId);
  }

  // — Bookings ———————————————————————————————————————————————————————————

  /** Written once, on Save: a booking never drifts while it is being read. */
  saveSegment(segment: Segment): Promise<void> {
    return trips.saveSegment(segment);
  }

  deleteSegment(segmentId: string): Promise<void> {
    return trips.deleteSegment(segmentId);
  }
}
