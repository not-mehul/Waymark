<script lang="ts">
  // Promote an idea onto the timeline. The length is filled in from the
  // idea's own estimate, because a museum is not a coffee.
  import type { Idea } from '../../domain/model';
  import { localTime, type LocalTime } from '../../domain/time';
  import { defaultScheduleDate } from '../../state/idea-plan';
  import DateField from '../ui/DateField.svelte';
  import Field from '../ui/Field.svelte';
  import ModalHead from '../ui/ModalHead.svelte';
  import TimeField from '../ui/TimeField.svelte';
  import { useTrip } from './context';

  let { idea }: { idea: Idea } = $props();
  const { model, close } = useTrip();

  // svelte-ignore state_referenced_locally
  let date = $state(defaultScheduleDate(idea, model.dossier, model.now));
  let time = $state<LocalTime | null>(localTime(10));
  // svelte-ignore state_referenced_locally
  let minutesText = $state(String(idea.typicalMinutes ?? 60));
  const minutes = $derived.by(() => {
    const n = Number.parseInt(minutesText.replace(/\D/g, ''), 10);
    return Number.isFinite(n) ? n : null;
  });

  async function schedule() {
    if (!time) return;
    close();
    await model.scheduleIdea(idea, date, time, minutes);
    model.tab = 'TIMELINE';
  }
</script>

<ModalHead eyebrow="Put it on the day" title={idea.title} />
<div class="modal-form">
  <p class="t-hint">{idea.note ?? 'It moves to the timeline and off the list.'}</p>
  <DateField label="Date" id="schedule-date" value={date} onchange={(d) => (date = d)} />
  <TimeField label="From" id="schedule-time" value={time} onchange={(t) => (time = t)} />
  <Field
    label="How long, in minutes"
    id="schedule-minutes"
    mono
    inputmode="numeric"
    placeholder="90"
    bind:value={minutesText}
  />
  <button type="button" class="btn btn-primary btn-block" disabled={!time} onclick={schedule}
    >Schedule it</button
  >
  <button type="button" class="btn btn-muted btn-block" onclick={close}>Not now</button>
</div>
