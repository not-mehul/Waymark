<script lang="ts" generics="V extends string">
  // Labels across the width, a hairline under all of them, and an amber rule
  // under the chosen one that slides between them.
  interface Props {
    tabs: readonly { tab: V; label: string }[];
    value: V;
    label: string;
  }

  let { tabs, value = $bindable(), label }: Props = $props();
  const index = $derived(
    Math.max(
      0,
      tabs.findIndex((t) => t.tab === value)
    )
  );
</script>

<div class="tab-rail" role="tablist" aria-label={label} style:--tabs={tabs.length} style:--index={index}>
  {#each tabs as t (t.tab)}
    <button type="button" role="tab" aria-selected={t.tab === value} onclick={() => (value = t.tab)}>
      {t.label}
    </button>
  {/each}
  <span class="tab-rule" aria-hidden="true"></span>
</div>
