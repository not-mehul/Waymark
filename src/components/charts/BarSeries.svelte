<script lang="ts">
  // Horizontal bars, sorted by the caller; the longest sets the scale. Every
  // chart here is single-hue: the two accents are too close to tell apart as
  // a pair, so length carries magnitude, a written label carries identity, and
  // colour does one job, emphasis.
  import type { Measure } from '../../domain/analytics';

  let { measures }: { measures: Measure[] } = $props();
  const peak = $derived(Math.max(1e-9, ...measures.map((m) => m.value)));
</script>

<div class="bar-series">
  {#each measures as measure (measure.label)}
    <div class="bar-measure">
      <div class="bar-head">
        <span class="t-body-small muted bar-label">{measure.label}</span>
        <!-- The value is always written out: a bar measured against a gridline is a bar misread. -->
        <span class="t-data-small" class:heading={measure.emphasis} class:dim={!measure.emphasis}
          >{measure.display}</span
        >
      </div>
      <span class="chart-track tall"
        ><span class="chart-fill" class:soft={!measure.emphasis} style:width="{(measure.value / peak) * 100}%"
        ></span></span
      >
    </div>
  {/each}
</div>
