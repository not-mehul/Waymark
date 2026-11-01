<script lang="ts">
  // The map, full bleed, or the same trip as a list. The map is a picture,
  // and a picture says nothing to a screen reader; the list says what the
  // map shows, leg by leg, with the distance each covers and a way into the
  // booking. It is one tap away for everyone, because "which leg was the
  // long one" is easier read than looked for.
  import { destinationOf, nights, segmentStart, segmentTitle } from '../../domain/model';
  import { formatDistance } from '../../domain/geo';
  import { dayCompact } from '../../domain/time-text';
  import { chartPlaces, chartRoutes, legDistance, mapLegs, mapStays } from '../../state/map';
  import { ui } from '../../state/ui.svelte';
  import Icon from '../ui/Icon.svelte';
  import SectionHeader from '../ui/SectionHeader.svelte';
  import { useTrip } from '../trip/context';
  import WorldMap from './WorldMap.svelte';

  const { model } = useTrip();
  let projection = $state<'FLAT' | 'GLOBE'>('FLAT');
  let asList = $state(false);

  const segments = $derived(model.dossier?.segments ?? []);
  const places = $derived(chartPlaces(segments, model.ideas));
  const routes = $derived(chartRoutes(segments));
  const legs = $derived(mapLegs(segments));
  const stays = $derived(mapStays(segments));
  const total = $derived(legs.map(legDistance).reduce<number>((sum, km) => sum + (km ?? 0), 0));

  const back = () => ui.go({ name: 'trip', tripId: model.tripId });
  const openSegment = (segmentId: string) => ui.go({ name: 'trip', tripId: model.tripId, segmentId });
</script>

{#if asList}
  <section class="screen" aria-labelledby="map-list-title">
    <header class="screen-head">
      <button type="button" class="btn-icon" aria-label="Back to the map" onclick={() => (asList = false)}>
        <Icon name="arrowLeft" />
      </button>
      <h1 id="map-list-title" class="t-card-title">Where the trip goes</h1>
    </header>
    <div class="tab-body">
      {#if legs.length === 0 && stays.length === 0}
        <p class="t-hint">Nothing on the map yet.</p>
      {/if}
      {#if legs.length > 0}<SectionHeader label="Legs" />{/if}
      {#each legs as segment (segment.id)}
        {@const km = legDistance(segment)}
        <button type="button" class="panel expense-row" onclick={() => openSegment(segment.id)}>
          <span class="menu-text">
            <span class="t-body-small">{segmentTitle(segment)}</span>
            <span class="t-hint">
              {[
                dayCompact(segmentStart(segment).toPlainDate()),
                `${segment.origin.name} to ${destinationOf(segment).name}`,
                km ? formatDistance(km) : null
              ]
                .filter((part) => part !== null)
                .join(' · ')}
            </span>
          </span>
        </button>
      {/each}
      {#if stays.length > 0}<SectionHeader label="Stays" />{/if}
      {#each stays as stay (stay.id)}
        {@const n = nights(stay)}
        <button type="button" class="panel expense-row" onclick={() => openSegment(stay.id)}>
          <span class="menu-text">
            <span class="t-body-small">{stay.propertyName}</span>
            <span class="t-hint"
              >{stay.origin.city.trim() || stay.origin.name} · {n === 1 ? '1 night' : `${n} nights`}</span
            >
          </span>
        </button>
      {/each}
      {#if total > 0}<p class="t-hint">{formatDistance(total)} in all, as the crow flies.</p>{/if}
    </div>
  </section>
{:else}
  <section class="map-screen" aria-label="Map of the trip">
    <WorldMap
      {places}
      {routes}
      {projection}
      description="Map of the trip: {routes.length} legs between {places.length} places. The list button above reads them out."
      onselect={openSegment}
    />
    <div class="map-top">
      <button type="button" class="btn-icon map-button" aria-label="Back" onclick={back}>
        <Icon name="arrowLeft" />
      </button>
      <button
        type="button"
        class="btn-icon map-button"
        aria-label="Show as a list"
        onclick={() => (asList = true)}
      >
        <Icon name="list" />
      </button>
    </div>
    <div class="segmented map-projection" role="radiogroup" aria-label="Projection">
      <button
        type="button"
        role="radio"
        aria-checked={projection === 'FLAT'}
        onclick={() => (projection = 'FLAT')}>Chart</button
      >
      <button
        type="button"
        role="radio"
        aria-checked={projection === 'GLOBE'}
        onclick={() => (projection = 'GLOBE')}>Globe</button
      >
    </div>
  </section>
{/if}
