<script lang="ts">
  interface Props {
    label: string;
    id: string;
    value: string;
    placeholder?: string;
    /** A textarea of this many rows instead of a one-line input. */
    rows?: number;
    mono?: boolean;
    inputmode?: 'text' | 'tel' | 'numeric' | 'decimal';
    autofocus?: boolean;
  }

  let {
    label,
    id,
    value = $bindable(),
    placeholder,
    rows,
    mono = false,
    inputmode,
    autofocus = false
  }: Props = $props();

  let control = $state<HTMLInputElement | HTMLTextAreaElement>();
  $effect(() => {
    if (autofocus) control?.focus();
  });
</script>

<div class="field">
  <label class="t-field-label" for={id}>{label}</label>
  {#if rows}
    <textarea
      bind:this={control}
      bind:value
      {id}
      class="textarea"
      class:input-mono={mono}
      {placeholder}
      {rows}
      spellcheck={mono ? false : undefined}
    ></textarea>
  {:else}
    <input
      bind:this={control}
      bind:value
      {id}
      type="text"
      class="input"
      class:input-mono={mono}
      {placeholder}
      {inputmode}
      autocomplete="off"
      spellcheck={mono ? false : undefined}
    />
  {/if}
</div>
