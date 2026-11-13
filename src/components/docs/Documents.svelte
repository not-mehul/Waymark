<script lang="ts">
  // The tickets and documents kept with a booking (or with the whole trip):
  // a boarding pass picture opens full-bright for the scanner at the gate, a
  // PDF e-ticket in the phone's own reader. Everything works offline.
  import { liveQuery } from 'dexie';
  import { isPicture, type TripDocument } from '../../domain/model';
  import { documents } from '../../state/app';
  import { dataUrl, keepFile, openElsewhere, removeDocument } from '../../state/documents';
  import { ui } from '../../state/ui.svelte';
  import Icon from '../ui/Icon.svelte';
  import SectionHeader from '../ui/SectionHeader.svelte';
  import { useTrip } from '../trip/context';

  let {
    segmentId,
    label = 'Tickets and documents',
    hint = 'A boarding pass, an e-ticket, a voucher: kept on the phone, to show with the radio off.'
  }: { segmentId: string | null; label?: string; hint?: string } = $props();

  const { model, open } = useTrip();
  const kept = liveQuery(() =>
    segmentId
      ? documents.forSegment(segmentId)
      : documents.forTrip(model.tripId).then((all) => all.filter((d) => d.segmentId === null))
  );

  let picker = $state<HTMLInputElement | null>(null);
  let adding = $state(false);

  async function add(files: FileList | null) {
    if (!files || files.length === 0) return;
    adding = true;
    try {
      let added = 0;
      for (const file of files) if (await keepFile(file, model.tripId, segmentId)) added++;
      if (added > 0) ui.notify(added === 1 ? 'Kept with this booking.' : `${added} files kept.`, 'success');
    } catch (err) {
      console.error('Keeping a document', err);
      ui.notify('That file could not be kept.', 'error');
    } finally {
      adding = false;
    }
  }

  function show(d: TripDocument) {
    if (isPicture(d)) open({ name: 'document', documentId: d.id });
    else void openElsewhere(d);
  }
</script>

<SectionHeader {label} />
<div class="panel docs">
  {#each $kept ?? [] as d (d.id)}
    <div class="doc-row">
      <button type="button" class="doc-open" onclick={() => show(d)} aria-label="Show {d.name}">
        {#if isPicture(d)}
          <img class="doc-thumb" src={dataUrl(d)} alt="" />
        {:else}
          <span class="doc-thumb doc-file"><Icon name="fileText" /></span>
        {/if}
        <span class="doc-text">
          <span class="t-body-small ellipsis">{d.name}</span>
          <span class="t-hint"
            >{d.code
              ? 'Boarding pass · tap to show at the gate'
              : isPicture(d)
                ? 'Picture · tap to show'
                : 'Opens in your reader'}</span
          >
        </span>
      </button>
      <button type="button" class="btn-icon" aria-label="Remove {d.name}" onclick={() => removeDocument(d)}>
        <Icon name="trash" />
      </button>
    </div>
  {/each}
  {#if ($kept ?? []).length === 0}<p class="t-hint">{hint}</p>{/if}
  <button type="button" class="btn btn-secondary" disabled={adding} onclick={() => picker?.click()}>
    <Icon name="paperclip" />
    {adding ? 'Keeping…' : 'Add a ticket or document'}
  </button>
</div>
<input
  bind:this={picker}
  type="file"
  accept="image/*,application/pdf"
  multiple
  hidden
  onchange={(ev) => {
    const files = ev.currentTarget.files;
    void add(files).finally(() => (ev.currentTarget.value = ''));
  }}
/>
