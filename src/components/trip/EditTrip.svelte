<script lang="ts">
  // The four facts a trip was created with, editable afterwards. Bookings
  // keep their own dates: a trip's window is only the frame they are read
  // against, and a booking outside it is a truer thing to show than one
  // silently moved.
  import { tripEndDate, tripStartDate } from '../../domain/model';
  import { daysBetween, type LocalDate } from '../../domain/time';
  import { dateRange } from '../../domain/time-text';
  import { bookedSpan } from '../../state/trip-logic';
  import DateField from '../ui/DateField.svelte';
  import Field from '../ui/Field.svelte';
  import Icon from '../ui/Icon.svelte';
  import ModalHead from '../ui/ModalHead.svelte';
  import { useTrip } from './context';

  const { model, close } = useTrip();
  const trip = model.dossier!.trip;
  let name = $state(trip.name);
  let destination = $state(trip.destinationSummary);
  let start = $state(tripStartDate(trip));
  let end = $state(tripEndDate(trip));

  const days = $derived(daysBetween(start, end) + 1);
  const ready = $derived(name.trim() !== '' || destination.trim() !== '');
  // A trip only ever widens on its own, so a booking moved or removed leaves
  // the old edge behind. This pulls both edges in to exactly what is booked.
  const span = $derived(bookedSpan(model.dossier?.segments ?? []));
  const fits = $derived(
    span !== null && (!span[0].equals(tripStartDate(trip)) || !span[1].equals(tripEndDate(trip)))
  );

  function moveStart(picked: LocalDate) {
    const length = Math.max(0, daysBetween(start, end));
    start = picked;
    if (daysBetween(picked, end) < 0) end = picked.add({ days: length });
  }

  async function save() {
    if (!ready) return;
    close();
    await model.updateTrip(name, destination, start, end);
  }

  async function fit() {
    close();
    await model.fitDatesToBookings();
  }
</script>

<ModalHead eyebrow={days === 1 ? 'One day' : `${days} days`} title="Edit this trip" />
<form
  class="modal-form"
  onsubmit={(ev) => {
    ev.preventDefault();
    void save();
  }}
>
  <Field label="Name" id="edit-trip-name" placeholder="London & Paris" bind:value={name} />
  <Field
    label="Destinations"
    id="edit-trip-destinations"
    placeholder="London, then Paris"
    bind:value={destination}
  />
  <div class="field-row">
    <DateField label="First day" id="edit-trip-start" value={start} onchange={moveStart} />
    <DateField label="Last day" id="edit-trip-end" value={end} min={start} onchange={(d) => (end = d)} />
  </div>
  {#if fits && span}
    <button type="button" class="btn btn-muted btn-block" onclick={fit}>
      <Icon name="calendar" /> Fit to bookings: {dateRange(span[0], span[1])}
    </button>
  {/if}
  <div class="btn-row">
    <button type="button" class="btn btn-muted" onclick={close}>Cancel</button>
    <button type="submit" class="btn btn-primary" disabled={!ready}><Icon name="check" /> Save</button>
  </div>
</form>
