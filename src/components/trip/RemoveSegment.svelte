<script lang="ts">
  import { segmentTitle, type Segment } from '../../domain/model';
  import { ui } from '../../state/ui.svelte';
  import Icon from '../ui/Icon.svelte';
  import ModalHead from '../ui/ModalHead.svelte';
  import { useTrip } from './context';

  let { segment }: { segment: Segment } = $props();
  const { model, close } = useTrip();

  async function remove() {
    close();
    ui.go({ name: 'trip', tripId: model.tripId });
    await model.deleteSegment(segment.id);
  }
</script>

<ModalHead eyebrow="Confirm" title="Remove this booking" />
<p>{segmentTitle(segment)} will be removed from the itinerary, along with its reference.</p>
<div class="btn-row">
  <button type="button" class="btn btn-muted" onclick={close}>Keep</button>
  <button type="button" class="btn btn-muted" onclick={remove}><Icon name="trash" /> Remove</button>
</div>
