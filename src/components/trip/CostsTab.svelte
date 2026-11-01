<script lang="ts">
  // Who paid for what, and the fewest payments that square it. Everything is
  // settled in one currency, converted once at the rates listed at the foot.
  import { EXPENSE_CATEGORY_LABEL, formatMoney, type Expense } from '../../domain/model';
  import { dayCompact } from '../../domain/time-text';
  import { settings } from '../../state/app';
  import { rateFor, rateText } from '../../state/costs-logic';
  import Icon from '../ui/Icon.svelte';
  import NoticeBanner from '../ui/NoticeBanner.svelte';
  import SectionHeader from '../ui/SectionHeader.svelte';
  import { useTrip } from './context';

  const { model, open } = useTrip();
  const costs = model.costs;

  const money = (minor: number) => formatMoney({ minor, currency: costs.settle });
  const summary = $derived(costs.summary);
  const categories = $derived(
    [...summary.byCategory].sort((a, b) => b[1] - a[1]).map(([category, minor]) => ({ category, minor }))
  );
  const largest = $derived(categories[0]?.minor ?? 1);
  const unconvertedCurrencies = $derived([...new Set(summary.unconverted.map((e) => e.amount.currency))]);
  const byDay = $derived.by(() => {
    const days: { key: string; label: string; expenses: Expense[] }[] = [];
    for (const e of costs.expenses) {
      const key = e.spentOn.toString();
      const last = days.at(-1);
      if (last?.key === key) last.expenses.push(e);
      else days.push({ key, label: dayCompact(e.spentOn), expenses: [e] });
    }
    return days;
  });

  function forWhom(e: Expense): string {
    if (e.splitAmong.length === 0 || e.splitAmong.length >= costs.party.length) return '';
    return ` · for ${e.splitAmong.map((id) => costs.nameOf(id)).join(', ')}`;
  }

  // Rates are looked at whenever the tab opens, a currency appears, or
  // online extras are turned on.
  $effect(() => {
    void settings.onlineExtras;
    void costs.foreign.join();
    void costs.refreshRates();
  });
</script>

<div class="tab-body">
  {#if summary.total > 0}
    <div class="panel">
      <div class="stats">
        <div>
          <span class="t-stat accent">{money(summary.total)}</span><span class="t-field-label"
            >spent together</span
          >
        </div>
        {#if costs.party.length > 0}
          <div>
            <span class="t-stat">{money(Math.trunc(summary.total / costs.party.length))}</span>
            <span class="t-field-label">a head</span>
          </div>
        {/if}
      </div>
      {#if categories.length > 1}
        <hr class="hairline" />
        <div class="bars">
          {#each categories as row, index (row.category)}
            <span class="t-data-small muted">{EXPENSE_CATEGORY_LABEL[row.category]}</span>
            <span class="bar-track"
              ><span class="bar" class:lead={index === 0} style:width="{(row.minor / largest) * 100}%"
              ></span></span
            >
            <span class="t-data-small">{money(row.minor)}</span>
          {/each}
        </div>
      {/if}
    </div>

    <SectionHeader label="Settle up" />
    <div class="panel settle">
      {#if summary.transfers.length === 0}
        <p>Everyone has paid their share.</p>
      {/if}
      {#each summary.transfers as transfer (transfer.from + transfer.to)}
        <div class="settle-row">
          <span class="settle-who">{costs.nameOf(transfer.from)} pays {costs.nameOf(transfer.to)}</span>
          <span class="t-data accent">{money(transfer.amount)}</span>
        </div>
      {/each}
      <div class="settle-balances">
        {#each summary.balances.filter((b) => b.paid !== 0 || b.share !== 0) as balance (balance.travelerId)}
          <div class="settle-row">
            <span class="t-body-small">{costs.nameOf(balance.travelerId)}</span>
            <span class="t-data-small muted">paid {money(balance.paid)} · share {money(balance.share)}</span>
          </div>
        {/each}
      </div>
    </div>
  {/if}

  {#if summary.unconverted.length > 0}
    <NoticeBanner
      headline="Not counted yet: {summary.unconverted.length} in {unconvertedCurrencies.join(', ')}"
      detail="There is no rate to {costs.settle} for {unconvertedCurrencies.length === 1
        ? 'it'
        : 'them'}. Tap to type one in."
      onclick={() => open({ name: 'rate', currency: unconvertedCurrencies[0]! })}
    />
  {/if}

  <button
    type="button"
    class="btn btn-primary btn-block"
    disabled={costs.party.length === 0}
    onclick={() => open({ name: 'expense', expense: null })}
  >
    <Icon name="plus" /> Add an expense
  </button>
  {#if costs.party.length === 0 && costs.loaded}
    <p class="t-hint">Add somebody to the party first: an expense is paid by someone.</p>
  {/if}

  <SectionHeader label="Expenses" />
  {#if costs.expenses.length === 0}
    <p class="t-hint">Nothing spent yet. Add what was paid, by whom, and for whom.</p>
  {/if}
  {#each byDay as day (day.key)}
    <span class="t-field-label">{day.label}</span>
    {#each day.expenses as expense (expense.id)}
      <button type="button" class="panel expense-row" onclick={() => open({ name: 'expense', expense })}>
        <span class="menu-text">
          <span class="t-card-title">{expense.description}</span>
          <span class="t-data-small muted">
            {costs.nameOf(expense.paidBy)} paid · {EXPENSE_CATEGORY_LABEL[expense.category]}{forWhom(expense)}
          </span>
        </span>
        <span class="t-data">{formatMoney(expense.amount)}</span>
      </button>
    {/each}
  {/each}

  <SectionHeader label="Currency" />
  <button type="button" class="panel expense-row" onclick={() => open({ name: 'settleCurrency' })}>
    <span class="menu-text">
      <span class="t-field-label">Settled in</span>
      <span class="t-data heading">{costs.settle}</span>
      <span class="t-hint">Every expense is converted to this, once, at the rate below.</span>
    </span>
  </button>
  {#each costs.foreign as currency (currency)}
    {@const rate = rateFor(costs.data, currency)}
    <button type="button" class="panel expense-row" onclick={() => open({ name: 'rate', currency })}>
      <span class="t-data" class:accent={!rate} class:heading={!!rate}>
        1 {currency} = {rate ? `${rateText(rate.perUnit)} ${costs.settle}` : '?'}
      </span>
      <span class="t-data-small muted">
        {!rate ? 'tap to set' : rate.manual ? 'yours' : `live · ${dayCompact(rate.asOf)}`}
      </span>
    </button>
  {/each}
  {#if costs.note}<p class="t-hint">{costs.note}</p>{/if}
  {#if costs.foreign.length > 0}
    {#if settings.onlineExtras}
      <button
        type="button"
        class="btn btn-secondary btn-block"
        disabled={costs.updating}
        onclick={() => costs.refreshRates(true)}
      >
        {costs.updating ? 'Updating…' : 'Update the rates'}
      </button>
    {:else}
      <button type="button" class="btn btn-secondary btn-block" onclick={() => open({ name: 'online' })}>
        Use live rates
      </button>
    {/if}
  {/if}
</div>
