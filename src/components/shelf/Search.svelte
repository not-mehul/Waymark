<script lang="ts">
  // Every trip at once: a place, a hotel, an airline, a confirmation code.
  import { searchTrips, type SearchHit } from '../../domain/search';
  import { zonedAt } from '../../domain/time';
  import { dayCompact } from '../../domain/time-text';
  import { trips } from '../../state/app';
  import { ui } from '../../state/ui.svelte';
  import Icon from '../ui/Icon.svelte';
  import ModalHead from '../ui/ModalHead.svelte';

  let query = $state('');
  const everything = trips.bundles();
  let hits = $state.raw<SearchHit[]>([]);
  $effect(() => {
    const q = query;
    const timer = setTimeout(() => void everything.then((all) => (hits = searchTrips(all, q))), 120);
    return () => clearTimeout(timer);
  });

  function go(hit: SearchHit) {
    ui.close();
    ui.go({ name: 'trip', tripId: hit.tripId, segmentId: hit.segmentId ?? undefined });
  }
</script>

<ModalHead eyebrow="Every trip" title="Find" />
<div class="field">
  <label class="t-field-label" for="find-query">A place, a hotel, an airline or a reference</label>
  <!-- svelte-ignore a11y_autofocus -->
  <input
    id="find-query"
    class="input"
    type="search"
    inputmode="search"
    enterkeyhint="search"
    autocomplete="off"
    placeholder="Lisbon hotel, BA 286, K7PQ2X"
    autofocus
    bind:value={query}
  />
</div>
{#if query.trim().length >= 2}
  {#if hits.length === 0}
    <p class="t-hint">Nothing on any trip matches that.</p>
  {:else}
    <ul class="menu-list" aria-label="Results">
      {#each hits as hit (hit.tripId + (hit.segmentId ?? ''))}
        <li>
          <button type="button" class="menu-item" onclick={() => go(hit)}>
            <Icon name={hit.segmentId ? 'ticket' : 'compass'} />
            <span class="menu-text">
              <span class="t-body-small">{hit.title}</span>
              <span class="t-hint">
                {hit.segmentId ? `${hit.tripName} · ` : 'Trip · '}{dayCompact(
                  zonedAt(hit.atMillis, hit.zoneId).toPlainDate()
                )}
                {zonedAt(hit.atMillis, hit.zoneId).year}
              </span>
            </span>
            <Icon name="chevronRight" />
          </button>
        </li>
      {/each}
    </ul>
  {/if}
{/if}
