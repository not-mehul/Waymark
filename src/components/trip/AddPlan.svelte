<script lang="ts">
  // Adding a hotel, a train or a booked table as four short questions rather
  // than one screen of eleven fields, in the order the answers arrive: what
  // sort of thing, what it is called and where, when, and then the reference
  // numbers somebody has to go and find. Only the first two are needed.
  import { displayName, initials, type GroundMode, type SegmentKind } from '../../domain/model';
  import { systemZone, todayIn, type LocalDate } from '../../domain/time';
  import { defaultPlanDay, newPlanDraft, planContext, planSegment, withKind } from '../../state/plan';
  import type { IconName } from '../../util/icons';
  import ChoiceCard from '../ui/ChoiceCard.svelte';
  import DateField from '../ui/DateField.svelte';
  import Field from '../ui/Field.svelte';
  import Icon from '../ui/Icon.svelte';
  import ModalHead from '../ui/ModalHead.svelte';
  import StepFooter from '../ui/StepFooter.svelte';
  import StepRail from '../ui/StepRail.svelte';
  import TimeField from '../ui/TimeField.svelte';
  import { useTrip } from './context';
  import PlaceReading from './PlaceReading.svelte';

  const { model, close } = useTrip();
  const party = model.party;
  const STEPS = ['Kind', 'What', 'When', 'Details'];
  const KINDS: { kind: SegmentKind; title: string; detail: string; icon: IconName }[] = [
    {
      kind: 'LODGING',
      title: 'Somewhere to stay',
      detail: "Hotel, rental, a friend's spare room",
      icon: 'bed'
    },
    { kind: 'GROUND', title: 'Getting around', detail: 'Train, taxi, ferry, hire car', icon: 'train' },
    {
      kind: 'EXPERIENCE',
      title: 'Something booked',
      detail: 'A tour, a table, a show, a ticket',
      icon: 'ticket'
    }
  ];
  const MODES: GroundMode[] = ['TRAIN', 'TRANSIT', 'TAXI', 'FERRY'];

  let step = $state(0);
  let draft = $state.raw(
    newPlanDraft(
      defaultPlanDay(model.dossier, todayIn(systemZone())),
      party.map((t) => t.id)
    )
  );
  const set = (changes: Partial<typeof draft>) => (draft = { ...draft, ...changes });

  const context = $derived(planContext(model.dossier, draft.startDate));
  const named = $derived(draft.title.trim() !== '');
  const segment = $derived(step === 3 ? planSegment(draft, model.tripId, model.dossier) : null);

  function toggle(id: string) {
    set({
      travelerIds: draft.travelerIds.includes(id)
        ? draft.travelerIds.filter((t) => t !== id)
        : [...draft.travelerIds, id]
    });
  }

  async function add() {
    if (!segment) return;
    close();
    await model.saveSegment(segment);
  }

  function moveCheckIn(day: LocalDate) {
    set({ startDate: day, endDate: draft.endDate.until(day).sign > 0 ? day : draft.endDate });
  }
</script>

<ModalHead eyebrow={STEPS[step]} title="Add a plan" />
<StepRail steps={STEPS} index={step} onstep={(s) => (step = s)} />

<div class="step-body">
  {#if step === 0}
    <p class="step-question">What are you adding?</p>
    {#each KINDS as option (option.kind)}
      <ChoiceCard
        title={option.title}
        detail={option.detail}
        icon={option.icon}
        selected={draft.kind === option.kind}
        onclick={() => {
          draft = withKind(draft, option.kind);
          step = 1;
        }}
      />
    {/each}
  {:else if step === 1}
    <p class="step-question">
      {draft.kind === 'LODGING'
        ? 'Where are you staying?'
        : draft.kind === 'GROUND'
          ? 'What is the journey?'
          : 'What is booked?'}
    </p>
    <Field
      label="Name"
      id="plan-title"
      autofocus
      placeholder={draft.kind === 'LODGING'
        ? 'The Bloomsbury Rooms'
        : draft.kind === 'GROUND'
          ? 'Eurostar 9014'
          : "Musée de l'Orangerie"}
      bind:value={() => draft.title, (v) => set({ title: v })}
    />
    {#if draft.kind === 'GROUND'}
      <div class="chip-row wrap" role="group" aria-label="How">
        {#each MODES as mode (mode)}
          <button
            type="button"
            class="chip with-mark"
            aria-pressed={draft.mode === mode}
            onclick={() => set({ mode })}
          >
            <Icon name={mode === 'TRAIN' || mode === 'TRANSIT' ? 'train' : 'car'} />
            {mode.charAt(0) + mode.slice(1).toLowerCase()}
          </button>
        {/each}
      </div>
      <Field
        label="From"
        id="plan-from"
        placeholder="London, or LHR"
        bind:value={() => draft.where, (v) => set({ where: v, wherePlace: null })}
      />
      <PlaceReading
        text={draft.where}
        {context}
        chosen={draft.wherePlace}
        onchoose={(p) => set({ wherePlace: p })}
      />
      <Field
        label="To"
        id="plan-to"
        placeholder="Paris"
        bind:value={() => draft.destination, (v) => set({ destination: v, destinationPlace: null })}
      />
      <PlaceReading
        text={draft.destination}
        {context}
        chosen={draft.destinationPlace}
        onchoose={(p) => set({ destinationPlace: p })}
      />
    {:else}
      <Field
        label="Where"
        id="plan-where"
        placeholder="Bloomsbury, London"
        bind:value={() => draft.where, (v) => set({ where: v, wherePlace: null })}
      />
      <PlaceReading
        text={draft.where.trim() || draft.title}
        {context}
        chosen={draft.wherePlace}
        onchoose={(p) => set({ wherePlace: p })}
      />
    {/if}
    <StepFooter onback={() => (step = 0)} label="Next" enabled={named} onforward={() => (step = 2)} />
  {:else if step === 2}
    <p class="step-question">{draft.kind === 'LODGING' ? 'Which nights?' : 'When is it?'}</p>
    {#if draft.kind === 'LODGING'}
      <div class="field-row">
        <DateField label="Check in" id="plan-start" value={draft.startDate} onchange={moveCheckIn} />
        <DateField
          label="Check out"
          id="plan-end"
          value={draft.endDate}
          min={draft.startDate}
          onchange={(d) => set({ endDate: d })}
        />
      </div>
    {:else}
      <DateField
        label="Date"
        id="plan-date"
        value={draft.startDate}
        onchange={(d) => set({ startDate: d, endDate: d })}
      />
    {/if}
    <div class="field-row">
      <TimeField
        label="From"
        id="plan-from-time"
        value={draft.startTime}
        onchange={(t) => set({ startTime: t })}
      />
      <TimeField
        label="Until"
        id="plan-until-time"
        value={draft.endTime}
        onchange={(t) => set({ endTime: t })}
      />
    </div>
    {#if party.length > 1}
      <div class="chip-row wrap" role="group" aria-label="Who is on it">
        {#each party as traveler (traveler.id)}
          <button
            type="button"
            class="chip with-mark"
            aria-pressed={draft.travelerIds.includes(traveler.id)}
            onclick={() => toggle(traveler.id)}
          >
            <span class="party-mark tiny">{initials(traveler)}</span>{displayName(traveler)}
          </button>
        {/each}
      </div>
    {/if}
    <StepFooter onback={() => (step = 1)} label="Next" enabled onforward={() => (step = 3)} />
  {:else}
    <p class="step-question">Anything else?</p>
    <Field
      label="Booked with"
      id="plan-vendor"
      placeholder="Direct booking"
      bind:value={() => draft.vendor, (v) => set({ vendor: v })}
    />
    <Field
      label="Confirmation code"
      id="plan-code"
      placeholder="BLM-4471-QE"
      mono
      bind:value={() => draft.confirmationCode, (v) => set({ confirmationCode: v })}
    />
    <Field
      label="Note"
      id="plan-note"
      placeholder="Reception staffed from 07:00"
      rows={2}
      bind:value={() => draft.note, (v) => set({ note: v })}
    />
    {#if !segment}
      <p class="t-hint form-problem">It ends before it starts. Go back a step and check the times.</p>
    {/if}
    <StepFooter onback={() => (step = 2)} label="Add" enabled={segment !== null} finishing onforward={add} />
  {/if}
</div>
