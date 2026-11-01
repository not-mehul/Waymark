<script lang="ts">
  // style.md §8.12. Shared by Waymark, BookShelf and Ledger; keep the copies identical.
  // One toast at a time, at the bottom centre. It pauses while touched, and a
  // failure is announced as an alert.
  interface Props {
    toast: {
      key: number;
      message: string;
      kind: string;
      action?: { label: string };
      leaving?: boolean;
    } | null;
    onpause: () => void;
    onresume: () => void;
    onaction: () => void;
  }

  let { toast, onpause, onresume, onaction }: Props = $props();
</script>

<!-- Live regions stay in the page so screen readers hear what arrives in them. -->
{#each ['status', 'alert'] as region (region)}
  <div class="toast-region" role={region}>
    {#if toast && (toast.kind === 'error') === (region === 'alert')}
      {#key toast.key}
        <!-- Touching the toast holds it on screen; it is not a control itself. -->
        <!-- svelte-ignore a11y_no_static_element_interactions -->
        <div
          class="toast"
          class:leaving={toast.leaving}
          id="toast"
          data-kind={toast.kind}
          onpointerenter={onpause}
          onpointerdown={onpause}
          onpointerleave={onresume}
          onpointerup={onresume}
          onpointercancel={onresume}
        >
          <span class="toast-text">{toast.message}</span>
          {#if toast.action}
            <button type="button" class="toast-action" onclick={onaction}>{toast.action.label}</button>
          {/if}
        </div>
      {/key}
    {/if}
  </div>
{/each}
