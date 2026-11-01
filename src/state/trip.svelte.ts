// One open trip: what every screen inside it reads, and what they do to it.
// The counterpart of TripViewModel. Its subscriptions are live queries on
// IndexedDB, so a change made anywhere (another tab of the trip, an import)
// shows here without asking; the clock ticks once a minute for the "now"
// line and the countdowns.

import { askForReminders } from './shell.svelte';
import { ui } from './ui.svelte';
import { liveQuery, type Subscription } from 'dexie';
import { partyWarnings, splitWindows } from '../domain/party-split';
import {
  traveler,
  type Idea,
  type IdeaStatus,
  type RaisedReminder,
  type Segment,
  type Traveler,
  type TripDossier
} from '../domain/model';
import { newId } from '../db/trips';
import { buildTimeline } from '../domain/timeline';
import { ideaSections, ideaTally, ideasByCity, ideasByDay } from '../domain/ideas';
import type { LocalDate, LocalTime } from '../domain/time';
import { CostsModel } from './costs.svelte';
import { ideaFields, ideaZone, tripCities, tripDays, type IdeaDraft } from './idea-plan';
import { alerts, ideas, settings, trips } from './app';
import { editedTrip, freeTimeIdeas, reminderBanner } from './trip-logic';

export type TripTab = 'TIMELINE' | 'IDEAS' | 'PARTY' | 'COSTS';
export const TRIP_TABS: readonly { id: TripTab; label: string; domId: string; controls: string }[] = [
  { id: 'TIMELINE', label: 'Timeline', domId: 'trip-tab-timeline', controls: 'trip-tab-panel' },
  { id: 'IDEAS', label: 'Ideas', domId: 'trip-tab-ideas', controls: 'trip-tab-panel' },
  { id: 'PARTY', label: 'Party', domId: 'trip-tab-party', controls: 'trip-tab-panel' },
  { id: 'COSTS', label: 'Costs', domId: 'trip-tab-costs', controls: 'trip-tab-panel' }
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
  showDismissed = $state(false);

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

  readonly cities = $derived(tripCities(this.dossier));
  readonly days = $derived(tripDays(this.dossier));
  readonly ideaTally = $derived(ideaTally(this.ideas));
  readonly ideaSections = $derived(
    ideaSections(this.ideas, { travelerFilter: this.travelerFilter, includeDismissed: this.showDismissed })
  );
  readonly ideasByCity = $derived(
    ideasByCity(this.ideas, {
      cityOrder: this.cities,
      travelerFilter: this.travelerFilter,
      includeDismissed: this.showDismissed
    })
  );
  readonly dayPlan = $derived(
    ideasByDay(this.ideas, { travelerFilter: this.travelerFilter, includeDismissed: this.showDismissed })
  );

  /** The Costs tab's half, read as long as the trip is open. */
  readonly costs: CostsModel;

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
    this.costs = new CostsModel(tripId, () => this.dossier);
  }

  destroy(): void {
    for (const s of this.subscriptions) s.unsubscribe();
    clearInterval(this.ticker);
    this.costs.destroy();
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
  async saveSegment(segment: Segment): Promise<void> {
    await trips.saveSegment(segment);
    void askForReminders();
  }

  deleteSegment(segmentId: string): Promise<void> {
    return trips.deleteSegment(segmentId);
  }

  // — Ideas ——————————————————————————————————————————————————————————————

  async addIdea(draft: IdeaDraft): Promise<void> {
    if (draft.title.trim() === '') return;
    await ideas.add(this.tripId, ideaFields(draft, this.dossier));
  }

  setIdeaStatus(ideaId: string, status: IdeaStatus): Promise<void> {
    return ideas.setStatus(ideaId, status);
  }

  toggleInterest(ideaId: string, travelerId: string): Promise<void> {
    return ideas.toggleInterest(ideaId, travelerId);
  }

  pencilIdeaFor(ideaId: string, date: LocalDate | null): Promise<void> {
    return ideas.setPlannedDay(ideaId, date);
  }

  /** Removed at once, and offered back for a few seconds. */
  async deleteIdea(ideaId: string): Promise<void> {
    const removed = await ideas.delete(ideaId);
    if (!removed) return;
    ui.notify(`Removed “${removed.title}”.`, 'info', { label: 'Undo', run: () => void ideas.save(removed) });
  }

  /** Put an idea on the timeline, on the clock of the place it happens. */
  async scheduleIdea(idea: Idea, date: LocalDate, start: LocalTime, minutes: number | null): Promise<void> {
    await ideas.schedule(
      idea,
      date,
      start,
      ideaZone(idea, this.dossier, date),
      idea.interestedTravelerIds,
      minutes
    );
  }

  // — The party ——————————————————————————————————————————————————————————

  async addTraveler(fullName: string, nickname: string | null): Promise<void> {
    if (fullName.trim() === '') return;
    await trips.addTraveler(
      this.tripId,
      traveler({ id: newId('trav'), fullName: fullName.trim(), nickname: nickname?.trim() || null })
    );
  }

  /** Someone from another trip, brought onto this one with their profile. */
  addExistingTraveler(travelerId: string): Promise<void> {
    return trips.addExistingTraveler(this.tripId, travelerId);
  }

  removeTraveler(travelerId: string): Promise<void> {
    return trips.removeTraveler(this.tripId, travelerId);
  }
}
