<script lang="ts">
  // The currency a trip is settled in. Changing it means fresh rates for
  // everything.
  import { deviceCurrency, isCurrency } from '../../state/costs-logic';
  import Field from '../ui/Field.svelte';
  import ModalHead from '../ui/ModalHead.svelte';
  import { useTrip } from './context';

  const { model, close } = useTrip();
  const costs = model.costs;
  const suggestions = [...new Set([costs.settle, deviceCurrency(), ...costs.foreign])];
  let typed = $state('');

  async function choose(code: string) {
    close();
    await costs.settleIn(code);
  }
</script>

<ModalHead eyebrow="Currency" title="Settle in" />
<form
  class="modal-form"
  onsubmit={(ev) => {
    ev.preventDefault();
    if (isCurrency(typed)) void choose(typed);
  }}
>
  <div class="chip-row wrap">
    {#each suggestions as code (code)}
      <button type="button" class="chip" aria-pressed={code === costs.settle} onclick={() => choose(code)}
        >{code}</button
      >
    {/each}
  </div>
  <Field
    label="Or another"
    id="settle-other"
    placeholder="USD, GBP, CHF…"
    mono
    bind:value={() => typed, (v) => (typed = v.toUpperCase().slice(0, 3))}
  />
  <button type="submit" class="btn btn-primary btn-block" disabled={!isCurrency(typed)}>
    Settle in {typed.trim() || '…'}
  </button>
  <p class="t-hint">Rates typed for the old currency are forgotten; live ones are fetched again.</p>
</form>
