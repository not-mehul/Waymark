<script lang="ts">
  import { displayName, type Traveler } from '../../domain/model';
  import ModalHead from '../ui/ModalHead.svelte';
  import { useTrip } from './context';

  let { traveler }: { traveler: Traveler } = $props();
  const { model, close } = useTrip();

  // Bookings nobody else is on cannot stay: there would be no one left on
  // them. Say how many before they go.
  const onlyTheirs = $derived(
    (model.dossier?.segments ?? []).filter(
      (s) => s.travelerIds.length === 1 && s.travelerIds[0] === traveler.id
    ).length
  );

  async function remove() {
    close();
    await model.removeTraveler(traveler.id);
  }
</script>

<ModalHead title="Take {displayName(traveler)} off this trip" />
<p>
  {#if onlyTheirs === 0}
    Their name comes off every booking they share with the others.
  {:else}
    {onlyTheirs === 1 ? '1 booking only they are on goes' : `${onlyTheirs} bookings only they are on go`} with them,
    and their name comes off the rest.
  {/if}
</p>
<p class="t-hint">Their profile stays if they are on another trip.</p>
<div class="btn-row">
  <button type="button" class="btn btn-muted" onclick={close}>Cancel</button>
  <button type="button" class="btn btn-danger" onclick={remove}>Remove</button>
</div>
