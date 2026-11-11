<script lang="ts">
  // Waymark's idle toy: a compass. Spin it and the needle settles on a bearing,
  // with a little overshoot, as a real one does. By the eighth spin at the
  // latest it finds true north (an easter egg).
  import { play } from 'dusk-dawn';
  import { cheer, flyBy, hatch } from '../../util/delight';

  const POINTS = [
    'north',
    'north-northeast',
    'northeast',
    'east-northeast',
    'east',
    'east-southeast',
    'southeast',
    'south-southeast',
    'south',
    'south-southwest',
    'southwest',
    'west-southwest',
    'west',
    'west-northwest',
    'northwest',
    'north-northwest'
  ];
  const TRUE_NORTH = 8;

  let spins = $state(0);
  let point = $state<number | null>(null);
  // Lucide's needle points northeast; the turn is counted from there and only ever grows, so it always spins forward.
  let turn = $state(0);
  let found = false;
  let toy = $state<HTMLButtonElement>();
  const line = $derived(
    point === null
      ? 'Which way next? Give it a spin.'
      : `Heading ${POINTS[point]}, ${Math.round(point * 22.5)}°.`
  );

  function spin() {
    spins += 1;
    const next = !found && spins >= TRUE_NORTH ? 0 : Math.floor(Math.random() * POINTS.length);
    const from = ((turn % 360) + 360) % 360;
    turn += 360 + ((next * 22.5 - 45 - from + 720) % 360);
    point = next;
    if (next === 0 && !found) {
      found = true;
      setTimeout(() => {
        play(toy, 'pop');
        cheer(toy, { count: 36, spread: 0.6 });
        void flyBy();
        hatch('north', 'True north. Every way from here is forward.');
      }, 700);
    }
  }
</script>

<button type="button" class="idle-toy" bind:this={toy} aria-label="Spin the compass" onclick={spin}>
  <!-- Lucide "compass" -->
  <svg viewBox="0 0 24 24" aria-hidden="true">
    <circle cx="12" cy="12" r="10" />
    <path
      class="needle"
      style:--turn="{turn}deg"
      d="m16.24 7.76-1.804 5.411a2 2 0 0 1-1.265 1.265L7.76 16.24l1.804-5.411a2 2 0 0 1 1.265-1.265z"
    />
  </svg>
</button>
<p class="idle-line" aria-live="polite">{line}</p>

<style>
  .needle {
    transform-box: view-box;
    transform-origin: 12px 12px;
    transform: rotate(var(--turn));
    /* The slow spatial spring: the needle swings past its bearing and settles back. */
    transition: transform var(--spring-spatial-slow-dur) var(--spring-spatial-slow);
  }
</style>
