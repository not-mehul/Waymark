<script lang="ts">
  // A clock time: a field shaped like a text input that opens a picker modal.
  // The whole clock is laid out on one six-wide grid: twenty-four hours, then
  // the minutes in fives (departures mostly are), then +1 to +4 for the ones
  // that are not, so 08:31 is never rounded. The running time is the title.
  import { localTime, type LocalTime } from '../../domain/time';
  import Icon from './Icon.svelte';
  import PickerModal from './PickerModal.svelte';

  interface Props {
    label: string;
    id: string;
    /** Null shows the field empty. */
    value: LocalTime | null;
    onchange: (time: LocalTime) => void;
  }

  let { label, id, value, onchange }: Props = $props();

  const HOURS = Array.from({ length: 24 }, (_, h) => h);
  const FIVES = Array.from({ length: 12 }, (_, i) => i * 5);
  const ONES = [1, 2, 3, 4];
  const pad = (n: number) => String(n).padStart(2, '0');
  const text = (t: LocalTime) => `${pad(t.hour)}:${pad(t.minute)}`;

  let open = $state(false);
  let hour = $state(12);
  let minute = $state(0);

  function start() {
    hour = value?.hour ?? 12;
    minute = value?.minute ?? 0;
    open = true;
  }

  function choose() {
    open = false;
    const next = localTime(hour, minute);
    if (!value || !next.equals(value)) onchange(next);
  }
</script>

<div class="field">
  <span class="t-field-label" id="{id}-label">{label}</span>
  <button
    type="button"
    class="picker-field"
    {id}
    aria-labelledby="{id}-label {id}-value"
    aria-haspopup="dialog"
    aria-expanded={open}
    onclick={start}
  >
    <Icon name="clock" />
    {#if value}
      <span class="t-data" id="{id}-value">{text(value)}</span>
    {:else}
      <span class="picker-placeholder" id="{id}-value">Choose a time</span>
    {/if}
  </button>
</div>

{#if open}
  <PickerModal
    {id}
    eyebrow={label}
    title="{pad(hour)}:{pad(minute)}"
    oncancel={() => (open = false)}
    onchoose={choose}
  >
    <div class="picker-section">
      <span class="t-field-label" id="{id}-hours">Hour</span>
      <div class="picker-grid" role="group" aria-labelledby="{id}-hours">
        {#each HOURS as h (h)}
          <button type="button" class="day-cell" aria-pressed={h === hour} onclick={() => (hour = h)}
            >{pad(h)}</button
          >
        {/each}
      </div>
    </div>
    <div class="picker-section">
      <span class="t-field-label" id="{id}-minutes">Minute</span>
      <div class="picker-grid" role="group" aria-labelledby="{id}-minutes">
        {#each FIVES as m (m)}
          <button
            type="button"
            class="day-cell"
            aria-pressed={m === minute - (minute % 5)}
            aria-label="{pad(m)} minutes"
            onclick={() => (minute = m + (minute % 5))}>:{pad(m)}</button
          >
        {/each}
        {#each ONES as n (n)}
          <button
            type="button"
            class="day-cell"
            aria-pressed={minute % 5 === n}
            aria-label="Plus {n} {n === 1 ? 'minute' : 'minutes'}"
            onclick={() => (minute = minute - (minute % 5) + (minute % 5 === n ? 0 : n))}>+{n}</button
          >
        {/each}
      </div>
    </div>
  </PickerModal>
{/if}
