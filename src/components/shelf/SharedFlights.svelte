<script lang="ts">
  // Flights read out of an email shared into Waymark, to be added to a trip in
  // one go. Only the ones with every detail found can be saved; the rest are
  // shown, so it is clear what to add by hand.
  import { liveQuery } from 'dexie';
  import { compareDates } from '../../domain/time';
  import { tripEndDate, tripStartDate } from '../../domain/model';
  import { clock, dateRange, day } from '../../domain/time-text';
  import { isComplete, type BookingReading } from '../../share/booking-text';
  import { addSharedFlights } from '../../state/actions';
  import { trips } from '../../state/app';
  import { arrangeShelf, plural } from '../../state/shelf';
  import { ui } from '../../state/ui.svelte';
  import Icon from '../ui/Icon.svelte';
  import ModalHead from '../ui/ModalHead.svelte';

  let { reading }: { reading: BookingReading } = $props();

  const everything = liveQuery(() => trips.trips());
  const choices = $derived.by(() => {
    if (!$everything) return [];
    const shelf = arrangeShelf($everything);
    return [...shelf.active, ...shelf.upcoming, ...shelf.past];
  });
  const complete = $derived(reading.flights.filter(isComplete));
  const firstDate = $derived(complete.find((f) => f.date)?.date ?? null);

  // The trip the first flight falls in, if there is one; otherwise the first on the shelf.
  let picked = $state<string | null>(null);
  const chosen = $derived(
    picked ??
      choices.find(
        (t) =>
          firstDate !== null &&
          compareDates(tripStartDate(t), firstDate) <= 0 &&
          compareDates(firstDate, tripEndDate(t)) <= 0
      )?.id ??
      choices[0]?.id ??
      null
  );
  let busy = $state(false);

  async function add() {
    if (!chosen || busy) return;
    busy = true;
    const saved = await addSharedFlights(reading, chosen);
    ui.tell(saved === 1 ? 'One flight added.' : `${saved} flights added.`);
  }
</script>

<ModalHead eyebrow="Shared with Waymark" title={plural(reading.flights.length, 'flight')} />
<ul class="menu-list">
  {#each reading.flights as found, i (i)}
    <li class="credit" style:padding="var(--space-2) 0">
      <span class="t-data">
        {found.designator}{found.origin && found.destination
          ? ` · ${found.origin} → ${found.destination}`
          : ''}
      </span>
      <span class="t-hint">
        {#if isComplete(found)}
          {day(found.date!)}, {clock(found.departs!)} → {clock(found.arrives!)}
        {:else}
          Not everything was found; add this one by hand.
        {/if}
      </span>
    </li>
  {/each}
</ul>

{#if complete.length > 0 && choices.length > 0}
  <div class="field">
    <span class="t-field-label">Add to</span>
    <div class="chip-row wrap" role="group" aria-label="Add to">
      {#each choices as trip (trip.id)}
        <button
          type="button"
          class="chip"
          aria-pressed={chosen === trip.id}
          onclick={() => (picked = trip.id)}
        >
          {trip.name}
          <span class="t-hint">· {dateRange(tripStartDate(trip), tripEndDate(trip))}</span>
        </button>
      {/each}
    </div>
  </div>
  <div class="btn-row">
    <button type="button" class="btn btn-muted" onclick={() => ui.close()}>Cancel</button>
    <button type="button" class="btn btn-primary" disabled={!chosen || busy} onclick={add}>
      <Icon name="plus" />
      Add {plural(complete.length, 'flight')}
    </button>
  </div>
{:else}
  <p class="t-hint">
    {complete.length === 0
      ? 'None of these has every detail Waymark needs. Add them by hand from a trip.'
      : 'Make a trip first, then share the email again.'}
  </p>
  <button type="button" class="btn btn-muted btn-block" onclick={() => ui.close()}>Close</button>
{/if}
