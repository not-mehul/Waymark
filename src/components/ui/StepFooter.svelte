<script lang="ts">
  // Back on the left, the forward action on the right, the same size: the
  // colour does the emphasis. "Next" until the last step, where it commits.
  import Icon from './Icon.svelte';

  interface Props {
    onback?: () => void;
    label: string;
    enabled: boolean;
    finishing?: boolean;
    onforward: () => void;
  }

  let { onback, label, enabled, finishing = false, onforward }: Props = $props();
</script>

<div class="btn-row step-footer">
  {#if onback}
    <button type="button" class="btn btn-muted" onclick={onback}><Icon name="chevronLeft" /> Back</button>
  {/if}
  <button type="button" class="btn btn-primary" disabled={!enabled} onclick={onforward}>
    {label}
    <Icon name={finishing ? 'check' : 'chevronRight'} />
  </button>
</div>
