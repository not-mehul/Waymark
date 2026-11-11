<script lang="ts">
  // Who is travelling, what each of them is actually booked on, and the
  // windows where the party is not together.
  import { coverage, splitMinutes, type SplitWindow } from '../../domain/party-split';
  import {
    displayName,
    initials,
    partyById,
    segmentStart,
    segmentTitle,
    type Segment
  } from '../../domain/model';
  import { zonedAt } from '../../domain/time';
  import { clock, dayCompact, duration } from '../../domain/time-text';
  import { profileSummary } from '../../state/shelf';
  import Icon from '../ui/Icon.svelte';
  import NoticeBanner from '../ui/NoticeBanner.svelte';
  import SectionHeader from '../ui/SectionHeader.svelte';
  import { useTrip } from './context';

  const { model, open } = useTrip();
  const entries = $derived(model.dossier ? coverage(model.dossier) : []);

  /**
   * Split windows are instants; show each end on the clock of the leg that
   * starts or ends there (a train out of London reads London time, its
   * arrival Paris time), not of whatever hotel stay happens to cover it.
   */
  function zoneAt(millis: number): string {
    const d = model.dossier;
    const legs = d?.segments.filter((s) => s.kind !== 'LODGING') ?? [];
    return (
      legs.find((s) => s.startEpochMillis === millis)?.startZoneId ??
      legs.find((s) => s.endEpochMillis === millis)?.endZoneId ??
      d?.segments.find((s) => millis >= s.startEpochMillis && millis <= s.endEpochMillis)?.startZoneId ??
      d?.trip.homeZoneId ??
      'UTC'
    );
  }
  /** Two trains on the same route need their day and time to tell apart. */
  const missedLabel = (s: Segment) => {
    const at = segmentStart(s);
    return `${segmentTitle(s)} · ${dayCompact(at.toPlainDate())} ${clock(at)}`;
  };
  const span = (w: SplitWindow) =>
    `${clock(zonedAt(w.startMillis, zoneAt(w.startMillis)))} – ${clock(zonedAt(w.endMillis, zoneAt(w.endMillis)))}`;
  const plural = (n: number, word: string) => `${n} ${word}${n === 1 ? '' : 's'}`;
</script>

<div class="tab-body">
  {#each model.partyNotes as note (note)}
    <NoticeBanner headline={note} />
  {/each}

  <SectionHeader label="Travelers" />
  {#each entries as entry (entry.traveler.id)}
    {@const t = entry.traveler}
    {@const summary = profileSummary(t)}
    {@const dim = model.travelerFilter !== null && model.travelerFilter !== t.id}
    <div class="panel traveler-card">
      <div class="person-row">
        <button
          type="button"
          class="traveler-filter"
          aria-label="Show only {displayName(t)}'s bookings"
          onclick={() => {
            model.filterBy(t.id);
            model.tab = 'TIMELINE';
          }}
        >
          <span class="party-mark large" class:dim>{initials(t)}</span>
          <span class="t-card-title">{t.fullName}</span>
        </button>
        <button
          type="button"
          class="btn-icon"
          aria-label="Edit {displayName(t)}"
          onclick={() => open({ name: 'editTraveler', traveler: t })}><Icon name="edit" /></button
        >
        <button
          type="button"
          class="btn-icon"
          aria-label="Remove {displayName(t)}"
          onclick={() => open({ name: 'removeTraveler', traveler: t })}><Icon name="trash" /></button
        >
      </div>
      <p class="t-data-small">
        {plural(entry.flightCount, 'flight')} · {plural(entry.segmentCount, 'booking')}{entry.hasLodging
          ? ''
          : ' · no lodging'}
      </p>
      {#if summary}<p class="t-hint">{summary}</p>{/if}
      {#if entry.missingFromSegments.length > 0}
        <div class="facts">
          <div>
            <span class="t-field-label">Not travelling on</span>
            <span class="t-hint faint">{entry.missingFromSegments.map(missedLabel).join(', ')}</span>
          </div>
        </div>
      {/if}
    </div>
  {/each}

  <button type="button" class="btn btn-primary btn-block" onclick={() => open({ name: 'addTraveler' })}>
    <Icon name="users" /> Add traveler
  </button>

  <SectionHeader label="Apart" />
  {#if model.splits.length === 0}
    <div class="panel faint"><p class="t-hint">The party stays together for the whole trip.</p></div>
  {:else}
    {#each model.splits as window (window.startMillis)}
      <div class="panel split">
        <div class="split-head">
          <span class="t-data">{span(window)}</span>
          <span class="t-data-small accent">{duration(splitMinutes(window))}</span>
        </div>
        {#each window.groups as group (group.segmentId)}
          <div class="person-row">
            <span class="tl-marks">
              {#each group.travelerIds as id (id)}
                {@const who = model.dossier ? partyById(model.dossier.party, id) : null}
                {#if who}<span class="party-mark small">{initials(who)}</span>{/if}
              {/each}
            </span>
            <span class="t-body-small muted">{group.label}</span>
          </div>
        {/each}
        {#if window.unaccounted.length > 0}
          <!-- Nothing booked is usually somebody at the hotel, not somebody lost. -->
          <p class="t-hint">
            Nothing booked: {window.unaccounted
              .map((id) => (model.dossier ? partyById(model.dossier.party, id) : null))
              .filter((t) => t !== null)
              .map(displayName)
              .join(', ')}
          </p>
        {/if}
      </div>
    {/each}
  {/if}
</div>
