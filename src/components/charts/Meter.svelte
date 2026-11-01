<script lang="ts">
  /** A ratio against a limit: ideas ticked off, the waking hours with something on them. */
  let { fraction, label, value }: { fraction: number; label: string; value: string } = $props();
</script>

<div class="meter">
  <div class="bar-head">
    <span class="t-body-small muted">{label}</span>
    <span class="t-data-small dim">{value}</span>
  </div>
  <span class="chart-track"
    ><span class="chart-fill sage" style:width="{Math.min(1, Math.max(0, fraction)) * 100}%"></span></span
  >
</div>
