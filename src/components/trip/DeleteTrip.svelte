<script lang="ts">
  import { tripEndDate, tripStartDate } from '../../domain/model';
  import { dateRange } from '../../domain/time-text';
  import { ui } from '../../state/ui.svelte';
  import Field from '../ui/Field.svelte';
  import Icon from '../ui/Icon.svelte';
  import ModalHead from '../ui/ModalHead.svelte';
  import { useTrip } from './context';

  const { model, close } = useTrip();
  let typed = $state('');
  const dossier = $derived(model.dossier);
  const matches = $derived(
    dossier ? typed.trim().toLowerCase() === dossier.trip.name.trim().toLowerCase() : false
  );

  async function remove() {
    if (!matches) return;
    close();
    ui.go({ name: 'shelf' });
    await model.deleteTrip();
  }

  async function putAway() {
    close();
    ui.go({ name: 'shelf' });
    await model.setArchived(true);
  }
</script>

<ModalHead eyebrow="No undo" title="Delete this trip" />
{#if dossier}
  {@const { trip, segments } = dossier}
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
    <button type="button" class="btn btn-muted" onclick={close}>Keep it</button>
    <button type="button" class="btn btn-danger" disabled={!matches} onclick={remove}>
      <Icon name="trash" /> Delete
    </button>
  </div>
{/if}
