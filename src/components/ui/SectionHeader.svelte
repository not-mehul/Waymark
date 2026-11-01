<script lang="ts">
  interface Props {
    label: string;
    /** A count or a total at the right-hand end. */
    trailing?: string;
    /** The trailing figure in the danger colour: a day with too much in it. */
    critical?: boolean;
  }
  let { label, trailing, critical = false }: Props = $props();
</script>

{#if trailing}
  <div class="section-header with-trailing">
    <h2 class="t-section-label">{label}</h2>
    <span class="t-field-label" class:critical>{trailing}</span>
  </div>
{:else}
  <h2 class="section-header t-section-label">{label}</h2>
{/if}
