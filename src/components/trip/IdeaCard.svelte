<script lang="ts">
  import { displayName, initials, PRICE_BAND_LABEL, type Idea } from '../../domain/model';
  import { dayCompact, duration } from '../../domain/time-text';
  import Icon from '../ui/Icon.svelte';
  import { useTrip } from './context';
  import { IDEA_ICON } from './idea-icons';

  let { idea }: { idea: Idea } = $props();
  const { model, open } = useTrip();
  let expanded = $state(false);

  const done = $derived(idea.status === 'DONE');
  const scheduled = $derived(idea.status === 'SCHEDULED');
  const setAside = $derived(idea.status === 'DISMISSED');
  const facts = $derived(
    [
      idea.city.trim() || null,
      idea.place?.address ?? null,
      idea.priceBand ? PRICE_BAND_LABEL[idea.priceBand] : null,
      idea.typicalMinutes ? duration(idea.typicalMinutes) : null
    ]
      .filter((f) => f !== null)
      .join('  ·  ')
  );
</script>

<div class="panel idea-card" class:done class:set-aside={setAside}>
  <button type="button" class="idea-head" aria-expanded={expanded} onclick={() => (expanded = !expanded)}>
    <span class="idea-icon" class:sage={scheduled} class:faint={done}
      ><Icon name={IDEA_ICON[idea.kind]} /></span
    >
    <span class="menu-text">
      <span class="t-card-title idea-title">{idea.title}</span>
      {#if facts}<span class="t-data-small muted">{facts}</span>{/if}
    </span>
    {#if setAside}
      <span class="t-field-label faint">Not this trip</span>
    {:else if scheduled}
      <span class="t-field-label sage">On the timeline</span>
    {:else if idea.plannedDate}
      <span class="t-field-label accent">{dayCompact(idea.plannedDate)}</span>
    {/if}
  </button>

  {#if idea.note}<p class="t-hint idea-note">{idea.note}</p>{/if}

  {#if expanded}
    {#if idea.bestTime}
      <p class="t-hint with-icon"><Icon name="clock" /> {idea.bestTime}</p>
    {/if}

    {#if model.days.length > 0 && !scheduled}
      <div class="field">
        <span class="t-field-label">Pencil it in for</span>
        <div class="chip-row">
          {#each model.days as day (day.toString())}
            {@const chosen = idea.plannedDate?.equals(day) ?? false}
            <!-- Tapping the day it already holds rubs it out. -->
            <button
              type="button"
              class="chip"
              aria-pressed={chosen}
              onclick={() => model.pencilIdeaFor(idea.id, chosen ? null : day)}>{dayCompact(day)}</button
            >
          {/each}
        </div>
      </div>
    {/if}

    {#if model.party.length > 1}
      <div class="field">
        <span class="t-field-label">Who wants this</span>
        <div class="chip-row wrap">
          {#each model.party as traveler (traveler.id)}
            {@const keen = idea.interestedTravelerIds.includes(traveler.id)}
            <button
              type="button"
              class="chip with-mark"
              aria-pressed={keen}
              onclick={() => model.toggleInterest(idea.id, traveler.id)}
            >
              <span class="party-mark tiny">{initials(traveler)}</span>{displayName(traveler)}
            </button>
          {/each}
        </div>
      </div>
    {/if}

    <div class="chip-row idea-actions">
      {#if setAside}
        <button type="button" class="btn btn-muted" onclick={() => model.setIdeaStatus(idea.id, 'SAVED')}>
          <Icon name="plus" /> Bring it back
        </button>
      {:else}
        {#if !scheduled}
          <button type="button" class="btn btn-muted" onclick={() => open({ name: 'scheduleIdea', idea })}>
            <Icon name="clock" /> Schedule
          </button>
        {/if}
        <button
          type="button"
          class="btn btn-muted"
          onclick={() => model.setIdeaStatus(idea.id, done ? 'SAVED' : 'DONE')}
        >
          <Icon name="check" />
          {done ? 'Not yet' : 'Done'}
        </button>
        {#if !scheduled && !done}
          <button
            type="button"
            class="btn btn-muted"
            onclick={() => model.setIdeaStatus(idea.id, 'DISMISSED')}
          >
            <Icon name="archive" /> Not this trip
          </button>
        {/if}
      {/if}
      <!-- Removed at once, with an undo along the foot of the screen. -->
      <button
        type="button"
        class="btn-icon"
        aria-label="Remove {idea.title}"
        onclick={() => model.deleteIdea(idea.id)}><Icon name="trash" /></button
      >
    </div>
  {/if}
</div>
