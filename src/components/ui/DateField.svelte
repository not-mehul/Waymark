<script lang="ts">
  // A date, shown as the Kotlin PickerField shows it, opening a month calendar
  // in place: Monday first, today ringed, the chosen day lit. A tap on a day
  // chooses it and folds the calendar away.
  import { dayCompact } from '../../domain/time-text';
  import { compareDates, systemZone, todayIn, type LocalDate } from '../../domain/time';
  import Icon from './Icon.svelte';

  interface Props {
    label: string;
    id: string;
    value: LocalDate;
    /** Days before this are shown but cannot be chosen. */
    min?: LocalDate;
    onchange?: (date: LocalDate) => void;
  }

  let { label, id, value, min, onchange }: Props = $props();

  const MONTHS = [
    'January',
    'February',
    'March',
    'April',
    'May',
    'June',
    'July',
    'August',
    'September',
    'October',
    'November',
    'December'
  ];
  const WEEKDAYS = ['Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa', 'Su'];

  let open = $state(false);
  // The month on show: the chosen day's, until the arrows move it.
  // svelte-ignore state_referenced_locally
  let shown = $state(value.toPlainYearMonth());
  const today = todayIn(systemZone());

  const cells = $derived.by(() => {
    const first = shown.toPlainDate({ day: 1 });
    const lead = first.dayOfWeek - 1;
    const days: (LocalDate | null)[] = Array.from({ length: lead }, () => null);
    for (let d = 1; d <= shown.daysInMonth; d++) days.push(shown.toPlainDate({ day: d }));
    return days;
  });

  function toggle() {
    shown = value.toPlainYearMonth();
    open = !open;
  }

  function choose(date: LocalDate) {
    open = false;
    onchange?.(date);
  }
</script>

<div class="field">
  <span class="t-field-label" id="{id}-label">{label}</span>
  <button
    type="button"
    class="picker-field"
    aria-labelledby="{id}-label {id}-value"
    aria-expanded={open}
    aria-controls="{id}-calendar"
    onclick={toggle}
  >
    <Icon name="calendar" />
    <span class="t-data" id="{id}-value">{dayCompact(value)}</span>
    <span class="t-data-small">{value.year}</span>
  </button>
  {#if open}
    <div class="calendar" id="{id}-calendar" style:margin-top="var(--space-2)">
      <div class="calendar-head">
        <button
          type="button"
          class="btn-icon"
          aria-label="Previous month"
          onclick={() => (shown = shown.subtract({ months: 1 }))}><Icon name="chevronLeft" /></button
        >
        <span class="t-data" aria-live="polite">{MONTHS[shown.month - 1]} {shown.year}</span>
        <button
          type="button"
          class="btn-icon"
          aria-label="Next month"
          onclick={() => (shown = shown.add({ months: 1 }))}><Icon name="chevronRight" /></button
        >
      </div>
      <div class="calendar-grid">
        {#each WEEKDAYS as weekday (weekday)}
          <span class="t-field-label" aria-hidden="true">{weekday}</span>
        {/each}
        {#each cells as date, i (date?.toString() ?? `blank-${i}`)}
          {#if date}
            <button
              type="button"
              class="day-cell"
              class:today={date.equals(today)}
              aria-pressed={date.equals(value)}
              aria-label={dayCompact(date)}
              disabled={min !== undefined && compareDates(date, min) < 0}
              onclick={() => choose(date)}>{date.day}</button
            >
          {:else}
            <span></span>
          {/if}
        {/each}
      </div>
    </div>
  {/if}
</div>
