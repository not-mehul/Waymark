<script lang="ts">
  import { onMount } from 'svelte';
  import { loadCatalog } from '../catalog/load';
  import { NO_HANDOVER, runHandover } from '../share/handover';
  import { openTripFile } from '../state/actions';
  import { trips } from '../state/app';
  import { ui } from '../state/ui.svelte';
  import People from './people/People.svelte';
  import TravelerProfile from './people/TravelerProfile.svelte';
  import About from './shelf/About.svelte';
  import ImportPreview from './shelf/ImportPreview.svelte';
  import NewTrip from './shelf/NewTrip.svelte';
  import Problem from './shelf/Problem.svelte';
  import Shelf from './shelf/Shelf.svelte';
  import ShelfMenu from './shelf/ShelfMenu.svelte';
  import TripRoute from './trip/TripRoute.svelte';
  import Modal from './ui/Modal.svelte';

  // Any type: file managers and mail apps disagree about what a .json is.
  let filePicker: HTMLInputElement;

  onMount(() => {
    // Every station and city, for reading places typed into a booking. Fetched
    // once, beside the page; nothing on the shelf waits for it.
    loadCatalog().catch((err: unknown) => console.error('Catalog', err));
    void (async () => {
      await trips.repair();
      // The native source (the Kotlin app's last copy of every trip) comes with the shell in phase 5.
      const handover = await runHandover(NO_HANDOVER, trips);
      if (handover.outcome === 'imported') {
        ui.tell(handover.trips === 1 ? 'One trip moved across.' : `${handover.trips} trips moved across.`);
      } else if (handover.outcome === 'failed') {
        console.error('Handover failed', handover.reason);
        ui.tell('Some trips could not be moved across yet. They are safe, and the next start tries again.');
      }
    })();
  });
</script>

{#if ui.screen.name === 'people'}
  <People />
{:else if ui.screen.name === 'trip'}
  {#key ui.screen.tripId}
    <TripRoute tripId={ui.screen.tripId} segmentId={ui.screen.segmentId} adding={ui.screen.adding} />
  {/key}
{:else}
  <Shelf />
{/if}

{#if ui.modal}
  {@const modal = ui.modal}
  <Modal contentKey={ui.modalKey} onclose={() => ui.close()}>
    {#key ui.modalKey}
      {#if modal.name === 'newTrip'}
        <NewTrip />
      {:else if modal.name === 'shelfMenu'}
        <ShelfMenu onpickfile={() => filePicker.click()} />
      {:else if modal.name === 'about'}
        <About />
      {:else if modal.name === 'import'}
        <ImportPreview bundles={modal.bundles} existing={modal.existing} />
      {:else if modal.name === 'problem'}
        <Problem title={modal.title} detail={modal.detail} />
      {:else if modal.name === 'traveler'}
        <TravelerProfile traveler={modal.traveler} />
      {/if}
    {/key}
  </Modal>
{/if}

<input
  bind:this={filePicker}
  type="file"
  hidden
  onchange={(ev) => {
    const file = ev.currentTarget.files?.[0];
    ev.currentTarget.value = '';
    if (file) void openTripFile(file);
  }}
/>
