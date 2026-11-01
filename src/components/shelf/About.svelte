<script lang="ts">
  import { APP_VERSION } from '../../state/app';
  import ModalHead from '../ui/ModalHead.svelte';
  import SectionHeader from '../ui/SectionHeader.svelte';

  const CREDITS = [
    ['Coastlines', 'Natural Earth 1:110m — public domain'],
    ['Stations', 'IATA airport table — deduplicated, zones resolved from coordinates'],
    ['Cities', 'GeoNames cities15000 — CC BY 4.0, geonames.org'],
    ['Airlines', 'Hand-compiled IATA designators'],
    ['Destination notes', 'Hand-compiled'],
    ['Licence', 'MIT']
  ] as const;
</script>

<ModalHead eyebrow="Version {APP_VERSION}" title="Waymark" />
<p class="t-body-small">
  Everything for a trip in one place, held on this device, and working with the radio off. No itinerary is
  ever sent anywhere. The only time Waymark goes online is for the weather and exchange rates, and only once
  you turn on online extras in a trip's menu.
</p>
<p class="t-body-small">
  Nothing is backed up off this device. A trip lives on this phone until you back it up to a file or share it,
  which is the way to keep a copy anywhere else or hand one to somebody.
</p>
<SectionHeader label="Bundled data" />
{#each CREDITS as [label, credit] (label)}
  <div class="credit">
    <span class="t-field-label" style:margin="0">{label}</span>
    <span class="t-body-small">{credit}</span>
  </div>
{/each}
