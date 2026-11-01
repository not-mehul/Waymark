<script lang="ts">
  // Add something somebody heard about, one question at a time: what kind,
  // what it is, a few optional details, and, only when the trip touches more
  // than one city, where. The note rides along with the name because "the
  // bookshop off Charing Cross Road" and "Anna said the upstairs room" are
  // one thought.
  import { IDEA_KIND_TEXT, IDEA_KINDS, PRICE_BAND_LABEL, PRICE_BANDS } from '../../domain/model';
  import { newIdeaDraft, type IdeaDraft } from '../../state/idea-plan';
  import ChoiceCard from '../ui/ChoiceCard.svelte';
  import Field from '../ui/Field.svelte';
  import ModalHead from '../ui/ModalHead.svelte';
  import StepFooter from '../ui/StepFooter.svelte';
  import StepRail from '../ui/StepRail.svelte';
  import { useTrip } from './context';
  import { IDEA_ICON } from './idea-icons';

  const { model, close } = useTrip();
  const cities = model.cities;
  const needsCity = cities.length > 1;
  const STEPS = ['Kind', 'What', 'Details', ...(needsCity ? ['Where'] : [])];

  let step = $state(0);
  let draft = $state.raw<IdeaDraft>(newIdeaDraft(cities));
  const set = (changes: Partial<IdeaDraft>) => (draft = { ...draft, ...changes });
  const named = $derived(draft.title.trim() !== '');

  const PLACEHOLDER = {
    SEE: "Sir John Soane's Museum",
    EAT: 'Cacio e pepe at Da Enzo',
    DO: 'Cooking class in Trastevere',
    SHOP: 'The paper shop near the Pantheon'
  };

  async function add() {
    if (!named) return;
    close();
    await model.addIdea(draft);
  }
</script>

<ModalHead eyebrow={STEPS[step]} title="Add to the list" />
<StepRail steps={STEPS} index={step} onstep={(s) => (step = s)} />

<div class="step-body">
  {#if step === 0}
    <p class="step-question">What sort of thing is it?</p>
    {#each IDEA_KINDS as kind (kind)}
      <!-- Choosing is the answer; there is nothing else on this step to confirm. -->
      <ChoiceCard
        title={IDEA_KIND_TEXT[kind].label}
        detail={IDEA_KIND_TEXT[kind].hint}
        icon={IDEA_ICON[kind]}
        selected={draft.kind === kind}
        onclick={() => {
          set({ kind });
          step = 1;
        }}
      />
    {/each}
  {:else if step === 1}
    <p class="step-question">
      {draft.kind === 'EAT'
        ? 'What is it, and where did you hear about it?'
        : draft.kind === 'SHOP'
          ? 'What are you looking for?'
          : 'What is it?'}
    </p>
    <Field
      label="Name"
      id="idea-title"
      autofocus
      placeholder={PLACEHOLDER[draft.kind]}
      bind:value={() => draft.title, (v) => set({ title: v })}
    />
    <Field
      label="Note"
      id="idea-note"
      rows={3}
      placeholder="Anything worth remembering"
      bind:value={() => draft.note, (v) => set({ note: v })}
    />
    <StepFooter onback={() => (step = 0)} label="Next" enabled={named} onforward={() => (step = 2)} />
  {:else if step === 2}
    <p class="step-question">Anything else worth knowing?</p>
    <Field
      label="Address"
      id="idea-address"
      placeholder="13 Lincoln's Inn Fields"
      bind:value={() => draft.address, (v) => set({ address: v })}
    />
    <div class="field">
      <span class="t-field-label">Price</span>
      <div class="chip-row" role="group" aria-label="Price">
        {#each PRICE_BANDS as band (band)}
          <button
            type="button"
            class="chip"
            aria-pressed={draft.priceBand === band}
            onclick={() => set({ priceBand: draft.priceBand === band ? null : band })}
            >{PRICE_BAND_LABEL[band]}</button
          >
        {/each}
      </div>
    </div>
    <div class="field-row">
      <Field
        label="Best time"
        id="idea-best"
        placeholder="Weekday mornings"
        bind:value={() => draft.bestTime, (v) => set({ bestTime: v })}
      />
      <Field
        label="Minutes"
        id="idea-minutes"
        mono
        inputmode="numeric"
        placeholder="90"
        bind:value={() => draft.minutes, (v) => set({ minutes: v })}
      />
    </div>
    <StepFooter
      onback={() => (step = 1)}
      label={needsCity ? 'Next' : 'Add'}
      enabled={named}
      finishing={!needsCity}
      onforward={() => (needsCity ? (step = 3) : add())}
    />
  {:else}
    <p class="step-question">Which city?</p>
    {#each cities as city (city)}
      <ChoiceCard
        title={city}
        detail=""
        icon="mapPin"
        selected={draft.city.toLowerCase() === city.toLowerCase()}
        onclick={() => set({ city })}
      />
    {/each}
    <StepFooter
      onback={() => (step = 2)}
      label="Add"
      enabled={named && draft.city.trim() !== ''}
      finishing
      onforward={add}
    />
  {/if}
</div>
