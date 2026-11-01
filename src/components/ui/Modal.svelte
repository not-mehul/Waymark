<script lang="ts" module>
  import { getContext } from 'svelte';

  const KEY = Symbol('modal');

  /**
   * Call during init in content that renders after an async load; call the
   * returned function once it is on screen, so focus lands on its title.
   */
  export function useModalFocus(): () => void {
    const ctx = getContext<{ settle: () => void } | undefined>(KEY);
    return () => ctx?.settle();
  }
</script>

<script lang="ts">
  import { setContext, tick, type Snippet } from 'svelte';
  import { pushBackHandler } from '../../platform/native';
  import Icon from './Icon.svelte';

  interface Props {
    /** Changes whenever the content is replaced (another dialog opened in its place). */
    contentKey: number;
    onclose: () => void;
    children: Snippet;
  }

  let { contentKey, onclose, children }: Props = $props();
  let dialog: HTMLDialogElement;
  let card: HTMLDivElement;
  let closeBtn: HTMLButtonElement;

  // If the content didn't claim focus, land on its title (for screen readers),
  // or failing that on its first control. Never leave focus outside the card.
  function settle() {
    void tick().then(() => {
      const active = document.activeElement;
      if (card && (!card.contains(active) || active === card || active === closeBtn)) {
        (
          card.querySelector<HTMLElement>('#modal-title') ??
          card.querySelector<HTMLElement>('input, textarea, select, button:not(.modal-close)')
        )?.focus();
      }
    });
  }
  setContext(KEY, { settle });

  // One native <dialog> for the whole session of dialogs: showModal() gives the
  // top layer, an inert background and Escape (`cancel`). Focus returns to
  // whatever opened the first one, and Android Back closes it.
  $effect(() => {
    const returnTo = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    dialog.showModal();
    const release = pushBackHandler(() => onclose());
    return () => {
      release();
      if (dialog.open) dialog.close();
      if (returnTo?.isConnected) returnTo.focus();
    };
  });

  $effect(() => {
    void contentKey;
    card.scrollTop = 0;
    settle();
  });
</script>

<dialog
  bind:this={dialog}
  class="modal-dialog"
  aria-labelledby="modal-title"
  onclick={(ev) => {
    // The dialog box fills the viewport; a click on it outside the card dismisses.
    if (ev.target === dialog) onclose();
  }}
  oncancel={(ev) => {
    ev.preventDefault();
    onclose();
  }}
>
  <div class="modal-card" bind:this={card}>
    <button
      bind:this={closeBtn}
      type="button"
      class="btn-icon modal-close"
      aria-label="Close"
      onclick={onclose}
    >
      <Icon name="x" />
    </button>
    {@render children()}
  </div>
</dialog>
