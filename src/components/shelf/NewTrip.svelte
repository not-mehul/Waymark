<script lang="ts">
  import {
    startOfDay,
    systemZone,
    todayIn,
    daysBetween,
    zonedOf,
    localTime,
    type LocalDate
  } from '../../domain/time';
  import { trips } from '../../state/app';
  import { ui } from '../../state/ui.svelte';
  import DateField from '../ui/DateField.svelte';
  import Field from '../ui/Field.svelte';
  import Icon from '../ui/Icon.svelte';
  import ModalHead from '../ui/ModalHead.svelte';

  const zone = systemZone();
  const fortnight = todayIn(zone).add({ weeks: 2 });

  let name = $state('');
  let destination = $state('');
  let start = $state(fortnight);
  let end = $state(fortnight.add({ days: 6 }));
  let busy = $state(false);

  const days = $derived(daysBetween(start, end) + 1);
  const ready = $derived(name.trim() !== '' || destination.trim() !== '');

  function moveStart(picked: LocalDate) {
    // Dragging the first day past the last drags the trip with it rather
    // than producing a negative-length itinerary.
    const span = Math.max(0, daysBetween(start, end));
    start = picked;
    if (daysBetween(picked, end) < 0) end = picked.add({ days: span });
  }

  async function create() {
    if (!ready || busy) return;
    busy = true;
    const id = await trips.createTrip(
      name.trim() || destination.trim() || 'Untitled trip',
      destination.trim(),
      startOfDay(start, zone).epochMilliseconds,
      zonedOf(end, localTime(23, 59), zone).epochMilliseconds,
      zone
    );
    ui.close();
    ui.go({ name: 'trip', tripId: id });
  }
</script>

<ModalHead eyebrow={days === 1 ? 'One day' : `${days} days`} title="New itinerary" />
<form
  class="modal-form"
  onsubmit={(ev) => {
    ev.preventDefault();
    void create();
  }}
>
  <Field label="Name" id="trip-name" placeholder="London & Paris" bind:value={name} />
  <Field
    label="Destinations"
    id="trip-destinations"
    placeholder="London, then Paris"
    bind:value={destination}
  />
  <div class="field-row">
    <DateField label="First day" id="trip-start" value={start} onchange={moveStart} />
    <DateField label="Last day" id="trip-end" value={end} min={start} onchange={(d) => (end = d)} />
  </div>
  <div class="btn-row">
    <button type="button" class="btn btn-muted" onclick={() => ui.close()}>Cancel</button>
    <button type="submit" class="btn btn-primary" disabled={!ready || busy}
      ><Icon name="check" /> Create</button
    >
  </div>
</form>
