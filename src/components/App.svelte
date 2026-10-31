<script lang="ts">
  import { SAMPLE_TRIP_NAME } from '../catalog/sample-itinerary';
</script>

<header>
  <p class="eyebrow">Waymark</p>
  <h1>Every trip, offline.</h1>
</header>
<p class="note">
  This is the new Waymark, being rebuilt on the same stack as Ledger and BookShelf. The trip logic is in place
  (try it against <em>{SAMPLE_TRIP_NAME}</em> in the tests); the screens follow in the next steps. Until then, the
  Android app on your phone is still the Kotlin one.
</p>

<style>
  header {
    margin-bottom: var(--space-5);
  }
  h1 {
    font-family: var(--font-serif);
    font-weight: 400;
    font-size: 2.25rem;
    line-height: 1.15;
    color: var(--text-heading);
    margin-top: var(--space-2);
  }
  .note {
    max-width: 36rem;
    color: var(--text-body);
  }
</style>
