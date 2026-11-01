<script lang="ts">
  import { displayName, initials } from '../../domain/model';
  import Field from '../ui/Field.svelte';
  import ModalHead from '../ui/ModalHead.svelte';
  import { useTrip } from './context';

  const { model, close } = useTrip();
  let name = $state('');
  let nickname = $state('');

  async function add() {
    if (name.trim() === '') return;
    close();
    await model.addTraveler(name, nickname);
  }

  async function bring(travelerId: string) {
    close();
    await model.addExistingTraveler(travelerId);
  }
</script>

<ModalHead eyebrow="Party" title="Add traveler" />
<form
  class="modal-form"
  onsubmit={(ev) => {
    ev.preventDefault();
    void add();
  }}
>
  <!-- Somebody who has travelled with you before comes with their profile filled in. -->
  {#if model.otherTravelers.length > 0}
    <div class="field">
      <span class="t-field-label">Travelled with you before</span>
      <div class="chip-row">
        {#each model.otherTravelers as other (other.id)}
          <button type="button" class="chip with-mark" aria-pressed="false" onclick={() => bring(other.id)}>
            <span class="party-mark tiny">{initials(other)}</span>{displayName(other)}
          </button>
        {/each}
      </div>
    </div>
    <span class="t-field-label">Or someone new</span>
  {/if}
  <Field
    label="Full name"
    id="new-traveler-name"
    placeholder="As printed on the passport"
    bind:value={name}
  />
  <Field label="Known as" id="new-traveler-nickname" placeholder="Optional" bind:value={nickname} />
  <button type="submit" class="btn btn-primary btn-block" disabled={name.trim() === ''}>Add</button>
</form>
