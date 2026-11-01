<script lang="ts">
  import {
    displayName,
    EXPENSE_CATEGORIES,
    EXPENSE_CATEGORY_LABEL,
    moneyAmountText,
    parseMoney,
    type Expense,
    type ExpenseCategory
  } from '../../domain/model';
  import { systemZone, todayIn } from '../../domain/time';
  import { currencyOn, isCurrency } from '../../state/costs-logic';
  import DateField from '../ui/DateField.svelte';
  import Field from '../ui/Field.svelte';
  import ModalHead from '../ui/ModalHead.svelte';
  import { useTrip } from './context';

  let { existing }: { existing: Expense | null } = $props();
  const { model, close } = useTrip();
  const costs = model.costs;
  const party = costs.party;
  const today = todayIn(systemZone());

  // svelte-ignore state_referenced_locally
  const start = existing;
  let description = $state(start?.description ?? '');
  let day = $state(start?.spentOn ?? today);
  let currency = $state(start?.amount.currency ?? currencyOn(model.dossier, today, costs.settle));
  let amount = $state(start ? moneyAmountText(start.amount) : '');
  let paidBy = $state(start?.paidBy ?? party[0]?.id ?? '');
  let among = $state.raw<string[]>(
    start && start.splitAmong.length > 0 ? [...start.splitAmong] : party.map((t) => t.id)
  );
  let category = $state<ExpenseCategory>(start?.category ?? 'FOOD');
  let otherCurrency = $state(false);
  let typed = $state('');

  const money = $derived(parseMoney(amount, currency));
  const valid = $derived(description.trim() !== '' && money !== null && paidBy !== '' && among.length > 0);
  const offered = $derived([
    ...new Set([currencyOn(model.dossier, day, costs.settle), costs.settle, currency])
  ]);

  async function save() {
    if (!valid || !money) return;
    close();
    await costs.save({ description, amount: money, paidBy, among, spentOn: day, category }, existing);
  }

  async function remove() {
    if (!existing) return;
    close();
    await costs.delete(existing.id);
  }
</script>

<ModalHead eyebrow="Costs" title={existing ? 'Edit the expense' : 'Add an expense'} />
<form
  class="modal-form"
  onsubmit={(ev) => {
    ev.preventDefault();
    void save();
  }}
>
  <Field
    label="What it was"
    id="expense-what"
    placeholder="Dinner, the taxi from the station…"
    bind:value={description}
  />
  <Field
    label="How much, in {currency}"
    id="expense-amount"
    placeholder="0.00"
    mono
    inputmode="decimal"
    hint={amount.trim() !== '' && money === null ? `That is not an amount of ${currency}.` : undefined}
    bind:value={amount}
  />
  <div class="chip-row" role="group" aria-label="Currency">
    {#each offered as code (code)}
      <button
        type="button"
        class="chip"
        aria-pressed={code === currency && !otherCurrency}
        onclick={() => {
          currency = code;
          otherCurrency = false;
        }}>{code}</button
      >
    {/each}
    <button type="button" class="chip" aria-pressed={otherCurrency} onclick={() => (otherCurrency = true)}
      >Other</button
    >
  </div>
  {#if otherCurrency}
    <Field
      label="Currency code"
      id="expense-currency"
      placeholder="CHF, JPY, THB…"
      mono
      hint={typed.length === 3 && !isCurrency(typed) ? 'Not a currency code.' : undefined}
      bind:value={
        () => typed,
        (v) => {
          typed = v.toUpperCase().slice(0, 3);
          if (isCurrency(typed)) currency = typed;
        }
      }
    />
  {/if}

  <DateField label="Day" id="expense-day" value={day} onchange={(d) => (day = d)} />

  <div class="field">
    <span class="t-field-label">Paid by</span>
    <div class="chip-row wrap">
      {#each party as traveler (traveler.id)}
        <button
          type="button"
          class="chip"
          aria-pressed={traveler.id === paidBy}
          onclick={() => (paidBy = traveler.id)}>{displayName(traveler)}</button
        >
      {/each}
    </div>
  </div>

  <div class="field">
    <span class="t-field-label">For</span>
    <div class="chip-row wrap">
      {#each party as traveler (traveler.id)}
        {@const selected = among.includes(traveler.id)}
        <button
          type="button"
          class="chip"
          aria-pressed={selected}
          onclick={() =>
            (among = selected ? among.filter((id) => id !== traveler.id) : [...among, traveler.id])}
          >{displayName(traveler)}</button
        >
      {/each}
    </div>
    {#if among.length === 0}<p class="t-hint critical">Somebody has to share it.</p>{/if}
  </div>

  <div class="field">
    <span class="t-field-label">Kind</span>
    <div class="chip-row wrap">
      {#each EXPENSE_CATEGORIES as kind (kind)}
        <button type="button" class="chip" aria-pressed={kind === category} onclick={() => (category = kind)}
          >{EXPENSE_CATEGORY_LABEL[kind]}</button
        >
      {/each}
    </div>
  </div>

  <button type="submit" class="btn btn-primary btn-block" disabled={!valid}>Save</button>
  {#if existing}
    <button type="button" class="btn btn-danger btn-block" onclick={remove}>Remove</button>
  {/if}
</form>
