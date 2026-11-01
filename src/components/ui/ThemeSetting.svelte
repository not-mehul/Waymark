<script lang="ts">
  // The theme setting (style.md §2): System follows the device, Dusk and Dawn
  // stay put. The header switch moves between the last two.
  import { settings } from '../../state/app';
  import type { ThemeChoice } from '../../state/settings.svelte';

  const CHOICES: readonly { value: ThemeChoice; label: string }[] = [
    { value: 'SYSTEM', label: 'System' },
    { value: 'DUSK', label: 'Dusk' },
    { value: 'DAWN', label: 'Dawn' }
  ];
</script>

<div class="menu-item static theme-setting">
  <span class="menu-text">
    <span class="t-body-small" id="theme-setting-label">Theme</span>
    <span class="t-hint">System follows your device.</span>
  </span>
  <div class="segmented" role="radiogroup" aria-labelledby="theme-setting-label">
    {#each CHOICES as choice (choice.value)}
      <button
        type="button"
        role="radio"
        aria-checked={settings.theme === choice.value}
        onclick={() => settings.setTheme(choice.value)}>{choice.label}</button
      >
    {/each}
  </div>
</div>
