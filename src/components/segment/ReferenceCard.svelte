<script lang="ts">
  // A reference turned to face somebody else: across a desk, at a kiosk, or
  // read down the phone. Big enough to read upside down, every character
  // spelt out, and the screen kept on while it is shown.
  import { onDestroy, onMount } from 'svelte';
  import { spellOut } from '../../domain/spelling';
  import { copyText } from '../../state/actions';
  import Icon from '../ui/Icon.svelte';
  import ModalHead from '../ui/ModalHead.svelte';

  let { label, code, onclose }: { label: string; code: string; onclose: () => void } = $props();
  const spelled = $derived(spellOut(code));
  // A ticket number is all digits, and digits need no spelling out.
  const hasLetters = $derived(spelled.some((s) => /[A-Z]/.test(s.character)));
  let awake = $state(false);

  let lock: { release(): Promise<void> } | null = null;
  onMount(() => {
    const wakeLock = (navigator as { wakeLock?: { request(type: 'screen'): Promise<typeof lock> } }).wakeLock;
    wakeLock
      ?.request('screen')
      .then((l) => {
        lock = l;
        awake = true;
      })
      .catch(() => undefined);
  });
  onDestroy(() => void lock?.release().catch(() => undefined));
</script>

<ModalHead eyebrow={label} title="Show at the desk" />
<div class="desk-code" aria-label={code}>
  {#each spelled as s, i (i)}<span class="desk-char" class:digit={/[0-9]/.test(s.character)}
      >{s.character}</span
    >{/each}
</div>
{#if hasLetters}
  <ol class="desk-spelling" aria-label="Spelt out">
    {#each spelled as s, i (i)}
      <li>
        <span class="t-data">{s.character}</span>
        <span class="t-hint">{s.word ?? '—'}</span>
      </li>
    {/each}
  </ol>
{/if}
{#if awake}<p class="t-hint">The screen stays on while this is open.</p>{/if}
<div class="btn-row">
  <button type="button" class="btn btn-muted" onclick={onclose}>Close</button>
  <button type="button" class="btn btn-secondary" onclick={() => copyText(code, label)}>
    <Icon name="copy" /> Copy
  </button>
</div>
