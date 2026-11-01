<script lang="ts">
  import { displayName, SEAT_PREFERENCES, type Traveler } from '../../domain/model';
  import { trips } from '../../state/app';
  import { SEAT_LABEL } from '../../state/shelf';
  import { ui } from '../../state/ui.svelte';
  import Field from '../ui/Field.svelte';
  import Icon from '../ui/Icon.svelte';
  import ModalHead from '../ui/ModalHead.svelte';
  import OptionChips from '../ui/OptionChips.svelte';

  /** Closes the dialog it is in: the shelf's, unless a trip passes its own. */
  let { traveler, onclose = () => ui.close() }: { traveler: Traveler; onclose?: () => void } = $props();

  // A draft: nothing changes until Save.
  // svelte-ignore state_referenced_locally
  const original = traveler;
  let fullName = $state(original.fullName);
  let nickname = $state(original.nickname ?? '');
  let seat = $state(original.seatPreference);
  let meal = $state(original.mealPreference ?? '');
  let loyalty = $state(original.loyaltyRefs.join('\n'));
  let phone = $state(original.contactPhone ?? '');

  const seats = SEAT_PREFERENCES.map((value) => ({ value, label: SEAT_LABEL[value] }));
  const orNull = (text: string) => text.trim() || null;

  async function save() {
    if (!fullName.trim()) return;
    await trips.saveTraveler({
      ...original,
      fullName: fullName.trim(),
      nickname: orNull(nickname),
      seatPreference: seat,
      mealPreference: orNull(meal),
      loyaltyRefs: loyalty
        .split('\n')
        .map((line) => line.trim())
        .filter((line) => line !== ''),
      contactPhone: orNull(phone)
    });
    onclose();
  }
</script>

<ModalHead eyebrow="Profile" title={displayName(original)} />
<form
  class="modal-form"
  onsubmit={(ev) => {
    ev.preventDefault();
    void save();
  }}
>
  <Field
    label="Full name"
    id="traveler-name"
    placeholder="As printed on the passport"
    bind:value={fullName}
  />
  <Field label="Known as" id="traveler-nickname" placeholder="Optional" bind:value={nickname} />
  <OptionChips label="Seat" options={seats} bind:value={seat} />
  <Field label="Meal" id="traveler-meal" placeholder="Vegetarian, no nuts" bind:value={meal} />
  <Field
    label="Loyalty numbers, one per line"
    id="traveler-loyalty"
    placeholder="BA Executive Club 12345678"
    rows={3}
    mono
    bind:value={loyalty}
  />
  <Field
    label="Phone"
    id="traveler-phone"
    placeholder="+44 20 7946 0000"
    inputmode="tel"
    mono
    bind:value={phone}
  />
  <div class="btn-row">
    <button type="button" class="btn btn-muted" onclick={onclose}>Cancel</button>
    <button type="submit" class="btn btn-primary" disabled={!fullName.trim()}
      ><Icon name="check" /> Save</button
    >
  </div>
</form>
