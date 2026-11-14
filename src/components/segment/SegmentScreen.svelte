<script lang="ts">
  // One booking in full, and read only until Edit: a booking is a record of
  // something already arranged, so a stray tap never changes it.
  import { countryCurrency, countryOf, countryPower } from '../../catalog/countries';
  import { RISK_LABEL, slackMinutes } from '../../domain/connection-risk';
  import { distanceKm, formatDistance } from '../../domain/geo';
  import {
    designator,
    destinationOf,
    displayName,
    groundModeLabel,
    hasCoordinates,
    initials,
    nights,
    segmentEnd,
    segmentStart,
    segmentTitle,
    type Segment,
    type Traveler
  } from '../../domain/model';
  import { timelineLinks } from '../../domain/timeline';
  import { clock, dayCompact, duration, durationBetween, zoneLabel, zoneShift } from '../../domain/time-text';
  import { totalMinutes, transitOptions } from '../../domain/transit';
  import { copyText } from '../../state/actions';
  import { ui } from '../../state/ui.svelte';
  import NoticeBanner from '../ui/NoticeBanner.svelte';
  import Icon from '../ui/Icon.svelte';
  import SectionHeader from '../ui/SectionHeader.svelte';
  import { useTrip } from '../trip/context';
  import EditSegment from './EditSegment.svelte';
  import Documents from '../docs/Documents.svelte';

  let { segmentId }: { segmentId: string } = $props();
  const { model, open } = useTrip();

  const segment = $derived(model.segment(segmentId));
  const party = $derived(model.party);
  let editing = $state(false);

  /** Back to the timeline, and the booking goes at once with Undo in the toast. */
  async function remove(s: Segment) {
    ui.go({ name: 'trip', tripId: model.tripId });
    await model.deleteSegment(s.id);
  }

  // The reference just copied wears a tick for a moment, where the finger was.
  let copied = $state<string | null>(null);
  let copiedTimer: ReturnType<typeof setTimeout> | undefined;
  async function copy(value: string, what: string) {
    if (!(await copyText(value, what))) return;
    copied = value;
    clearTimeout(copiedTimer);
    copiedTimer = setTimeout(() => (copied = null), 1600);
  }

  /** What the two ends of a booking are called: a hotel is checked into, not departed. */
  function endLabels(s: Segment): [string, string] {
    switch (s.kind) {
      case 'LODGING':
        return ['Check-in', 'Check-out'];
      case 'EXPERIENCE':
        return ['Starts', 'Ends'];
      default:
        return ['Departs', 'Arrives'];
    }
  }

  function specifics(s: Segment, travelers: readonly Traveler[]): [string, string][] {
    const rows: [string, string | null][] = [];
    switch (s.kind) {
      case 'FLIGHT':
        rows.push(
          ['Flight', designator(s)],
          ['Operated by', s.operatedBy],
          ['Aircraft', s.aircraft],
          ['Cabin', s.cabin],
          ['Departure terminal', s.departureTerminal],
          ['Departure gate', s.departureGate],
          ['Arrival terminal', s.arrivalTerminal],
          ['Arrival gate', s.arrivalGate]
        );
        for (const [id, seat] of Object.entries(s.seats)) {
          const who = travelers.find((t) => t.id === id);
          rows.push([who ? `${displayName(who)}'s seat` : 'Seat', seat]);
        }
        break;
      case 'LODGING':
        rows.push(['Room', s.roomDescription], ['Address', s.origin.address], ['Arriving', s.checkInNote]);
        break;
      case 'GROUND':
        rows.push(
          ['Mode', groundModeLabel(s.mode)],
          ['Operator', s.provider],
          ['Pick-up', s.pickupInstruction]
        );
        break;
      case 'EXPERIENCE':
        rows.push(['Category', s.category], ['Address', s.origin.address], ['Tips', s.curatedBy]);
        break;
    }
    return rows.filter((r): r is [string, string] => r[1] !== null && r[1] !== '');
  }

  // How the traveler gets here from whatever came before: each traveler's
  // own previous booking, and nothing at all where the party disagrees.
  const link = $derived(
    model.dossier && segment
      ? (timelineLinks(model.dossier.segments, model.dossier).find((l) => l.toSegment.id === segment.id) ??
          null)
      : null
  );
</script>

{#if editing && segment}
  <EditSegment
    original={segment}
    {party}
    oncancel={() => (editing = false)}
    onsaved={() => (editing = false)}
  />
{:else}
  <section class="screen" aria-labelledby="segment-title">
    <header class="screen-head">
      <button type="button" class="btn-icon" aria-label="Back" onclick={() => ui.back()}>
        <Icon name="arrowLeft" />
      </button>
      <h1 class="t-card-title ellipsis" id="segment-title">{segment ? segmentTitle(segment) : 'Booking'}</h1>
      {#if segment}
        {@const current = segment}
        <button type="button" class="btn-icon" aria-label="Edit booking" onclick={() => (editing = true)}>
          <Icon name="edit" />
        </button>
        <button type="button" class="btn-icon" aria-label="Remove booking" onclick={() => remove(current)}>
          <Icon name="trash" />
        </button>
      {/if}
    </header>

    {#if !segment}
      <p class="t-hint empty-line">This booking has been removed.</p>
    {:else}
      {@const start = segmentStart(segment)}
      {@const end = segmentEnd(segment)}
      {@const destination = destinationOf(segment)}
      {@const shift = zoneShift(segment.startZoneId, segment.endZoneId, segment.startEpochMillis)}
      <div class="panel">
        <div class="ends">
          <div>
            <span class="t-field-label">{endLabels(segment)[0]}</span>
            <span class="t-stat">{clock(start)}</span>
            <span class="t-data-small">{dayCompact(start.toPlainDate())} · {zoneLabel(start)}</span>
            <span class="t-body-small muted">{segment.origin.name}</span>
          </div>
          <div class="end-right">
            <span class="t-field-label">{endLabels(segment)[1]}</span>
            <span class="t-stat">{clock(end)}</span>
            <span class="t-data-small">{dayCompact(end.toPlainDate())} · {zoneLabel(end)}</span>
            <span class="t-body-small muted">{destination.name}</span>
          </div>
        </div>
        <hr class="hairline" />
        <div class="stats">
          <div>
            {#if segment.kind === 'LODGING'}
              {@const n = nights(segment)}
              <span class="t-stat">{n}</span>
              <span class="t-field-label">{n === 1 ? 'Night' : 'Nights'}</span>
            {:else}
              <span class="t-stat">{durationBetween(segment.startEpochMillis, segment.endEpochMillis)}</span>
              <span class="t-field-label">Duration</span>
            {/if}
          </div>
          {#if hasCoordinates(segment.origin) && hasCoordinates(destination) && segment.origin.name !== destination.name}
            <div>
              <span class="t-stat">{formatDistance(distanceKm(segment.origin, destination))}</span>
              <span class="t-field-label">Distance</span>
            </div>
          {/if}
          {#if shift}
            <div>
              <span class="t-stat accent">{shift}</span>
              <span class="t-field-label">Clock shift</span>
            </div>
          {/if}
        </div>
      </div>

      {@const rows = specifics(segment, party)}
      {#if rows.length > 0 || segment.note}
        <div class="panel facts">
          {#each rows as [label, value] (label)}
            <div>
              <span class="t-field-label">{label}</span>
              <span class="t-body-small">{value}</span>
            </div>
          {/each}
          {#if segment.note}<p class="t-hint">{segment.note}</p>{/if}
        </div>
      {/if}

      {#if party.length > 1}
        {@const ids = segment.travelerIds}
        {@const travelling = party.filter((t) => ids.length === 0 || ids.includes(t.id))}
        <SectionHeader label="Travelling" />
        <div class="person-row">
          <span class="tl-marks">
            {#each travelling as t (t.id)}<span class="party-mark small">{initials(t)}</span>{/each}
          </span>
          <span class="t-body-small">
            {travelling.length === party.length ? 'Everyone' : travelling.map(displayName).join(', ')}
          </span>
        </div>
      {/if}

      <!-- The reference, in the open: the one thing a person at a desk needs. -->
      {@const tickets =
        segment.kind === 'FLIGHT'
          ? Object.entries(segment.ticketNumbers).filter(([id]) => party.some((t) => t.id === id))
          : []}
      {#if segment.confirmationCode || tickets.length > 0}
        <SectionHeader label="Reference" />
        <div class="panel facts">
          {#if segment.confirmationCode}
            {@const code = segment.confirmationCode}
            {@const label = segment.bookedWith || 'Confirmation'}
            <div class="ref-row">
              <button
                type="button"
                class="ref-copy"
                aria-label="Copy {label} {code}"
                onclick={() => copy(code, label)}
              >
                <span class="t-field-label ref-label" class:copied={copied === code}>
                  {#if copied === code}<Icon name="check" /> Copied{:else}{label}{/if}
                </span>
                <span class="t-data-large">{code}</span>
              </button>
              <button
                type="button"
                class="btn-icon"
                aria-label="Show {code} large"
                onclick={() => open({ name: 'reference', label, code })}
              >
                <Icon name="maximize" />
              </button>
            </div>
          {/if}
          {#each tickets as [travelerId, number] (travelerId)}
            {@const owner = party.find((t) => t.id === travelerId)}
            {@const label = `${owner ? displayName(owner) : 'Ticket'} · ticket`}
            <div class="ref-row">
              <button
                type="button"
                class="ref-copy"
                aria-label="Copy {label} {number}"
                onclick={() => copy(number, 'Ticket number')}
              >
                <span class="t-field-label ref-label" class:copied={copied === number}>
                  {#if copied === number}<Icon name="check" /> Copied{:else}{label}{/if}
                </span>
                <span class="t-data">{number}</span>
              </button>
              <button
                type="button"
                class="btn-icon"
                aria-label="Show {number} large"
                onclick={() => open({ name: 'reference', label, code: number })}
              >
                <Icon name="maximize" />
              </button>
            </div>
          {/each}
          <span class="t-hint">Tap a reference to copy it, or the corner button to show it large.</span>
        </div>
      {/if}

      <Documents segmentId={segment.id} />

      {#if link}
        <SectionHeader label="Getting here" />
        {#if link.verdict}
          {@const verdict = link.verdict}
          <NoticeBanner
            icon="clock"
            headline="{RISK_LABEL[verdict.level]} · {duration(verdict.availableMinutes)} from {segmentTitle(
              link.fromSegment
            )}"
            detail="{verdict.reason} · needs {duration(verdict.requiredMinutes)}"
            critical={slackMinutes(verdict) < 0}
          />
          {#if link.estimate}
            <div class="panel faint facts">
              <span class="t-section-label">Ways across</span>
              {#each transitOptions(destinationOf(link.fromSegment), segment.origin) as option (option.mode)}
                <div class="way">
                  <span class="t-body-small muted">{groundModeLabel(option.mode)}</span>
                  <span class="t-data-small"
                    >{duration(totalMinutes(option))} · {formatDistance(option.distanceKm)}</span
                  >
                </div>
              {/each}
            </div>
          {/if}
        {:else}
          <NoticeBanner
            icon="coffee"
            headline="{duration(link.gapMinutes)} free before this"
            detail="After {segmentTitle(link.fromSegment)}, nothing booked and nowhere else to be."
          />
        {/if}
      {/if}

      {@const country = countryOf(destination)}
      {#if country}
        <p class="t-hint">
          {country.name}: {countryCurrency(country)} · {countryPower(country)} · emergency {country.emergencyNumber}
        </p>
      {/if}
    {/if}
  </section>
{/if}
