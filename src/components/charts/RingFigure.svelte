<script lang="ts">
  // A ring with a figure in it. The type steps down as the figure gets longer
  // and is bounded by the ring's inner width, so "18452 km" never runs out
  // over its own stroke.
  let { fraction, figure, caption }: { fraction: number; figure: string; caption: string } = $props();
  const SIZE = 92;
  const STROKE = 6;
  const r = (SIZE - STROKE) / 2;
  const circumference = 2 * Math.PI * r;
  const size = $derived(
    figure.length <= 4 ? 'big' : figure.length <= 6 ? 'mid' : figure.length <= 9 ? 'small' : 'tiny'
  );
</script>

<div class="ring-figure">
  <div class="ring">
    <svg width={SIZE} height={SIZE} viewBox="0 0 {SIZE} {SIZE}" aria-hidden="true">
      <circle cx={SIZE / 2} cy={SIZE / 2} {r} class="ring-track" stroke-width={STROKE} fill="none" />
      <circle
        cx={SIZE / 2}
        cy={SIZE / 2}
        {r}
        class="ring-fill"
        stroke-width={STROKE}
        fill="none"
        stroke-dasharray="{circumference * Math.min(1, Math.max(0, fraction))} {circumference}"
        transform="rotate(-90 {SIZE / 2} {SIZE / 2})"
      />
    </svg>
    <span class="ring-text {size}">{figure}</span>
  </div>
  <span class="t-field-label">{caption}</span>
</div>
