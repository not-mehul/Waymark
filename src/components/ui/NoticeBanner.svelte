<script lang="ts">
  import type { IconName } from '../../util/icons';
  import Icon from './Icon.svelte';

  interface Props {
    headline: string;
    detail?: string;
    icon?: IconName;
    critical?: boolean;
    ondismiss?: () => void;
  }

  let { headline, detail, icon = 'info', critical = false, ondismiss }: Props = $props();
</script>

<div class="notice" class:critical role="status">
  <Icon name={icon} />
  <div>
    <span class="t-body-small">{headline}</span>
    {#if detail}<span class="t-hint">{detail}</span>{/if}
  </div>
  {#if ondismiss}
    <button type="button" class="btn-icon" aria-label="Dismiss" onclick={ondismiss}><Icon name="x" /></button>
  {/if}
</div>
