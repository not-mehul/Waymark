<script lang="ts">
  import { tripEndDate, tripStartDate, type Trip } from '../../domain/model';
  import { dateRange } from '../../domain/time-text';
  import { cardStatus } from '../../state/shelf';
  import Icon from '../ui/Icon.svelte';

  interface Props {
    trip: Trip;
    now: number;
    dimmed?: boolean;
    onclick: () => void;
  }

  let { trip, now, dimmed = false, onclick }: Props = $props();
  const status = $derived(cardStatus(trip, now));
</script>

<button type="button" class="panel trip-card" class:dimmed {onclick}>
  <div class="trip-card-top">
    <div>
      <span class="t-card-title">{trip.name}</span>
      {#if trip.destinationSummary}
        <span class="t-body-small ellipsis">{trip.destinationSummary}</span>
      {/if}
    </div>
    <Icon name="chevronRight" />
  </div>
  <div class="trip-card-foot">
    <span class="t-data-small">{dateRange(tripStartDate(trip), tripEndDate(trip))}</span>
    <span class="t-data-small status-{status.tone}">{status.text}</span>
  </div>
</button>
