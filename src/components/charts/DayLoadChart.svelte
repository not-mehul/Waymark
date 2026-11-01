<script lang="ts">
  // Days along the bottom, hours up the side. Two shades of one hue, moving
  // and booked, both named in the legend beneath: never colour alone.
  interface DayColumn {
    key: string;
    label: string;
    moving: number;
    booked: number;
    today: boolean;
  }
  let { days }: { days: DayColumn[] } = $props();
  const peak = $derived(Math.max(4, ...days.map((d) => d.moving + d.booked)));
</script>

{#if days.length > 0}
  <div class="day-load">
    <div class="day-columns" aria-hidden="true">
      {#each days as day (day.key)}
        <span class="day-column" class:today={day.today}>
          <span class="chart-part half" style:height="{(day.booked / peak) * 100}%"></span>
          <span class="chart-part full" style:height="{(day.moving / peak) * 100}%"></span>
        </span>
      {/each}
    </div>
    <div class="bar-head">
      <span class="t-field-label faint">{days[0]!.label}</span>
      <span class="t-field-label faint">{days.at(-1)!.label}</span>
    </div>
    <div class="legend">
      <span class="legend-item"><span class="swatch full"></span>Moving</span>
      <span class="legend-item"><span class="swatch half"></span>Booked</span>
    </div>
  </div>
{/if}
