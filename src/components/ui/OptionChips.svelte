<script lang="ts" generics="V extends string">
  interface Props {
    label: string;
    options: readonly { value: V; label: string }[];
    value: V;
  }

  let { label, options, value = $bindable() }: Props = $props();
</script>

<div class="field">
  <span class="t-field-label">{label}</span>
  <div class="chip-row" role="group" aria-label={label}>
    {#each options as option (option.value)}
      <button
        type="button"
        class="chip"
        aria-pressed={option.value === value}
        onclick={() => (value = option.value)}>{option.label}</button
      >
    {/each}
  </div>
</div>
