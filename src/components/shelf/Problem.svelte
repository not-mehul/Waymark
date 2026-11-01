<script lang="ts">
  import { ui } from '../../state/ui.svelte';
  import ModalHead from '../ui/ModalHead.svelte';

  let { title, detail }: { title: string; detail: string } = $props();
</script>

<ModalHead {title} />
<p>{detail}</p>
<button type="button" class="btn btn-muted btn-block" onclick={() => ui.close()}>Close</button>
