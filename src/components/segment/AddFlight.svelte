<script lang="ts">
  // A flight, typed in. Nothing fills itself in from a schedule; what the app
  // contributes is what an airport code already implies, coordinates and a
  // zone, which is enough to work out that a 22:20 departure lands the next
  // afternoon. Reading a boarding pass from a picture arrives in phase 4.
  import { findAirport, searchAirports, type Airport } from '../../catalog/airports';
  import { DAY_OFFSETS, legDayOffset, legMinutes } from '../../domain/leg-times';
  import { displayName, initials } from '../../domain/model';
  import { systemZone, todayIn } from '../../domain/time';
  import { dayOffsetSuffix, duration } from '../../domain/time-text';
  import { readBookingText } from '../../share/booking-text';
  import {
    canSave,
    defaultFlightDate,
    entryLeg,
    flightOf,
    newFlightEntry,
    prefill,
    stationHint,
    timeProblem,
    type FlightEntry
  } from '../../state/add-flight';
  import { ui } from '../../state/ui.svelte';
  import DateField from '../ui/DateField.svelte';
  import Field from '../ui/Field.svelte';
  import Icon from '../ui/Icon.svelte';
  import NoticeBanner from '../ui/NoticeBanner.svelte';
  import SectionHeader from '../ui/SectionHeader.svelte';
  import TimeField from '../ui/TimeField.svelte';
  import { useTrip } from '../trip/context';

  const { model } = useTrip();
  const party = model.party;
  const today = todayIn(systemZone());

  let entry = $state.raw<FlightEntry>(
    newFlightEntry(
      model.dossier ? defaultFlightDate(model.dossier.trip, today) : today,
      party.map((t) => t.id)
    )
  );
  const set = (changes: Partial<FlightEntry>) => (entry = { ...entry, ...changes });

  let showDetail = $state(false);
  let notice = $state<string | null>(null);
  let saving = $state(false);

  const leg = $derived(entryLeg(entry));
  const problem = $derived(timeProblem(entry));
  const ready = $derived(canSave(entry) && !saving);
  const onBoard = $derived(party.filter((t) => entry.selectedTravelers.includes(t.id)));

  // Station suggestions a beat after typing stops: the directory is nine
  // thousand rows. Once the code resolves exactly there is nothing to suggest.
  function suggestionsFor(text: string): Airport[] {
    const typed = text.trim();
    if (typed.length < 2 || (typed.length === 3 && findAirport(typed))) return [];
    return searchAirports(typed, 4);
  }
  let originSuggestions = $state.raw<Airport[]>([]);
  let destinationSuggestions = $state.raw<Airport[]>([]);
  $effect(() => {
    const text = entry.origin;
    const timer = setTimeout(() => (originSuggestions = suggestionsFor(text)), 150);
    return () => clearTimeout(timer);
  });
  $effect(() => {
    const text = entry.destination;
    const timer = setTimeout(() => (destinationSuggestions = suggestionsFor(text)), 150);
    return () => clearTimeout(timer);
  });

  async function pasteEmail() {
    let text: string;
    try {
      text = await navigator.clipboard.readText();
    } catch {
      notice = 'Waymark was not allowed to read the clipboard. Paste the flight number in by hand.';
      return;
    }
    const reading = readBookingText(text);
    const found = reading.flights[0];
    if (!found) {
      notice =
        'Nothing on the clipboard reads as a flight. Copy the part of the email with the flight number in it.';
      return;
    }
    entry = prefill(entry, {
      designator: found.designator,
      date: found.date,
      origin: found.origin,
      destination: found.destination,
      departs: found.departs,
      arrives: found.arrives,
      confirmationCode: reading.reference
    });
    notice =
      'Filled in from the clipboard — check it before saving.' +
      (reading.flights.length > 1
        ? ` It mentions ${reading.flights.length} flights; this is the first.`
        : '');
  }

  function toggle(id: string) {
    set({
      selectedTravelers: entry.selectedTravelers.includes(id)
        ? entry.selectedTravelers.filter((t) => t !== id)
        : [...entry.selectedTravelers, id]
    });
  }

  /** Tapping the inferred day again hands the choice back to the inference. */
  function setDayOffset(days: number) {
    set({ arrivalDayOffset: entry.arrivalDayOffset === days ? null : days });
  }

  async function save() {
    const flight = flightOf(
      entry,
      model.tripId,
      party.map((t) => t.id)
    );
    if (!flight || saving) return;
    saving = true;
    await model.saveSegment(flight);
    ui.back();
  }
</script>

{#snippet station(
  label: string,
  id: string,
  placeholder: string,
  value: string,
  suggestions: Airport[],
  onset: (code: string) => void
)}
  {@const resolved = findAirport(value)}
  <Field
    {label}
    {id}
    {placeholder}
    mono
    hint={resolved ? stationHint(resolved) : undefined}
    bind:value={() => value, (v) => onset(v.toUpperCase())}
  />
  {#if suggestions.length > 0}
    <div class="chip-row" role="group" aria-label="{label}: stations">
      {#each suggestions as airport (airport.code)}
        <button type="button" class="chip" aria-pressed="false" onclick={() => onset(airport.code)}>
          {airport.code} · {airport.city}
        </button>
      {/each}
    </div>
  {:else if value.trim().length >= 3 && !resolved}
    <p class="t-hint">No station called “{value}” in the bundled list.</p>
  {/if}
{/snippet}

<section class="screen" aria-labelledby="add-flight-title">
  <header class="screen-head">
    <button type="button" class="btn-icon" aria-label="Back" onclick={() => ui.back()}>
      <Icon name="arrowLeft" />
    </button>
    <h1 class="t-card-title" id="add-flight-title">Add flight</h1>
  </header>

  <div class="chip-row">
    <button type="button" class="chip with-mark" aria-pressed="false" onclick={pasteEmail}>
      <Icon name="ticket" /> Paste an email
    </button>
  </div>
  {#if notice}
    <NoticeBanner headline={notice} ondismiss={() => (notice = null)} />
  {/if}

  <form
    class="modal-form"
    onsubmit={(ev) => {
      ev.preventDefault();
      void save();
    }}
  >
    <div class="field-row">
      <Field
        label="Flight"
        id="flight-designator"
        placeholder="BA286"
        mono
        bind:value={() => entry.designator, (v) => set({ designator: v.toUpperCase() })}
      />
      <DateField label="Date" id="flight-date" value={entry.date} onchange={(d) => set({ date: d })} />
    </div>
    {@render station('From', 'flight-from', 'SFO', entry.origin, originSuggestions, (v) =>
      set({ origin: v })
    )}
    {@render station('To', 'flight-to', 'LHR', entry.destination, destinationSuggestions, (v) =>
      set({ destination: v })
    )}
    <div class="field-row">
      <TimeField
        label="Departs"
        id="flight-departs"
        value={entry.departTime}
        onchange={(t) => set({ departTime: t })}
      />
      <TimeField
        label="Arrives"
        id="flight-arrives"
        value={entry.arriveTime}
        onchange={(t) => set({ arriveTime: t })}
      />
    </div>
    {#if leg}
      {@const lands = dayOffsetSuffix(leg.departure, leg.arrival)}
      <p class="t-data block-time">{duration(legMinutes(leg))} in the air{lands ? `, lands ${lands}` : ''}</p>
    {/if}
    {#if problem}<p class="t-hint form-problem">{problem}</p>{/if}

    <!-- The arrival day is worked out from the two clocks, and can be said outright. -->
    {#if entry.departTime && entry.arriveTime}
      {@const shown = entry.arrivalDayOffset ?? (leg ? legDayOffset(leg) : null)}
      <div class="field">
        <span class="t-field-label">Lands</span>
        <div class="chip-row" role="group" aria-label="Lands">
          {#each DAY_OFFSETS as offset (offset)}
            <button
              type="button"
              class="chip"
              aria-pressed={shown === offset}
              onclick={() => setDayOffset(offset)}
            >
              {offset === 0 ? 'Same day' : offset > 0 ? `+${offset}` : `−${-offset}`}
            </button>
          {/each}
        </div>
      </div>
    {/if}

    <!-- Everything past this point is optional, and folded away until wanted. -->
    <div class="section-toggle">
      <SectionHeader label="Detail" />
      <button type="button" class="link-button" onclick={() => (showDetail = !showDetail)}>
        {showDetail ? 'Hide' : 'Show'}
      </button>
    </div>
    {#if showDetail}
      <div class="field-row">
        <Field
          label="Terminal out"
          id="flight-terminal-out"
          placeholder="I"
          mono
          bind:value={() => entry.departureTerminal, (v) => set({ departureTerminal: v })}
        />
        <Field
          label="Terminal in"
          id="flight-terminal-in"
          placeholder="5"
          mono
          bind:value={() => entry.arrivalTerminal, (v) => set({ arrivalTerminal: v })}
        />
      </div>
      <div class="field-row">
        <Field
          label="Aircraft"
          id="flight-aircraft"
          placeholder="Boeing 777"
          bind:value={() => entry.aircraft, (v) => set({ aircraft: v })}
        />
        <Field
          label="Cabin"
          id="flight-cabin"
          placeholder="Economy"
          bind:value={() => entry.cabin, (v) => set({ cabin: v })}
        />
      </div>
      <Field
        label="Operated by"
        id="flight-operated"
        placeholder="Only if another airline flies it"
        bind:value={() => entry.operatedBy, (v) => set({ operatedBy: v })}
      />
      <Field
        label="Record locator"
        id="flight-locator"
        placeholder="K7QH2P"
        mono
        bind:value={() => entry.confirmationCode, (v) => set({ confirmationCode: v })}
      />
      {#each onBoard as traveler (traveler.id)}
        <div class="field-row per-person">
          <Field
            label="{displayName(traveler)} · e-ticket"
            id="flight-ticket-{traveler.id}"
            placeholder="E-ticket"
            mono
            bind:value={
              () => entry.eTickets[traveler.id] ?? '',
              (v) => set({ eTickets: { ...entry.eTickets, [traveler.id]: v } })
            }
          />
          <Field
            label="Seat"
            id="flight-seat-{traveler.id}"
            placeholder="21A"
            mono
            bind:value={
              () => entry.seats[traveler.id] ?? '',
              (v) => set({ seats: { ...entry.seats, [traveler.id]: v.toUpperCase() } })
            }
          />
        </div>
      {/each}
    {/if}

    {#if party.length > 1}
      <SectionHeader label="Who is on it" />
      <div class="chip-row wrap" role="group" aria-label="Who is on it">
        {#each party as traveler (traveler.id)}
          <button
            type="button"
            class="chip with-mark"
            aria-pressed={entry.selectedTravelers.includes(traveler.id)}
            onclick={() => toggle(traveler.id)}
          >
            <span class="party-mark tiny">{initials(traveler)}</span>{displayName(traveler)}
          </button>
        {/each}
      </div>
    {/if}

    <button type="submit" class="btn btn-primary btn-block" disabled={!ready}>
      <Icon name="check" /> Add to itinerary
    </button>
  </form>
</section>
