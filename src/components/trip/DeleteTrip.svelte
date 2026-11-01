<script lang="ts">
  import { liveQuery } from 'dexie';
  import { tripEndDate, tripStartDate } from '../../domain/model';
  import { dateRange } from '../../domain/time-text';
  import { trips } from '../../state/app';
  import { ui } from '../../state/ui.svelte';
  import Field from '../ui/Field.svelte';
  import Icon from '../ui/Icon.svelte';
  import ModalHead from '../ui/ModalHead.svelte';

  let { tripId }: { tripId: string } = $props();
  const dossier = liveQuery(() => trips.dossier(tripId));
  let typed = $state('');
  const matches = $derived(
    $dossier ? typed.trim().toLowerCase() === $dossier.trip.name.trim().toLowerCase() : false
  );

  async function remove() {
    if (!matches) return;
    await trips.deleteTrip(tripId);
    ui.close();
    ui.go({ name: 'shelf' });
  }

  async function putAway() {
    await trips.setArchived(tripId, true);
    ui.close();
  }
</script>

<ModalHead eyebrow="No undo" title="Delete this trip" />
{#if $dossier}
  {@const { trip, segments } = $dossier}
  <p class="t-body-small">{trip.name} · {dateRange(tripStartDate(trip), tripEndDate(trip))}</p>
  <p class="t-hint">
    {segments.length}
    {segments.length === 1 ? 'booking' : 'bookings'}, every idea and every note will be deleted from this
    device.
  </p>
  <Field
    label="Type the trip's name to confirm"
    id="delete-confirm"
    placeholder={trip.name}
    bind:value={typed}
  />
  <!-- Most trips people delete are over. Putting one away keeps it, quiet, back in one tap. -->
  {#if !trip.archived}
    <button type="button" class="btn btn-muted btn-block" onclick={putAway}>
      <Icon name="archive" /> Put it away instead
    </button>
  {/if}
  <div class="btn-row">
    <button type="button" class="btn btn-muted" onclick={() => ui.close()}>Keep it</button>
    <button type="button" class="btn btn-danger" disabled={!matches} onclick={remove}>
      <Icon name="trash" /> Delete
    </button>
  </div>
{/if}
