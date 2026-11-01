<script lang="ts">
  // A part-to-whole as one divided bar, every part labelled beneath: two
  // shades of one hue and a neutral remainder.
  let { parts }: { parts: { label: string; value: number }[] } = $props();
  const total = $derived(parts.reduce((n, p) => n + p.value, 0));
  const SHADES = ['full', 'half', 'faint'];
</script>

{#if total > 0}
  <div class="split-bar">
    <div class="split-track">
      {#each parts as part, index (part.label)}
        <span class="chart-part {SHADES[index % 3]}" style:flex-grow={part.value / total}></span>
      {/each}
    </div>
    <div class="legend">
      {#each parts as part, index (part.label)}
        <span class="legend-item"><span class="swatch {SHADES[index % 3]}"></span>{part.label}</span>
      {/each}
    </div>
  </div>
{/if}
