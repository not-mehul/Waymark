<script lang="ts">
  // Until phase 3 brings the timeline, a trip opens to what the shelf already
  // knows about it, so a restored backup can be checked trip by trip.
  import { liveQuery } from 'dexie';
  import { segmentStart, segmentTitle, tripEndDate, tripStartDate } from '../../domain/model';
  import { dateRange, dayCompact } from '../../domain/time-text';
  import { trips } from '../../state/app';
  import { plural } from '../../state/shelf';
  import { ui } from '../../state/ui.svelte';
  import Icon from '../ui/Icon.svelte';
  import PartyMark from '../ui/PartyMark.svelte';
  import SectionHeader from '../ui/SectionHeader.svelte';

  let { tripId }: { tripId: string } = $props();
  const dossier = liveQuery(() => trips.dossier(tripId));
</script>

<section class="screen" aria-labelledby="trip-title">
  <header class="screen-head">
    <button type="button" class="btn-icon" aria-label="Back" onclick={() => ui.go({ name: 'shelf' })}>
      <Icon name="arrowLeft" />
    </button>
    <h1 class="t-card-title ellipsis" id="trip-title">{$dossier?.trip.name ?? ''}</h1>
  </header>

  {#if $dossier === undefined}
    <p class="t-hint empty-line">Reading…</p>
  {:else if $dossier === null}
    <p class="t-hint empty-line">This trip is no longer on this device.</p>
  {:else}
    {@const { trip, party, segments } = $dossier}
    <div class="panel">
      {#if trip.destinationSummary}<p class="t-body-small">{trip.destinationSummary}</p>{/if}
      <p class="t-data">{dateRange(tripStartDate(trip), tripEndDate(trip))}</p>
      <p class="t-data-small">
        {plural(segments.length, 'booking')} · {plural(party.travelers.length, 'traveler')}{trip.archived
          ? ' · put away'
          : ''}
      </p>
    </div>

    {#if party.travelers.length > 0}
      <SectionHeader label="Party" />
      {#each party.travelers as traveler (traveler.id)}
        <div class="person-row">
          <PartyMark {traveler} />
          <span class="t-body-small">{traveler.fullName}</span>
        </div>
      {/each}
    {/if}

    {#if segments.length > 0}
      <SectionHeader label="Bookings" />
      {#each segments as segment (segment.id)}
        <div class="t-data-small">
          {dayCompact(segmentStart(segment).toPlainDate())} · {segmentTitle(segment)}
        </div>
      {/each}
    {/if}

    <p class="t-hint">
      The timeline, the map and the costs for this trip arrive in the next step of the rewrite.
    </p>

    <div class="btn-row">
      <button type="button" class="btn btn-muted" onclick={() => trips.setArchived(trip.id, !trip.archived)}>
        <Icon name="archive" />
        {trip.archived ? 'Bring back' : 'Put away'}
      </button>
      <button
        type="button"
        class="btn btn-danger"
        onclick={() => ui.open({ name: 'deleteTrip', tripId: trip.id })}
      >
        <Icon name="trash" /> Delete
      </button>
    </div>
  {/if}
</section>
