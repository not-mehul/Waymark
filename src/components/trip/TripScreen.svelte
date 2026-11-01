<script lang="ts">
  // The command centre: one trip, four views of it. The header carries the
  // trip's name, its dates, and one control; everything else is in the menu.
  import { tripEndDate, tripStartDate } from '../../domain/model';
  import { dateRange } from '../../domain/time-text';
  import { TRIP_TABS } from '../../state/trip.svelte';
  import { ui } from '../../state/ui.svelte';
  import Icon from '../ui/Icon.svelte';
  import NoticeBanner from '../ui/NoticeBanner.svelte';
  import TabRail from '../ui/TabRail.svelte';
  import { useTrip } from './context';
  import CostsTab from './CostsTab.svelte';
  import IdeasTab from './IdeasTab.svelte';
  import PartyTab from './PartyTab.svelte';
  import Timeline from './Timeline.svelte';

  const { model, open } = useTrip();
  const trip = $derived(model.dossier?.trip);
  const openSegment = (segmentId: string) => ui.go({ name: 'trip', tripId: model.tripId, segmentId });
</script>

<section class="screen" aria-labelledby="trip-title">
  <header class="screen-head">
    <button type="button" class="btn-icon" aria-label="Back to itineraries" onclick={() => ui.back()}>
      <Icon name="arrowLeft" />
    </button>
    <div class="trip-head">
      <h1 class="t-card-title ellipsis" id="trip-title">{trip?.name ?? '…'}</h1>
      {#if trip}
        <span class="t-data-small">
          {dateRange(tripStartDate(trip), tripEndDate(trip))}{trip.archived ? ' · put away' : ''}
        </span>
      {/if}
    </div>
    <button type="button" class="btn-icon" aria-label="More" onclick={() => open({ name: 'menu' })}>
      <Icon name="menu" />
    </button>
  </header>

  {#if model.banner}
    {@const banner = model.banner}
    <NoticeBanner
      icon="bell"
      headline={banner.headline}
      detail={banner.detail}
      onclick={() => openSegment(banner.segmentId)}
      ondismiss={() => model.dismissBanner(banner.segmentId)}
    />
  {/if}

  <TabRail
    tabs={TRIP_TABS}
    value={model.tab}
    label="Views of this trip"
    onselect={(tab) => (model.tab = tab)}
  />

  <div role="tabpanel" id="trip-tab-panel" aria-labelledby={TRIP_TABS.find((t) => t.id === model.tab)?.domId}>
    {#if model.tab === 'TIMELINE'}
      <Timeline
        onopen={openSegment}
        onaddflight={() => ui.go({ name: 'trip', tripId: model.tripId, adding: 'flight' })}
      />
    {:else if model.tab === 'IDEAS'}
      <IdeasTab />
    {:else if model.tab === 'PARTY'}
      <PartyTab />
    {:else}
      <CostsTab />
    {/if}
  </div>
</section>
