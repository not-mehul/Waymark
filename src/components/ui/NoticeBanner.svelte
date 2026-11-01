<script lang="ts">
  import type { IconName } from '../../util/icons';
  import Icon from './Icon.svelte';

  interface Props {
    headline: string;
    detail?: string;
    icon?: IconName;
    critical?: boolean;
    onclick?: () => void;
    ondismiss?: () => void;
  }

  let { headline, detail, icon = 'info', critical = false, onclick, ondismiss }: Props = $props();
</script>

<div class="notice" class:critical role="status">
  <Icon name={icon} />
  {#if onclick}
    <button type="button" class="notice-text" {onclick}>
      <span class="t-body-small">{headline}</span>
      {#if detail}<span class="t-hint">{detail}</span>{/if}
    </button>
  {:else}
    <div class="notice-text">
      <span class="t-body-small">{headline}</span>
      {#if detail}<span class="t-hint">{detail}</span>{/if}
    </div>
  {/if}
  {#if ondismiss}
    <button type="button" class="btn-icon" aria-label="Dismiss" onclick={ondismiss}><Icon name="x" /></button>
  {/if}
</div>
