<script lang="ts">
  import { tripEndDate, tripStartDate } from '../../domain/model';
  import { dateRange } from '../../domain/time-text';
  import type { TripBundle } from '../../share/trip-archive';
  import { importTrips } from '../../state/actions';
  import { plural } from '../../state/shelf';
  import { ui } from '../../state/ui.svelte';
  import Icon from '../ui/Icon.svelte';
  import ModalHead from '../ui/ModalHead.svelte';

  let { bundles, existing }: { bundles: TripBundle[]; existing: Set<string> } = $props();
  let busy = $state(false);
  const replacing = $derived(bundles.some((b) => existing.has(b.trip.id)));

  async function confirm() {
    busy = true;
    try {
      await importTrips(bundles);
    } catch (error) {
      console.error('Import failed', error);
      busy = false;
      ui.open({ name: 'problem', title: 'That file could not be opened', detail: String(error) });
    }
  }
</script>

<ModalHead
  eyebrow="Open in Waymark"
  title={bundles.length === 1 ? bundles[0]!.trip.name : `${bundles.length} trips`}
/>
<ul class="menu-list">
  {#each bundles as bundle (bundle.trip.id)}
    <li class="credit" style:padding="var(--space-2) 0">
      <span class="t-body-small">{bundle.trip.name}</span>
      <span class="t-hint">
        {dateRange(tripStartDate(bundle.trip), tripEndDate(bundle.trip))} · {plural(
          bundle.segments.length,
          'booking'
        )}
        · {plural(bundle.travelers.length, 'traveler')}
      </span>
      {#if existing.has(bundle.trip.id)}
        <span class="t-hint" style:color="var(--accent-amber)">Replaces the copy already on this phone</span>
      {/if}
    </li>
  {/each}
</ul>
<p class="t-hint">Opening a trip that is already here replaces it with this copy, bookings and all.</p>
<div class="btn-row">
  <button type="button" class="btn btn-muted" onclick={() => ui.close()}>Cancel</button>
  <button type="button" class="btn btn-primary" disabled={busy} onclick={confirm}>
    <Icon name="download" />
    {replacing ? 'Replace' : 'Add'}
  </button>
</div>
