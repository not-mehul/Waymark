<script lang="ts">
  // style.md §8.9. Shared by Waymark, BookShelf and Ledger; keep the copies identical.
  // A binary switch between Dusk and Dawn. On "System" it shows whichever side
  // the system is on, and tapping it picks the other.
  interface Props {
    /** True when the page is showing Dawn. */
    dawn: boolean;
    label: string;
    ontoggle: () => void;
  }

  let { dawn, label, ontoggle }: Props = $props();
</script>

<button
  type="button"
  class="theme-toggle"
  role="switch"
  aria-label={label}
  aria-checked={dawn}
  onclick={ontoggle}
>
  <span class="toggle-icon icon-moon" aria-hidden="true">
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round">
      <path d="M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z" />
    </svg>
  </span>
  <span class="toggle-icon icon-sun" aria-hidden="true">
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round">
      <circle cx="12" cy="12" r="4" />
      <path
        d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41"
      />
    </svg>
  </span>
  <span class="knob" aria-hidden="true"></span>
</button>
