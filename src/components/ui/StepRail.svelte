<script lang="ts">
  // One segment per step, filled up to where you are, tappable back: a
  // position rather than "2 of 4". Forward is earned, not offered.
  interface Props {
    steps: readonly string[];
    index: number;
    onstep: (step: number) => void;
  }

  let { steps, index, onstep }: Props = $props();
</script>

<div class="step-rail" aria-label="Steps">
  {#each steps as label, step (label)}
    <button
      type="button"
      class:done={step < index}
      class:current={step === index}
      disabled={step > index}
      aria-current={step === index ? 'step' : undefined}
      aria-label={label}
      onclick={() => onstep(step)}
    ></button>
  {/each}
</div>
