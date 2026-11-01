<script lang="ts">
  import { settings } from '../../state/app';
  import Icon from './Icon.svelte';
</script>

<button
  type="button"
  class="theme-toggle"
  role="switch"
  aria-label="Dawn theme"
  aria-checked={settings.mode === 'light'}
  onclick={() => settings.toggleTheme()}
>
  <span class="icon-moon"><Icon name="moon" /></span>
  <span class="icon-sun"><Icon name="sun" /></span>
  <span class="knob" aria-hidden="true"></span>
</button>
