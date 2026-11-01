<script lang="ts">
  // How the app has understood a typed place, said back before it is saved.
  // "Paris" becomes Paris, France and Paris's clock; "the bakery by the hotel"
  // stays as typed, on the clock of wherever the trip is that day. When a name
  // could be more than one city the others are offered beside it, and a place
  // nobody could look up can have its clock set by hand.
  import { placeCandidates, resolvedLabel, type ResolvedPlace } from '../../catalog/places';
  import { samePlace, type Place } from '../../domain/model';
  import { utcOffset } from '../../domain/time-text';
  import type { PlanContext } from '../../state/plan';

  interface Props {
    text: string;
    context: PlanContext;
    chosen: Place | null;
    onchoose: (place: Place | null) => void;
  }

  let { text, context, chosen, onchoose }: Props = $props();

  // A beat after typing stops: the gazetteer is thirty thousand cities.
  let readings = $state.raw<ResolvedPlace[]>([]);
  $effect(() => {
    const typed = text.trim();
    const { near, zoneId } = context;
    if (typed === '') {
      readings = [];
      return;
    }
    const timer = setTimeout(() => (readings = placeCandidates(typed, near, zoneId)), 250);
    return () => clearTimeout(timer);
  });

  const current = $derived(chosen ?? readings[0]?.place ?? null);
  // A typed place whose clock was set by hand is still the typed reading.
  const currentReading = $derived(current ? readings.find((r) => samePlace(r.place, current)) : undefined);
  const typedOnly = $derived((currentReading?.match ?? 'TYPED') === 'TYPED');
  let pickingZone = $state(false);

  const zones = (() => {
    try {
      return Intl.supportedValuesOf('timeZone').filter((z) => z.includes('/') && !z.startsWith('Etc/'));
    } catch {
      return [];
    }
  })();

  /** "Europe/Paris" → "Paris time, UTC+1". */
  const zoneName = (zoneId: string) =>
    `${zoneId.slice(zoneId.lastIndexOf('/') + 1).replace(/_/g, ' ')} time, ${utcOffset(zoneId)}`;
</script>

{#if current && readings.length > 0}
  <div class="place-reading">
    <span class="t-hint">
      Read as {typedOnly ? `“${current.name}”` : resolvedLabel(currentReading!)} · {zoneName(
        current.timeZoneId
      )}
    </span>
    {#if readings.length > 1}
      <div class="chip-row" role="group" aria-label="Readings">
        {#each readings as reading, i (i)}
          <button
            type="button"
            class="chip"
            aria-pressed={reading.match === 'TYPED' ? typedOnly : samePlace(reading.place, current)}
            onclick={() => onchoose(reading.place)}
          >
            {reading.match === 'TYPED' ? 'As typed' : resolvedLabel(reading)}
          </button>
        {/each}
      </div>
    {/if}
    {#if typedOnly}
      {#if pickingZone && zones.length > 0}
        <select
          class="input"
          aria-label="Whose clock?"
          value={current.timeZoneId}
          onchange={(ev) => {
            onchoose({ ...current, timeZoneId: ev.currentTarget.value });
            pickingZone = false;
          }}
        >
          {#each zones as zone (zone)}<option value={zone}>{zoneName(zone)}</option>{/each}
        </select>
      {:else}
        <button type="button" class="link-button accent" onclick={() => (pickingZone = true)}
          >Set its clock</button
        >
      {/if}
    {/if}
  </div>
{/if}
