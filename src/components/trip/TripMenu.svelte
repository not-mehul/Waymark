<script lang="ts">
  // Everything that is not one of the tabs, named, one tap further away: the
  // right distance for things reached for once a trip rather than once a minute.
  import { settings } from '../../state/app';
  import { ui } from '../../state/ui.svelte';
  import type { IconName } from '../../util/icons';
  import Icon from '../ui/Icon.svelte';
  import ModalHead from '../ui/ModalHead.svelte';
  import ThemeToggle from '../ui/ThemeToggle.svelte';
  import { useTrip, type TripSheet } from './context';

  const { model, open, close } = useTrip();
  const archived = $derived(model.dossier?.trip.archived === true);

  interface Row {
    icon: IconName;
    title: string;
    detail: string;
    danger?: boolean;
    act: () => void;
  }
  const opens = (sheet: TripSheet) => () => open(sheet);

  const goes = (view: 'map' | 'numbers' | 'notes') => () => {
    close();
    ui.go({ name: 'trip', tripId: model.tripId, view });
  };
  const viewRows: Row[] = [
    {
      icon: 'map',
      title: 'The map',
      detail: 'Every leg, on a chart or a globe, or as a list',
      act: goes('map')
    },
    { icon: 'chart', title: 'The numbers', detail: 'Distance, days, legs, carbon', act: goes('numbers') },
    {
      icon: 'compass',
      title: 'Destination notes',
      detail: 'Money, plugs, emergency numbers',
      act: goes('notes')
    }
  ];

  const settingsRows: Row[] = $derived([
    {
      icon: 'globe',
      title: 'Online extras',
      detail: settings.onlineExtras
        ? 'On: weather and live exchange rates'
        : 'Off: Waymark makes no network request',
      act: opens({ name: 'online' })
    },
    {
      icon: 'bell',
      title: 'Reminders',
      detail: settings.reminders.summary(),
      act: opens({ name: 'reminders' })
    }
  ]);
  const tripRows: Row[] = $derived([
    {
      icon: 'edit',
      title: 'Edit this trip',
      detail: 'Name, destinations, first and last day',
      act: opens({ name: 'edit' })
    },
    {
      icon: 'archive',
      title: archived ? 'Bring it back' : 'Put it away',
      detail: archived ? 'Back on the shelf with the others' : 'Off the shelf, kept whole, quiet',
      act: () => {
        close();
        if (!archived) ui.go({ name: 'shelf' });
        void model.setArchived(!archived);
      }
    },
    // Last, and the only row in the danger colour.
    {
      icon: 'trash',
      title: 'Delete this trip',
      detail: 'Everything on it goes with it',
      danger: true,
      act: opens({ name: 'delete' })
    }
  ]);
</script>

{#snippet rows(list: Row[])}
  <ul class="menu-list">
    {#each list as row (row.title)}
      <li>
        <button type="button" class="menu-item" class:danger={row.danger} onclick={row.act}>
          <Icon name={row.icon} />
          <span class="menu-text">
            <span class="t-body-small">{row.title}</span>
            <span class="t-hint">{row.detail}</span>
          </span>
          <Icon name="chevronRight" />
        </button>
      </li>
    {/each}
  </ul>
{/snippet}

<ModalHead title="This trip" />
{@render rows(viewRows)}
<hr class="hairline" />
{@render rows(settingsRows)}
<div class="menu-item static">
  <span class="menu-text"><span class="t-body-small">Appearance</span></span>
  <ThemeToggle />
</div>
<hr class="hairline" />
{@render rows(tripRows)}
