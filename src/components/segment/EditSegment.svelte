<script lang="ts">
  // The editing half of a booking: every field, held in one form and written
  // back once, on Save. Cancel discards. Changing an airport or a place moves
  // the booking into that place's zone, with the clock times kept as typed.
  import {
    GROUND_MODES,
    groundModeLabel,
    displayName,
    initials,
    segmentEnd,
    segmentStart,
    type Segment,
    type Traveler
  } from '../../domain/model';
  import { durationBetween, zoneLabel } from '../../domain/time-text';
  import { applyForm, describeProblem, formOf } from '../../state/segment-form';
  import DateField from '../ui/DateField.svelte';
  import Field from '../ui/Field.svelte';
  import Icon from '../ui/Icon.svelte';
  import SectionHeader from '../ui/SectionHeader.svelte';
  import TimeField from '../ui/TimeField.svelte';
  import { useTrip } from '../trip/context';

  interface Props {
    original: Segment;
    party: Traveler[];
    oncancel: () => void;
    onsaved: () => void;
  }
  let { original, party, oncancel, onsaved }: Props = $props();
  const { model } = useTrip();

  // svelte-ignore state_referenced_locally
  let form = $state(formOf(original, party));
  const outcome = $derived(applyForm(form, original, party));
  const edited = $derived('saved' in outcome ? outcome.saved : null);
  const problem = $derived('refused' in outcome ? describeProblem(outcome.refused) : null);
  const shown = $derived(edited ?? original);
  const onBoard = $derived(party.filter((t) => form.travelerIds.includes(t.id)));

  const KIND_HEADING = { FLIGHT: 'Flight', LODGING: 'Stay', GROUND: 'Getting there', EXPERIENCE: 'Booking' };
  const isStay = $derived(original.kind === 'LODGING');

  function toggle(travelerId: string) {
    form.travelerIds = form.travelerIds.includes(travelerId)
      ? form.travelerIds.filter((id) => id !== travelerId)
      : [...form.travelerIds, travelerId];
  }

  async function save() {
    if (!edited) return;
    await model.saveSegment(edited);
    onsaved();
  }
</script>

<section class="screen" aria-labelledby="edit-title">
  <header class="screen-head">
    <button type="button" class="btn-icon" aria-label="Cancel" onclick={oncancel}
      ><Icon name="arrowLeft" /></button
    >
    <h1 class="t-card-title" id="edit-title">Edit booking</h1>
  </header>

  <form
    class="modal-form"
    onsubmit={(ev) => {
      ev.preventDefault();
      void save();
    }}
  >
    <SectionHeader label={KIND_HEADING[original.kind]} />
    {#if original.kind === 'FLIGHT'}
      <Field label="Flight number" id="edit-name" placeholder="BA286" mono bind:value={form.name} />
      <div class="field-row">
        <Field label="From" id="edit-from" placeholder="SFO" mono bind:value={form.where} />
        <Field label="To" id="edit-to" placeholder="LHR" mono bind:value={form.destination} />
      </div>
    {:else if original.kind === 'GROUND'}
      <Field label="Operator" id="edit-name" placeholder="Eurostar" bind:value={form.name} />
      <Field label="From" id="edit-from" placeholder="St Pancras, London" bind:value={form.where} />
      <Field label="To" id="edit-to" placeholder="Gare du Nord, Paris" bind:value={form.destination} />
    {:else}
      <Field
        label="Name"
        id="edit-name"
        placeholder={isStay ? 'The Hoxton' : 'Louvre, timed entry'}
        bind:value={form.name}
      />
      <Field
        label="Where"
        id="edit-where"
        placeholder="Paris"
        hint="A city, or a place in one — “Holborn, London”"
        bind:value={form.where}
      />
      <Field
        label="Address"
        id="edit-address"
        placeholder="199-206 High Holborn"
        rows={2}
        bind:value={form.address}
      />
    {/if}

    <SectionHeader label={isStay ? 'Check in' : 'Starts'} />
    <div class="field-row">
      <DateField
        label="Date"
        id="edit-start-date"
        value={form.startDate}
        onchange={(d) => (form.startDate = d)}
      />
      <TimeField
        label={zoneLabel(segmentStart(shown))}
        id="edit-start-time"
        value={form.startTime}
        onchange={(t) => (form.startTime = t)}
      />
    </div>

    <SectionHeader label={isStay ? 'Check out' : 'Ends'} />
    <div class="field-row">
      <DateField label="Date" id="edit-end-date" value={form.endDate} onchange={(d) => (form.endDate = d)} />
      <TimeField
        label={zoneLabel(segmentEnd(shown))}
        id="edit-end-time"
        value={form.endTime}
        onchange={(t) => (form.endTime = t)}
      />
    </div>
    {#if edited}
      <p class="t-hint">
        {durationBetween(edited.startEpochMillis, edited.endEpochMillis)}{segmentEnd(edited)
          .toPlainDate()
          .equals(form.endDate)
          ? ''
          : ', ending the next day'}
      </p>
    {/if}

    {#if party.length > 1}
      <SectionHeader label="Who is on it" />
      <div class="chip-row wrap">
        {#each party as traveler (traveler.id)}
          <button
            type="button"
            class="chip with-mark"
            aria-pressed={form.travelerIds.includes(traveler.id)}
            onclick={() => toggle(traveler.id)}
          >
            <span class="party-mark tiny">{initials(traveler)}</span>{displayName(traveler)}
          </button>
        {/each}
      </div>
    {/if}

    {#if original.kind === 'FLIGHT'}
      <SectionHeader label="Detail" />
      <div class="field-row">
        <Field
          label="Terminal out"
          id="edit-dep-terminal"
          placeholder="I"
          mono
          bind:value={form.departureTerminal}
        />
        <Field label="Gate" id="edit-dep-gate" placeholder="A6" mono bind:value={form.departureGate} />
      </div>
      <div class="field-row">
        <Field
          label="Terminal in"
          id="edit-arr-terminal"
          placeholder="5"
          mono
          bind:value={form.arrivalTerminal}
        />
        <Field label="Gate" id="edit-arr-gate" placeholder="B32" mono bind:value={form.arrivalGate} />
      </div>
      <div class="field-row">
        <Field label="Aircraft" id="edit-aircraft" placeholder="Boeing 777" bind:value={form.aircraft} />
        <Field label="Cabin" id="edit-cabin" placeholder="Economy" bind:value={form.cabin} />
      </div>
      <Field
        label="Operated by"
        id="edit-operated"
        placeholder="Only when another airline flies it"
        bind:value={form.operatedBy}
      />
    {:else if original.kind === 'LODGING'}
      <SectionHeader label="Detail" />
      <Field label="Room" id="edit-room" placeholder="Double, courtyard side" bind:value={form.room} />
      <Field
        label="Check-in"
        id="edit-checkin"
        placeholder="Key code in the confirmation email"
        rows={2}
        bind:value={form.checkInNote}
      />
    {:else if original.kind === 'GROUND'}
      <SectionHeader label="How" />
      <div class="chip-row wrap" role="group" aria-label="How">
        {#each GROUND_MODES as mode (mode)}
          <button
            type="button"
            class="chip"
            aria-pressed={form.mode === mode}
            onclick={() => (form.mode = mode)}
          >
            {groundModeLabel(mode)}
          </button>
        {/each}
      </div>
      <Field
        label="Pick-up"
        id="edit-pickup"
        placeholder="Driver waits at arrivals with a sign"
        rows={2}
        bind:value={form.pickup}
      />
    {:else}
      <SectionHeader label="Detail" />
      <Field label="Kind" id="edit-category" placeholder="Museum, dinner, tour" bind:value={form.category} />
      <Field
        label="Tips"
        id="edit-tips"
        placeholder="Go in by the Carrousel entrance"
        rows={2}
        bind:value={form.tips}
      />
    {/if}

    <SectionHeader label="Reference" />
    <div class="field-row">
      <Field
        label="Booked with"
        id="edit-booked-with"
        placeholder="Direct booking"
        bind:value={form.bookedWith}
      />
      <Field
        label={original.kind === 'FLIGHT' ? 'Record locator' : 'Confirmation'}
        id="edit-reference"
        placeholder="K7QH2P"
        mono
        bind:value={form.confirmationCode}
      />
    </div>

    <!-- Seat and ticket number are per person, and only for the people on it. -->
    {#if original.kind === 'FLIGHT'}
      {#each onBoard as traveler (traveler.id)}
        <div class="field-row per-person">
          <Field
            label="{displayName(traveler)} · e-ticket"
            id="edit-ticket-{traveler.id}"
            placeholder="E-ticket"
            mono
            bind:value={() => form.tickets[traveler.id] ?? '', (v) => (form.tickets[traveler.id] = v)}
          />
          <Field
            label="Seat"
            id="edit-seat-{traveler.id}"
            placeholder="21A"
            mono
            bind:value={
              () => form.seats[traveler.id] ?? '', (v) => (form.seats[traveler.id] = v.toUpperCase())
            }
          />
        </div>
      {/each}
    {/if}

    <SectionHeader label="Note" />
    <Field
      label="Note"
      id="edit-note"
      placeholder="Anything worth remembering about this booking"
      rows={3}
      bind:value={form.note}
    />

    {#if problem}<p class="t-hint form-problem" role="alert">{problem}</p>{/if}

    <button type="submit" class="btn btn-primary btn-block" disabled={!edited}>
      <Icon name="check" /> Save changes
    </button>
    <button type="button" class="btn btn-muted btn-block" onclick={oncancel}>Cancel</button>
  </form>
</section>
