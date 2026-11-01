<script lang="ts">
  // A rate typed by hand: what one unit of a currency is worth in the
  // settling one.
  import { parseRate, rateFor, rateText } from '../../state/costs-logic';
  import Field from '../ui/Field.svelte';
  import ModalHead from '../ui/ModalHead.svelte';
  import { useTrip } from './context';

  let { currency }: { currency: string } = $props();
  const { model, close } = useTrip();
  const costs = model.costs;
  // svelte-ignore state_referenced_locally
  const current = rateFor(costs.data, currency);
  let text = $state(current ? rateText(current.perUnit) : '');
  const value = $derived(parseRate(text));

  async function use() {
    if (value === null) return;
    close();
    await costs.setRate(currency, value);
  }

  async function forget() {
    close();
    await costs.clearRate(currency);
  }
</script>

<ModalHead eyebrow="Rate" title="1 {currency} is worth" />
<form
  class="modal-form"
  onsubmit={(ev) => {
    ev.preventDefault();
    void use();
  }}
>
  <Field
    label="In {costs.settle}"
    id="rate-value"
    mono
    inputmode="decimal"
    autofocus
    hint="From a receipt, a card statement or a bureau’s board. A rate typed here is kept, and a live one never replaces it."
    bind:value={text}
  />
  <button type="submit" class="btn btn-primary btn-block" disabled={value === null}>Use this rate</button>
  {#if current?.manual}
    <button type="button" class="btn btn-muted btn-block" onclick={forget}
      >Forget it, use the live rate</button
    >
  {/if}
</form>
