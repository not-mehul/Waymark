<script lang="ts">
  // Once a trip is under way or over, each city it stayed in can go on to the
  // sibling apps: rated in Ledger, or sent as a postcard to the nookling.
  // Each is a link that opens a screen there to confirm; nothing is saved by
  // sending it (style.md §14.1).
  import { countryForCode } from '../../catalog/countries';
  import { citiesVisited, type CityVisit } from '../../domain/after-trip';
  import { dayCompact } from '../../domain/time-text';
  import { handOff, type Handoff } from '../../platform/apps';
  import { buildLink } from '../../platform/links';
  import { ui } from '../../state/ui.svelte';
  import Icon from '../ui/Icon.svelte';
  import ModalHead from '../ui/ModalHead.svelte';
  import { useTrip } from './context';

  const { model } = useTrip();
  const cities = $derived(model.dossier ? citiesVisited(model.dossier) : []);
  let sent = $state<Record<string, true>>({});

  const where = (c: CityVisit) => {
    const country = countryForCode(c.country)?.name;
    return country ? `${c.city}, ${country}` : c.city;
  };

  function said(result: Handoff, app: string) {
    if (result === 'shared') ui.notify(`${app} isn't on this phone, so it went to the share sheet.`, 'info');
    else if (result === 'copied')
      ui.notify(`${app} isn't on this phone. The city is copied instead.`, 'info');
    else if (result === 'failed') ui.notify(`That could not be sent to ${app}.`, 'error');
  }

  async function rate(c: CityVisit) {
    const url = buildLink(
      'ledger',
      'add',
      { city: c.city, country: c.country, date: c.arrived.toString() },
      'waymark'
    );
    const result = await handOff(url, `${where(c)}, ${dayCompact(c.arrived)}`);
    if (result === 'opened') sent = { ...sent, [`ledger:${c.city}`]: true };
    said(result, 'Ledger');
  }

  async function postcard(c: CityVisit) {
    const url = buildLink(
      'nookling',
      'postcard',
      { city: c.city, country: c.country, date: c.arrived.toString() },
      'waymark'
    );
    const result = await handOff(url, `A postcard from ${where(c)}`);
    if (result === 'opened') sent = { ...sent, [`nookling:${c.city}`]: true };
    said(result, 'Nookling');
  }
</script>

<ModalHead eyebrow="After the trip" title="The cities you stayed in" />
{#if cities.length === 0}
  <p class="t-hint">
    No stays on this trip yet. A city shows here once a hotel is booked in it, or the trip spends a day there.
  </p>
{:else}
  <ul class="after-list">
    {#each cities as c (c.city)}
      <li class="panel after-row">
        <div class="after-place">
          <span class="t-body-small">{where(c)}</span>
          <span class="t-hint">From {dayCompact(c.arrived)}</span>
        </div>
        <div class="btn-row">
          <button type="button" class="btn btn-secondary" onclick={() => rate(c)}>
            {#if sent[`ledger:${c.city}`]}<Icon name="check" />{:else}<Icon name="star" />{/if} Rate in Ledger
          </button>
          <button type="button" class="btn btn-muted" onclick={() => postcard(c)}>
            {#if sent[`nookling:${c.city}`]}<Icon name="check" />{:else}<Icon name="mail" />{/if} Postcard to Nookling
          </button>
        </div>
      </li>
    {/each}
  </ul>
  <p class="t-hint">
    Each opens the other app with the city filled in. Nothing is kept there until you say so.
  </p>
{/if}
