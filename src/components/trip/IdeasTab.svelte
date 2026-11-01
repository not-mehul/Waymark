<script lang="ts">
  // The unscheduled half of the trip: places to see, food to try, walks to
  // take. Nothing here has a time on it, which is the point. Anything on the
  // list can be promoted onto the timeline, where it becomes an ordinary
  // booking and stops asking for attention.
  import { estimatedMinutes, outstanding } from '../../domain/ideas';
  import { IDEA_KIND_TEXT } from '../../domain/model';
  import { dayCompact, duration } from '../../domain/time-text';
  import Icon from '../ui/Icon.svelte';
  import SectionHeader from '../ui/SectionHeader.svelte';
  import { useTrip } from './context';
  import IdeaCard from './IdeaCard.svelte';

  type Grouping = 'KIND' | 'PLACE' | 'DAY';
  const GROUPINGS: { value: Grouping; label: string }[] = [
    { value: 'KIND', label: 'By kind' },
    { value: 'PLACE', label: 'By place' },
    { value: 'DAY', label: 'By day' }
  ];
  /** A day with more than this pencilled in is a day nobody will keep. */
  const FULL_DAY_MINUTES = 480;

  const { model, open } = useTrip();
  let grouping = $state<Grouping>('KIND');
</script>

<div class="tab-body">
  <div class="stats">
    <div>
      <span class="t-stat">{model.ideaTally.saved}</span><span class="t-field-label">On the list</span>
    </div>
    <div>
      <span class="t-stat accent">{model.ideaTally.scheduled}</span><span class="t-field-label"
        >Scheduled</span
      >
    </div>
    <div><span class="t-stat">{model.ideaTally.done}</span><span class="t-field-label">Done</span></div>
  </div>

  <!-- The same list, read three ways: by what it is, by where it is, and by the day it is pencilled in for. -->
  <div class="segmented full" role="radiogroup" aria-label="Group ideas">
    {#each GROUPINGS as option (option.value)}
      <button
        type="button"
        role="radio"
        aria-checked={grouping === option.value}
        onclick={() => (grouping = option.value)}>{option.label}</button
      >
    {/each}
  </div>

  {#if grouping === 'KIND'}
    {#each model.ideaSections as section (section.kind)}
      <SectionHeader label={IDEA_KIND_TEXT[section.kind].heading} />
      {#each section.ideas as idea (idea.id)}<IdeaCard {idea} />{/each}
    {/each}
  {:else if grouping === 'PLACE'}
    {#each model.ideasByCity as group (group.city)}
      <SectionHeader label={group.city} trailing="{outstanding(group)} open" />
      {#each group.ideas as idea (idea.id)}<IdeaCard {idea} />{/each}
    {/each}
  {:else}
    {#each model.dayPlan.days as day (day.date.toString())}
      {@const minutes = estimatedMinutes(day)}
      <SectionHeader
        label={dayCompact(day.date)}
        trailing={duration(minutes)}
        critical={minutes > FULL_DAY_MINUTES}
      />
      {#each day.ideas as idea (idea.id)}<IdeaCard {idea} />{/each}
    {/each}
    {#if model.dayPlan.undated.length > 0}
      <SectionHeader label="No day yet" />
      {#each model.dayPlan.undated as idea (idea.id)}<IdeaCard {idea} />{/each}
    {/if}
  {/if}

  {#if model.ideaTally.dismissed > 0}
    <button type="button" class="link-button" onclick={() => (model.showDismissed = !model.showDismissed)}>
      {model.showDismissed ? 'Hide what was set aside' : `Show ${model.ideaTally.dismissed} set aside`}
    </button>
  {/if}

  {#if model.ideaSections.length === 0}
    <div class="panel faint"><p class="t-hint">Nothing on the list yet.</p></div>
  {/if}

  <button type="button" class="btn btn-primary btn-block" onclick={() => open({ name: 'addIdea' })}>
    <Icon name="plus" /> Add an idea
  </button>
</div>
