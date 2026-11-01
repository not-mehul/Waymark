<script lang="ts" generics="V extends string">
  // style.md §8.5. Shared by Waymark, BookShelf and Ledger; keep the copies identical.
  // Words on a hairline, with an amber rule under the chosen one that slides
  // between them. Scrolls sideways when the words don't fit.
  import { tick } from 'svelte';

  interface Tab {
    id: V;
    label: string;
    /** A count after the word. */
    badge?: string | number;
    /** The tab button's DOM id, for tests and aria-labelledby. */
    domId?: string;
    /** The panel the tab controls. */
    controls?: string;
  }

  interface Props {
    tabs: readonly Tab[];
    value: V;
    label: string;
    onselect: (id: V) => void;
  }

  let { tabs, value, label, onselect }: Props = $props();
  let rail: HTMLDivElement;
  const hasValue = $derived(tabs.some((t) => t.id === value));
  let ruleX = $state(0);
  let ruleWidth = $state(0);

  function place() {
    const active = rail?.querySelector<HTMLElement>('[aria-selected="true"]');
    if (!active) {
      ruleWidth = 0;
      return;
    }
    ruleX = active.offsetLeft;
    ruleWidth = active.offsetWidth;
    const { scrollLeft, clientWidth } = rail;
    if (active.offsetLeft < scrollLeft || active.offsetLeft + active.offsetWidth > scrollLeft + clientWidth) {
      rail.scrollTo({ left: active.offsetLeft - (clientWidth - active.offsetWidth) / 2, behavior: 'smooth' });
    }
  }

  $effect(() => {
    void value;
    void tabs;
    void tick().then(place);
  });

  $effect(() => {
    const observer = new ResizeObserver(() => place());
    observer.observe(rail);
    // Fraunces arriving late changes nothing here (the rail is mono), but the
    // badge counts do change widths.
    for (const el of rail.querySelectorAll('[role="tab"]')) observer.observe(el);
    return () => observer.disconnect();
  });

  function onkeydown(event: KeyboardEvent) {
    const i = Math.max(
      0,
      tabs.findIndex((t) => t.id === value)
    );
    const next = event.key === 'ArrowRight' ? i + 1 : event.key === 'ArrowLeft' ? i - 1 : -2;
    if (next === -2) return;
    event.preventDefault();
    const tab = tabs[(next + tabs.length) % tabs.length];
    if (!tab) return;
    onselect(tab.id);
    void tick().then(() => rail.querySelector<HTMLElement>('[aria-selected="true"]')?.focus());
  }
</script>

<div
  class="tab-rail"
  role="tablist"
  aria-label={label}
  tabindex="-1"
  bind:this={rail}
  style:--rule-x="{ruleX}px"
  style:--rule-width="{ruleWidth}px"
  style:--rule-opacity={hasValue ? 1 : 0}
  {onkeydown}
>
  {#each tabs as tab, i (tab.id)}
    <button
      type="button"
      role="tab"
      id={tab.domId}
      aria-controls={tab.controls}
      aria-selected={tab.id === value}
      tabindex={tab.id === value || (!hasValue && i === 0) ? 0 : -1}
      onclick={() => onselect(tab.id)}
    >
      {tab.label}
      {#if tab.badge !== undefined}<span class="badge">{tab.badge}</span>{/if}
    </button>
  {/each}
  <span class="tab-rule" aria-hidden="true"></span>
</div>
