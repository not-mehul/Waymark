<script lang="ts">
  // A little something to play with when the app is left alone. After a quiet
  // spell the dock rises in the corner with this app's toy, the way a toast
  // arrives. Any touch, key or scroll outside it sends it away again (a 160ms
  // fade, as every exit), so it never sits on top of work.
  import type { Snippet } from 'svelte';
  import { fade } from 'svelte/transition';
  import { cubicIn } from 'svelte/easing';

  let {
    after = 40_000,
    label,
    disabled = false,
    children
  }: { after?: number; label: string; disabled?: boolean; children: Snippet } = $props();

  let shown = $state(false);
  let dock = $state<HTMLElement>();

  $effect(() => {
    if (disabled) {
      shown = false;
      return;
    }
    let timer: ReturnType<typeof setTimeout> | undefined;
    const arm = () => {
      clearTimeout(timer);
      timer = setTimeout(() => (shown = true), after);
    };
    const wake = (event: Event) => {
      const inside = dock && event.target instanceof Node && dock.contains(event.target);
      if (shown && inside && !(event instanceof KeyboardEvent && event.key === 'Escape')) return;
      shown = false;
      arm();
    };
    const visibility = () => (document.hidden ? clearTimeout(timer) : arm());
    const events = ['pointerdown', 'keydown', 'wheel', 'touchstart', 'scroll'] as const;
    for (const name of events) window.addEventListener(name, wake, { capture: true, passive: true });
    document.addEventListener('visibilitychange', visibility);
    arm();
    return () => {
      clearTimeout(timer);
      for (const name of events) window.removeEventListener(name, wake, { capture: true });
      document.removeEventListener('visibilitychange', visibility);
    };
  });
</script>

{#if shown}
  <aside
    class="idle-dock"
    aria-label={label}
    bind:this={dock}
    data-testid="idle-dock"
    out:fade={{ duration: 160, easing: cubicIn }}
  >
    {@render children()}
  </aside>
{/if}

<style>
  /* While the dock is up, a toast (an easter egg's, say) sits above it, as it does above a FAB. */
  :global(html body:has(.idle-dock)) {
    --toast-lift: 96px;
  }
  .idle-dock {
    position: fixed;
    left: calc(var(--space-5) + var(--inset-left));
    bottom: calc(var(--space-5) + var(--inset-bottom));
    z-index: 40;
    display: flex;
    align-items: center;
    gap: var(--space-3);
    width: max-content;
    max-width: min(calc(100% - 2 * var(--space-5) - 72px), 340px);
    min-height: 72px;
    padding: var(--space-2) var(--space-5) var(--space-2) var(--space-2);
    /* The toast surface, opaque since dusk-dawn 2.1.0. */
    background: var(--modal-bg);
    border: 1px solid var(--border);
    border-radius: var(--corner-lg);
    box-shadow: 0 12px 32px rgba(var(--shadow-rgb), calc(0.45 * var(--shadow-strength)));
    animation: idle-dock-in var(--spring-spatial-default-dur) var(--spring-spatial-default) both;
  }
  /* The toy: one 56px target, its drawing in the Lucide idiom (§9) at 1.5 stroke. */
  .idle-dock :global(.idle-toy) {
    flex: none;
    display: grid;
    place-items: center;
    width: 56px;
    height: 56px;
    padding: 0;
    color: var(--accent-amber);
    background: var(--panel-bg-faint);
    border: 1px solid var(--border-faint);
    border-radius: var(--corner-md);
    cursor: pointer;
    transition:
      border-radius var(--spring-spatial-fast-dur) var(--spring-spatial-fast),
      transform var(--spring-spatial-fast-dur) var(--spring-spatial-fast),
      background-color var(--spring-effects-fast-dur) var(--spring-effects-fast);
  }
  .idle-dock :global(.idle-toy:active) {
    border-radius: var(--corner-xl);
    transform: scale(var(--press-scale));
  }
  .idle-dock :global(.idle-toy:focus-visible) {
    outline: 2px solid var(--accent-bright);
    outline-offset: 2px;
  }
  .idle-dock :global(.idle-toy > svg) {
    width: 36px;
    height: 36px;
    overflow: visible;
    fill: none;
    stroke: currentColor;
    stroke-width: 1.5;
    stroke-linecap: round;
    stroke-linejoin: round;
  }
  .idle-dock :global(.idle-line) {
    margin: 0;
    font-family: var(--font-serif);
    font-style: italic;
    font-size: 1rem;
    line-height: 1.4;
    color: var(--text-body);
  }
  @media (hover: hover) and (pointer: fine) {
    .idle-dock :global(.idle-toy:hover) {
      background-color: var(--panel-bg);
    }
  }
  @keyframes idle-dock-in {
    from {
      opacity: 0;
      transform: translateY(16px) scale(0.92);
    }
  }
  @media (prefers-reduced-motion: reduce) {
    .idle-dock {
      animation: idle-dock-fade 100ms linear both;
    }
    .idle-dock :global(.idle-toy:active) {
      transform: none;
    }
    @keyframes idle-dock-fade {
      from {
        opacity: 0;
      }
    }
  }
</style>
