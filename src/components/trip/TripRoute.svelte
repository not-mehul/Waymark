<script lang="ts">
  // One trip, held open while its timeline and its bookings are read: they
  // share one model, as the Kotlin screens shared one view model.
  import { onDestroy } from 'svelte';
  import { TripModel } from '../../state/trip.svelte';
  import { ui, type TripView } from '../../state/ui.svelte';
  import MapScreen from '../map/MapScreen.svelte';
  import NumbersScreen from '../numbers/NumbersScreen.svelte';
  import AddFlight from '../segment/AddFlight.svelte';
  import SegmentScreen from '../segment/SegmentScreen.svelte';
  import Modal from '../ui/Modal.svelte';
  import { provideTrip, type TripSheet } from './context';
  import TravelerProfile from '../people/TravelerProfile.svelte';
  import AddPlan from './AddPlan.svelte';
  import AddIdea from './AddIdea.svelte';
  import CurrencySheet from './CurrencySheet.svelte';
  import ExpenseSheet from './ExpenseSheet.svelte';
  import RateSheet from './RateSheet.svelte';
  import AddTraveler from './AddTraveler.svelte';
  import ScheduleIdea from './ScheduleIdea.svelte';
  import RemoveTraveler from './RemoveTraveler.svelte';
  import DeleteTrip from './DeleteTrip.svelte';
  import EditTrip from './EditTrip.svelte';
  import OnlineExtras from './OnlineExtras.svelte';
  import Reminders from './Reminders.svelte';
  import RemoveSegment from './RemoveSegment.svelte';
  import TripMenu from './TripMenu.svelte';
  import TripScreen from './TripScreen.svelte';

  let {
    tripId,
    segmentId,
    adding,
    view
  }: { tripId: string; segmentId?: string; adding?: 'flight'; view?: TripView } = $props();

  // svelte-ignore state_referenced_locally
  const model = new TripModel(tripId);
  onDestroy(() => model.destroy());

  let sheet = $state.raw<TripSheet | null>(null);
  // What the dialog shows. It outlives [sheet] by the length of the closing
  // animation, so the content never reads a sheet that has gone.
  let shown = $state.raw<TripSheet>({ name: 'menu' });
  let sheetKey = $state(0);
  const close = () => (sheet = null);
  provideTrip({
    model,
    open: (next) => {
      sheet = next;
      shown = next;
      sheetKey++;
    },
    close
  });

  // A trip deleted elsewhere (or never there) has nothing to show.
  $effect(() => {
    if (model.dossier === null) ui.go({ name: 'shelf' });
  });
</script>

{#if adding === 'flight'}
  <AddFlight />
{:else if view === 'map'}
  <MapScreen />
{:else if view === 'numbers'}
  <NumbersScreen />
{:else if segmentId}
  {#key segmentId}
    <SegmentScreen {segmentId} />
  {/key}
{:else}
  <TripScreen />
{/if}

{#if sheet}
  {@const current = shown}
  <Modal contentKey={sheetKey} onclose={close}>
    {#key sheetKey}
      {#if current.name === 'menu'}
        <TripMenu />
      {:else if current.name === 'edit'}
        <EditTrip />
      {:else if current.name === 'reminders'}
        <Reminders />
      {:else if current.name === 'online'}
        <OnlineExtras />
      {:else if current.name === 'delete'}
        <DeleteTrip />
      {:else if current.name === 'expense'}
        <ExpenseSheet existing={current.expense} />
      {:else if current.name === 'rate'}
        <RateSheet currency={current.currency} />
      {:else if current.name === 'settleCurrency'}
        <CurrencySheet />
      {:else if current.name === 'addIdea'}
        <AddIdea />
      {:else if current.name === 'scheduleIdea'}
        <ScheduleIdea idea={current.idea} />
      {:else if current.name === 'addTraveler'}
        <AddTraveler />
      {:else if current.name === 'editTraveler'}
        <TravelerProfile traveler={current.traveler} onclose={close} />
      {:else if current.name === 'removeTraveler'}
        <RemoveTraveler traveler={current.traveler} />
      {:else if current.name === 'addPlan'}
        <AddPlan />
      {:else if current.name === 'removeSegment'}
        <RemoveSegment segment={current.segment} />
      {/if}
    {/key}
  </Modal>
{/if}
