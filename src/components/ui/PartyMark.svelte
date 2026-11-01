<script lang="ts">
  import { initials, type Traveler } from '../../domain/model';

  let { traveler }: { traveler: Traveler } = $props();
</script>

<span class="party-mark" aria-hidden="true">{initials(traveler)}</span>
