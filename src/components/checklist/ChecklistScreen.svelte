<script lang="ts">
  // Before you go: the trip's packing and to-do list, the passports and
  // visas that run out too soon, and the documents that belong to the whole
  // trip rather than one booking (insurance, a visa, a passport scan).
  import { liveQuery } from 'dexie';
  import { documentWarnings, suggestedChecklist, warningText } from '../../domain/before-you-go';
  import { displayName, type ChecklistItem } from '../../domain/model';
  import { checklist } from '../../state/app';
  import { ui } from '../../state/ui.svelte';
  import Documents from '../docs/Documents.svelte';
  import { useTrip } from '../trip/context';
  import Icon from '../ui/Icon.svelte';
  import NoticeBanner from '../ui/NoticeBanner.svelte';
  import SectionHeader from '../ui/SectionHeader.svelte';

  const { model, open } = useTrip();
  const items = liveQuery(() => checklist.forTrip(model.tripId));
  const list = $derived($items ?? []);
  const done = $derived(list.filter((i) => i.done).length);
  const warnings = $derived(model.dossier ? documentWarnings(model.dossier) : []);
  const suggestions = $derived.by(() => {
    if (!model.dossier) return [];
    const have = new Set(list.map((i) => i.text.toLowerCase()));
    return suggestedChecklist(model.dossier).filter((s) => !have.has(s.toLowerCase()));
  });

  let draft = $state('');
  async function addDraft(ev: SubmitEvent) {
    ev.preventDefault();
    const lines = draft.split('\n');
    draft = '';
    await checklist.add(model.tripId, lines);
  }

  async function remove(item: ChecklistItem) {
    await checklist.remove(item.id);
    ui.notify(`“${item.text}” removed.`, 'info', { label: 'Undo', run: () => void checklist.restore(item) });
  }
</script>

<section class="screen" aria-labelledby="checklist-title">
  <header class="screen-head">
    <button
      type="button"
      class="btn-icon"
      aria-label="Back"
      onclick={() => ui.go({ name: 'trip', tripId: model.tripId })}
    >
      <Icon name="arrowLeft" />
    </button>
    <h1 class="t-card-title" id="checklist-title">Before you go</h1>
  </header>

  {#each warnings as w (w.traveler.id + w.doc.label + w.doc.expires)}
    <NoticeBanner
      icon="alert"
      critical={w.problem !== 'UNDER_SIX_MONTHS'}
      headline={warningText(w)}
      detail="Expires {w.expires.toLocaleString('en-GB', {
        day: 'numeric',
        month: 'long',
        year: 'numeric'
      })}. Tap to update it."
      onclick={() => open({ name: 'editTraveler', traveler: w.traveler })}
    />
  {/each}

  <SectionHeader
    label={list.length > 0 ? `Packing and to do · ${done} of ${list.length}` : 'Packing and to do'}
  />
  <div class="panel checklist">
    {#if list.length === 0}
      <p class="t-hint">Nothing on the list yet. Type a line below, or start from the usual.</p>
    {/if}
    <ul class="check-list">
      {#each list as item (item.id)}
        <li class="check-row" class:done={item.done}>
          <button
            type="button"
            class="check-box"
            role="checkbox"
            aria-checked={item.done}
            aria-label={item.text}
            onclick={() => checklist.setDone(item.id, !item.done)}
          >
            {#if item.done}<Icon name="check" />{/if}
          </button>
          <span class="t-body-small check-text">{item.text}</span>
          <button type="button" class="btn-icon" aria-label="Remove {item.text}" onclick={() => remove(item)}>
            <Icon name="x" />
          </button>
        </li>
      {/each}
    </ul>
    <form class="check-add" onsubmit={addDraft}>
      <label class="visually-hidden" for="check-new">Add to the list</label>
      <input
        id="check-new"
        class="input"
        placeholder="Sunglasses, house keys to a neighbour…"
        enterkeyhint="done"
        inputmode="text"
        bind:value={draft}
      />
      <button type="submit" class="btn btn-secondary" disabled={draft.trim() === ''}>
        <Icon name="plus" /> Add
      </button>
    </form>
    {#if done > 0}
      <button type="button" class="link-button" onclick={() => checklist.clearTicks(model.tripId)}
        >Untick everything</button
      >
    {/if}
  </div>

  {#if suggestions.length > 0}
    <SectionHeader label="The usual, for where this trip goes" />
    <div class="chip-row" role="group" aria-label="Suggested lines">
      {#each suggestions as line (line)}
        <button type="button" class="chip" onclick={() => checklist.add(model.tripId, [line])}>
          <Icon name="plus" />
          {line}
        </button>
      {/each}
    </div>
    {#if suggestions.length > 1}
      <button
        type="button"
        class="link-button accent"
        onclick={() => checklist.add(model.tripId, suggestions)}
      >
        Add them all
      </button>
    {/if}
  {/if}

  <SectionHeader label="Passports and visas" />
  <div class="panel facts">
    {#each model.party as t (t.id)}
      <button type="button" class="menu-item" onclick={() => open({ name: 'editTraveler', traveler: t })}>
        <Icon name="ticket" />
        <span class="menu-text">
          <span class="t-body-small">{displayName(t)}</span>
          <span class="t-hint">
            {t.identityDocs.length === 0
              ? 'No passport or visa noted'
              : t.identityDocs
                  .map((d) => `${d.label || (d.kind === 'PASSPORT' ? 'Passport' : 'Visa')} to ${d.expires}`)
                  .join(' · ')}
          </span>
        </span>
        <Icon name="chevronRight" />
      </button>
    {:else}
      <p class="t-hint">Add the travelers on the Party tab, then note their passports here.</p>
    {/each}
  </div>

  <Documents
    segmentId={null}
    label="Documents for the whole trip"
    hint="Insurance, a visa, a scan of a passport: anything that isn't one booking's."
  />
</section>
