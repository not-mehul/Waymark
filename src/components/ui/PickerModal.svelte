<script lang="ts">
  // style.md §8.6: a picker opens in its own modal, over whatever is open, and
  // never changes a value until Choose. Cancel, Escape, a backdrop tap and
  // Android Back all leave the value as it was.
  import { tick, type Snippet } from 'svelte';
  import { pushBackHandler } from '../../platform/native';
  import Icon from './Icon.svelte';

  interface Props {
    id: string;
    /** The field's label, over the title. */
    eyebrow?: string;
    title: string;
    oncancel: () => void;
    onchoose: () => void;
    /** False while the draft cannot be chosen. */
    canChoose?: boolean;
    children: Snippet;
  }

  let { id, eyebrow, title, oncancel, onchoose, canChoose = true, children }: Props = $props();
  let dialog: HTMLDialogElement;

  $effect(() => {
    const returnTo = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    dialog.showModal();
    void tick().then(() => dialog.querySelector<HTMLElement>('[aria-pressed="true"]')?.focus());
    const release = pushBackHandler(() => oncancel());
    return () => {
      release();
      if (dialog.open) dialog.close();
      if (returnTo?.isConnected) returnTo.focus();
    };
  });
</script>

<dialog
  bind:this={dialog}
  class="modal-dialog picker-dialog"
  aria-labelledby="{id}-picker-title"
  onclick={(ev) => {
    if (ev.target === dialog) oncancel();
  }}
  oncancel={(ev) => {
    ev.preventDefault();
    oncancel();
  }}
>
  <div class="modal-card picker-card">
    <div class="modal-head picker-head">
      <div class="modal-head-text">
        {#if eyebrow}<p class="modal-eyebrow">{eyebrow}</p>{/if}
        <h2 class="modal-title" id="{id}-picker-title" aria-live="polite">{title}</h2>
      </div>
      <button type="button" class="btn-icon" aria-label="Close" onclick={oncancel}><Icon name="x" /></button>
    </div>
    {@render children()}
    <div class="btn-row">
      <button type="button" class="btn btn-muted" onclick={oncancel}>Cancel</button>
      <button type="button" class="btn btn-primary" id="{id}-choose" disabled={!canChoose} onclick={onchoose}
        >Choose</button
      >
    </div>
  </div>
</dialog>
