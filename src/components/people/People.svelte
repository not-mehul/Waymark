<script lang="ts">
  import { liveQuery } from 'dexie';
  import { trips } from '../../state/app';
  import { plural, profileSummary } from '../../state/shelf';
  import { ui } from '../../state/ui.svelte';
  import Icon from '../ui/Icon.svelte';
  import PartyMark from '../ui/PartyMark.svelte';

  const people = liveQuery(async () => ({
    travelers: await trips.allTravelers(),
    counts: await trips.tripCounts()
  }));
</script>

<section class="screen" aria-labelledby="people-title">
  <header class="screen-head">
    <button type="button" class="btn-icon" aria-label="Back" onclick={() => ui.go({ name: 'shelf' })}>
      <Icon name="arrowLeft" />
    </button>
    <h1 class="t-card-title" id="people-title">People</h1>
  </header>

  {#if !$people}
    <p class="t-hint empty-line">Reading…</p>
  {:else if $people.travelers.length === 0}
    <p class="t-hint empty-line">Nobody yet. Travelers are added from a trip's Party tab.</p>
  {:else}
    {#each $people.travelers as traveler (traveler.id)}
      {@const summary = profileSummary(traveler)}
      <button type="button" class="panel person-row" onclick={() => ui.open({ name: 'traveler', traveler })}>
        <PartyMark {traveler} />
        <div>
          <div class="t-body-small" style:color="var(--text-heading)">{traveler.fullName}</div>
          <div class="t-data-small ellipsis">
            {plural($people.counts.get(traveler.id) ?? 0, 'trip')}{summary ? ` · ${summary}` : ''}
          </div>
        </div>
      </button>
    {/each}
    <p class="t-hint">A change here shows on every trip the person is on.</p>
  {/if}
</section>
