<script lang="ts">
  // The single column the whole app exists to produce: days in order, the
  // time in the left gutter in mono, and the awkward gaps between bookings
  // named rather than left blank.
  import { RISK_LABEL } from '../../domain/connection-risk';
  import {
    designator,
    displayName,
    groundModeLabel,
    initials,
    nights,
    shortLabel,
    type Segment
  } from '../../domain/model';
  import type { EventRole, TimelineEvent, TimelineLink } from '../../domain/timeline';
  import { clock, duration, durationBetween } from '../../domain/time-text';
  import { transitSummary } from '../../domain/transit';
  import type { IconName } from '../../util/icons';
  import Icon from '../ui/Icon.svelte';
  import { useTrip } from './context';

  let { onopen, onaddflight }: { onopen: (segmentId: string) => void; onaddflight: () => void } = $props();
  const { model, open } = useTrip();

  const party = $derived(model.party);
  const initialsById = $derived(new Map(party.map((t) => [t.id, initials(t)])));

  function iconFor(s: Segment): IconName {
    switch (s.kind) {
      case 'FLIGHT':
        return 'plane';
      case 'LODGING':
        return 'bed';
      case 'GROUND':
        return s.mode === 'TRAIN' || s.mode === 'TRANSIT' ? 'train' : 'car';
      case 'EXPERIENCE':
        return 'compass';
    }
  }

  function headline(s: Segment, role: EventRole): string {
    switch (s.kind) {
      case 'FLIGHT':
      case 'GROUND':
        return `${shortLabel(s.origin)} → ${shortLabel(s.destination)}`;
      case 'LODGING':
        return role === 'CHECK_OUT' ? `Check out · ${s.propertyName}` : s.propertyName;
      case 'EXPERIENCE':
        return s.name;
    }
  }

  function subtitle(s: Segment, role: EventRole): string {
    switch (s.kind) {
      case 'FLIGHT':
        return `${designator(s)} · ${s.operatedBy ?? s.carrierCode}`;
      case 'LODGING':
        return role === 'CHECK_OUT' ? s.origin.city || s.origin.name : (s.origin.address ?? s.origin.city);
      case 'GROUND':
        return groundModeLabel(s.mode);
      case 'EXPERIENCE':
        return s.origin.address ?? (s.origin.city || s.category);
    }
  }

  function details(s: Segment): string {
    const parts: (string | null)[] = [];
    switch (s.kind) {
      case 'FLIGHT':
        parts.push(
          s.departureTerminal && `T${s.departureTerminal}`,
          s.departureGate && `Gate ${s.departureGate}`,
          durationBetween(s.startEpochMillis, s.endEpochMillis),
          s.aircraft
        );
        break;
      case 'LODGING': {
        const n = nights(s);
        parts.push(`${n} night${n === 1 ? '' : 's'}`, s.roomDescription);
        break;
      }
      case 'GROUND':
        parts.push(durationBetween(s.startEpochMillis, s.endEpochMillis), s.provider);
        break;
      case 'EXPERIENCE':
        parts.push(s.category, durationBetween(s.startEpochMillis, s.endEpochMillis));
        break;
    }
    return parts.filter((p): p is string => !!p).join('  ·  ');
  }

  const LINK_ICON: Record<TimelineLink['nature'], IconName> = {
    CONNECTION: 'split',
    TRANSFER: 'car',
    FREE_TIME: 'coffee'
  };
  const LINK_LABEL: Record<TimelineLink['nature'], string> = {
    CONNECTION: 'Connection',
    TRANSFER: 'Transfer',
    FREE_TIME: 'Free'
  };
  function linkTone(link: TimelineLink): string {
    const level = link.verdict?.level;
    return level === 'BROKEN' || level === 'AT_RISK' ? 'danger' : level === 'TIGHT' ? 'tight' : '';
  }

  /** Marks only when the booking is for some of the party, not all of it. */
  function marks(entry: TimelineEvent): string[] {
    const ids = entry.segment.travelerIds;
    if (ids.length === 0 || ids.length >= initialsById.size) return [];
    return ids.map((id) => initialsById.get(id)).filter((m): m is string => m !== undefined);
  }

  const filtered = $derived(party.find((t) => t.id === model.travelerFilter) ?? null);
  const nextInParty = $derived.by(() => {
    if (!filtered) return null;
    const i = party.findIndex((t) => t.id === filtered.id);
    return party[i + 1] ?? null;
  });
</script>

<div class="timeline">
  <!-- Nothing in the ordinary case; filtered, a plain statement of what is hidden. -->
  {#if party.length >= 2}
    {#if filtered}
      <div class="filter-bar">
        <span class="t-body-small">Showing {displayName(filtered)} only</span>
        {#if nextInParty}
          {@const next = nextInParty}
          <button type="button" class="link-button" onclick={() => model.filterBy(next.id)}>Next</button>
        {/if}
        <button
          type="button"
          class="btn-icon"
          aria-label="Show everyone"
          onclick={() => model.filterBy(null)}
        >
          <Icon name="x" />
        </button>
      </div>
    {:else}
      <button type="button" class="filter-hint t-hint" onclick={() => model.filterBy(party[0]!.id)}>
        Showing everyone
      </button>
    {/if}
  {/if}

  {#if model.dossier && model.timeline.length === 0}
    <p class="t-hint empty-line">Nothing booked yet.</p>
  {/if}

  {#each model.timeline as entry, index (entry.key)}
    <div class="tl-entry" style:--order={Math.min(index, 12)}>
      {#if entry.type === 'day'}
        <div class="tl-day">
          <h2 class="t-section-heading">{entry.headline}</h2>
          <span class="tl-rule"></span>
          <span class="t-field-label">{entry.zoneLabel}</span>
        </div>
      {:else if entry.type === 'now'}
        <div class="tl-now">
          <span class="t-field-label tl-gutter">Now</span>
          <span class="tl-rule"></span>
          <span class="t-data-small">{entry.label}</span>
        </div>
      {:else if entry.type === 'link'}
        {@const fits = model.freeTime.get(entry.key) ?? []}
        {@const detail = entry.verdict?.reason ?? (entry.estimate ? transitSummary(entry.estimate) : null)}
        <div class="tl-link {linkTone(entry)}">
          <span class="tl-gutter"><span class="tl-thread"></span></span>
          <div class="tl-link-body">
            <div class="tl-link-head">
              <Icon name={LINK_ICON[entry.nature]} />
              <span class="t-data-small">{LINK_LABEL[entry.nature]} · {duration(entry.gapMinutes)}</span>
              {#if entry.verdict}
                <span class="t-field-label">{RISK_LABEL[entry.verdict.level].toLowerCase()}</span>
              {/if}
            </div>
            {#if detail}<span class="t-hint">{detail}</span>{/if}
            {#if fits.length > 0}
              <span class="t-hint tl-fits">Would fit: {fits.map((i) => i.title).join(' · ')}</span>
            {/if}
          </div>
        </div>
      {:else}
        {@const segment = entry.segment}
        <div class="tl-event" class:past={entry.state === 'PAST'} class:active={entry.state === 'ACTIVE'}>
          <div class="tl-gutter">
            <span class="t-data">{clock(entry.localTime)}</span>
            {#if entry.arrivalDayOffset}<span class="t-field-label">{entry.arrivalDayOffset}</span>{/if}
          </div>
          <span class="tl-mark"><Icon name={iconFor(segment)} /></span>
          <button type="button" class="panel tl-card" onclick={() => onopen(segment.id)}>
            <span class="tl-card-top">
              <span class="tl-card-text">
                <span class="t-card-title">{headline(segment, entry.role)}</span>
                <span class="t-hint">{subtitle(segment, entry.role)}</span>
              </span>
              {#if marks(entry).length > 0}
                <span class="tl-marks">
                  {#each marks(entry) as mark, i (i)}<span class="party-mark small">{mark}</span>{/each}
                </span>
              {/if}
            </span>
            {#if details(segment)}
              <span class="t-data-small ellipsis tl-details">{details(segment)}</span>
            {/if}
          </button>
        </div>
      {/if}
    </div>
  {/each}

  <div class="btn-row timeline-actions">
    <button type="button" class="btn btn-secondary" onclick={onaddflight}
      ><Icon name="plane" /> Add flight</button
    >
    <button type="button" class="btn btn-secondary" onclick={() => open({ name: 'addPlan' })}>
      <Icon name="plus" /> Add plan
    </button>
  </div>
</div>
