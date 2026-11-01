<script lang="ts">
  // The itinerary laid out for paper: dark type on white, one accent, nothing
  // that needs colour to read, and no booking split across a page. Shown
  // only when printing; the browser's print dialog also saves it as a PDF.
  import type { DocumentBlock } from '../../domain/itinerary-document';

  let { blocks, footer }: { blocks: DocumentBlock[]; footer: string } = $props();
</script>

<div class="print-itinerary" aria-hidden="true">
  {#each blocks as block, index (index)}
    {#if block.type === 'title'}
      <header class="p-title">
        <h1>{block.name}</h1>
        {#if block.summary.trim()}<p class="p-summary">{block.summary}</p>{/if}
        <p class="p-dates">{block.dates}</p>
        {#if block.party}<p class="p-dim">{block.party}</p>{/if}
      </header>
    {:else if block.type === 'section'}
      <h2 class="p-section">{block.text}</h2>
    {:else if block.type === 'day'}
      <div class="p-day">
        <h3>{block.text}</h3>
        {#if block.weather}<span class="p-weather">{block.weather}</span>{/if}
      </div>
    {:else if block.type === 'entry'}
      <div class="p-entry">
        <span class="p-time">{block.time}</span>
        <div>
          <p class="p-entry-title">{block.title}</p>
          {#each block.details as detail, i (i)}<p class="p-dim">{detail}</p>{/each}
          {#if block.people}<p class="p-dim">{block.people}</p>{/if}
          {#if block.reference}<p class="p-ref">{block.reference}</p>{/if}
          {#if block.note}<p class="p-note">{block.note}</p>{/if}
        </div>
      </div>
    {:else if block.type === 'row'}
      <div class="p-row"><span>{block.label}</span><span>{block.value}</span></div>
    {:else}
      <p class="p-note">{block.text}</p>
    {/if}
  {/each}
  <p class="p-footer">{footer}</p>
</div>
