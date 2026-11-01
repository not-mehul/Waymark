<script lang="ts">
  // The one place Waymark asks to go online, and says exactly what that means
  // before it does. The same switch serves the weather and the rates.
  import { settings } from '../../state/app';
  import ModalHead from '../ui/ModalHead.svelte';
  import { useTrip } from './context';

  const { close } = useTrip();
  const set = (on: boolean) => {
    settings.setOnlineExtras(on);
    close();
  };
</script>

<ModalHead eyebrow={settings.onlineExtras ? 'On' : 'Off'} title="Online extras" />
<p>
  Everything else in Waymark works with the radio off, and always will. These two extras need to ask somebody:
</p>
<ul class="t-body-small bullet-list">
  <li>
    The weather where the trip will be, from Open-Meteo. It is sent a place, to about a kilometre, and the
    dates — never the trip, the bookings or who is going.
  </li>
  <li>
    The day's exchange rates, from the European Central Bank by way of Frankfurter. Nothing is sent but the
    request for today's rates.
  </li>
</ul>
<p class="t-hint">
  Neither service needs an account. What is fetched is kept on the phone, so it is still there abroad without
  a signal. Turn this off and the app makes no network request at all.
</p>
<div class="btn-row">
  {#if settings.onlineExtras}
    <button type="button" class="btn btn-muted" onclick={() => set(false)}>Turn off</button>
  {:else}
    <button type="button" class="btn btn-muted" onclick={close}>Not now</button>
    <button type="button" class="btn btn-primary" onclick={() => set(true)}>Turn on</button>
  {/if}
</div>
