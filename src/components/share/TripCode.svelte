<script lang="ts">
  // The trip as a QR code, for Waymark on a phone across the table. It
  // carries as much of the trip as fits, everything if it can, then without
  // the ideas, then without long notes, and says what it left out. Too big
  // even for that, it says to send the file instead.
  import { TRIP_CODE_PREFIX } from '../../share/files';
  import { qrDrawing } from '../../share/qr';
  import { archiveOf, CODE_DETAIL_OMITS, toCode } from '../../share/trip-archive';
  import { useTrip } from '../trip/context';
  import Icon from '../ui/Icon.svelte';
  import ModalHead from '../ui/ModalHead.svelte';

  let { onsendfile }: { onsendfile: () => void } = $props();
  const { model, close } = useTrip();

  const code = $derived.by(() => {
    const d = model.dossier;
    if (!d) return null;
    return toCode(
      archiveOf([
        { trip: d.trip, travelers: d.party.travelers, segments: d.segments, ideas: model.ideas, costs: null }
      ])
    );
  });
  const drawing = $derived(code ? qrDrawing(TRIP_CODE_PREFIX + code.payload) : null);
  const omits = $derived(code ? CODE_DETAIL_OMITS[code.detail] : null);
</script>

<ModalHead eyebrow="Trip code" title={model.dossier?.trip.name ?? 'Trip'} />
<div class="modal-form">
  {#if !code || !drawing}
    <p>This trip is too big for a code. Send the trip file instead: it carries everything.</p>
  {:else}
    <div class="qr-frame">
      <svg
        viewBox="0 0 {drawing.size} {drawing.size}"
        shape-rendering="crispEdges"
        role="img"
        aria-label="QR code for {model.dossier?.trip.name}"
      >
        <rect width={drawing.size} height={drawing.size} fill="#fff" />
        <path d={drawing.path} fill="#000" />
      </svg>
    </div>
    <p class="t-hint">
      Scan it with the other phone’s camera, or save a picture of it and open that from Waymark’s trip list.{omits
        ? ` Leaves out ${omits} to fit.`
        : ''}
    </p>
  {/if}
  <div class="btn-row">
    <button type="button" class="btn btn-muted" onclick={close}>Close</button>
    <button type="button" class="btn btn-muted" onclick={onsendfile}
      ><Icon name="share" /> Send the file</button
    >
  </div>
</div>
