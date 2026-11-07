<script lang="ts">
  import { liveQuery } from 'dexie';
  import { APP_VERSION, settings, trips } from '../../state/app';
  import { loadExample } from '../../state/actions';
  import { arrangeShelf } from '../../state/shelf';
  import { segmentStart, segmentTitle } from '../../domain/model';
  import { systemZone, todayIn } from '../../domain/time';
  import { clock, day, dayCompact, relative } from '../../domain/time-text';
  import { ui } from '../../state/ui.svelte';
  import Icon from '../ui/Icon.svelte';
  import SectionHeader from '../ui/SectionHeader.svelte';
  import { ThemeToggle } from 'dusk-dawn';
  import TripCard from './TripCard.svelte';

  const everything = liveQuery(() => trips.trips());
  let showArchived = $state(false);

  const now = Date.now();
  const today = day(todayIn(systemZone(), now));
  // The one booking that comes next, whichever trip it is on: what the widget shows.
  const upNext = liveQuery(() => trips.nextAcrossTrips(Date.now(), 1));
  const next = $derived($upNext?.[0] ?? null);
  const shelf = $derived($everything ? arrangeShelf($everything, now) : null);
  // Headings appear only where there is more than one group to tell apart.
  const grouped = $derived(
    shelf !== null && shelf.active.length > 0 && (shelf.upcoming.length > 0 || shelf.past.length > 0)
  );
  const open = (tripId: string) => ui.go({ name: 'trip', tripId });
</script>

<section class="screen" aria-labelledby="shelf-title">
  <header class="masthead">
    <div>
      <span class="t-section-label shelf-eyebrow">{today}</span>
      <h1 class="t-page-title" id="shelf-title">Waymark</h1>
      <p class="t-tagline">Your whole trip, offline.</p>
    </div>
    <div class="masthead-controls">
      <ThemeToggle
        dawn={settings.mode === 'light'}
        label="Dawn theme"
        ontoggle={() => settings.toggleTheme()}
      />
      <button
        type="button"
        class="btn-icon"
        aria-label="Every trip"
        onclick={() => ui.open({ name: 'shelfMenu' })}
      >
        <Icon name="menu" />
      </button>
    </div>
  </header>

  {#if next}
    {@const [nextTrip, segment] = next}
    {@const start = segmentStart(segment)}
    {@const under = segment.startEpochMillis <= now}
    <button
      type="button"
      class="up-next"
      onclick={() => ui.go({ name: 'trip', tripId: nextTrip.id, segmentId: segment.id })}
    >
      <span class="t-field-label">{under ? 'Under way' : 'Up next'} · {nextTrip.name}</span>
      <span class="up-next-row">
        <span class="t-body ellipsis">{segmentTitle(segment)}</span>
        <span class="t-data-small accent up-next-when">
          {under ? `ends ${relative(segment.endEpochMillis, now)}` : relative(segment.startEpochMillis, now)}
        </span>
      </span>
      <span class="t-data-small dim">{dayCompact(start.toPlainDate())} · {clock(start)} local</span>
    </button>
  {/if}

  {#if shelf}
    {#if shelf.active.length > 0}
      {#if grouped}<SectionHeader label="Under way" />{/if}
      {#each shelf.active as trip (trip.id)}
        <TripCard {trip} {now} onclick={() => open(trip.id)} />
      {/each}
    {/if}

    {#if shelf.upcoming.length === 0 && shelf.active.length === 0}
      <!-- Only on a genuinely empty shelf is the example offered: somebody who
           has just deleted their last trip does not want somebody else's back. -->
      {#if $everything?.length === 0}
        <div class="empty-state">
          <Icon name="compass" />
          <p class="empty-title">No trips yet</p>
          <p class="empty-text">
            Every flight, stay and transfer for a trip, on one timeline that works offline.
          </p>
          <button type="button" class="btn btn-secondary" onclick={loadExample}
            >Load the worked example</button
          >
        </div>
      {:else}
        <p class="t-hint empty-line">Nothing planned yet.</p>
      {/if}
    {:else}
      {#if grouped && shelf.upcoming.length > 0}<SectionHeader label="Ahead" />{/if}
      {#each shelf.upcoming as trip (trip.id)}
        <TripCard {trip} {now} onclick={() => open(trip.id)} />
      {/each}
    {/if}

    {#if shelf.past.length > 0}
      <SectionHeader label="Behind" />
      {#each shelf.past as trip (trip.id)}
        <TripCard {trip} {now} dimmed onclick={() => open(trip.id)} />
      {/each}
    {/if}

    <!-- Put-away trips stay out of sight until asked for: kept, not shelved. -->
    {#if shelf.archived.length > 0}
      <button type="button" class="link-button" onclick={() => (showArchived = !showArchived)}>
        {showArchived ? 'Hide what is put away' : `Put away (${shelf.archived.length})`}
      </button>
      {#if showArchived}
        {#each shelf.archived as trip (trip.id)}
          <TripCard {trip} {now} dimmed onclick={() => open(trip.id)} />
        {/each}
      {/if}
    {/if}
  {:else}
    <p class="t-hint empty-line">Reading the shelf…</p>
  {/if}

  <button type="button" class="btn btn-primary btn-block" onclick={() => ui.open({ name: 'newTrip' })}>
    <Icon name="plus" /> New itinerary
  </button>
  <button type="button" class="version-line t-data-small" onclick={() => ui.open({ name: 'about' })}>
    Waymark {APP_VERSION} · offline
  </button>
</section>
