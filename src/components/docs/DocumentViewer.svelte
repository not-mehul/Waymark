<script lang="ts">
  // A pass held up to a scanner: the picture as large as the screen allows,
  // on white, at full brightness with the screen kept on until it closes.
  import { liveQuery } from 'dexie';
  import { onDestroy, onMount } from 'svelte';
  import { documents } from '../../state/app';
  import { copyText } from '../../state/actions';
  import { dataUrl, setBright } from '../../state/documents';
  import Icon from '../ui/Icon.svelte';
  import ModalHead from '../ui/ModalHead.svelte';

  let { documentId, onclose }: { documentId: string; onclose: () => void } = $props();
  const doc = liveQuery(() => documents.get(documentId));

  let lock: { release(): Promise<void> } | null = null;
  onMount(() => {
    setBright(true);
    const wakeLock = (navigator as { wakeLock?: { request(type: 'screen'): Promise<typeof lock> } }).wakeLock;
    wakeLock
      ?.request('screen')
      .then((l) => (lock = l))
      .catch(() => undefined);
  });
  onDestroy(() => {
    setBright(false);
    void lock?.release().catch(() => undefined);
  });
</script>

{#if $doc}
  {@const d = $doc}
  <ModalHead eyebrow="Show at the gate" title={d.name} />
  <div class="pass-sheet">
    <img class="pass-image" src={dataUrl(d)} alt={d.code ? `Boarding pass ${d.name}` : d.name} />
  </div>
  <p class="t-hint">Full brightness while this is open. Hold it flat under the scanner.</p>
  <div class="btn-row">
    <button type="button" class="btn btn-muted" onclick={onclose}>Close</button>
    {#if d.code}
      {@const code = d.code}
      <button type="button" class="btn btn-secondary" onclick={() => copyText(code, 'The barcode’s text')}>
        <Icon name="copy" /> Copy the code
      </button>
    {/if}
  </div>
{:else if $doc === null}
  <ModalHead title="Removed" />
  <p class="t-hint">This document is no longer kept.</p>
{/if}
