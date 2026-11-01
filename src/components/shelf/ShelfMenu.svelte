<script lang="ts">
  import { backUpEveryTrip } from '../../state/actions';
  import { ui } from '../../state/ui.svelte';
  import type { IconName } from '../../util/icons';
  import Icon from '../ui/Icon.svelte';
  import ModalHead from '../ui/ModalHead.svelte';
  import ThemeSetting from '../ui/ThemeSetting.svelte';

  /** The file picker lives in App, so it outlasts this dialog. */
  let { onpickfile }: { onpickfile: () => void } = $props();

  const items: { icon: IconName; label: string; act: () => void }[] = [
    {
      icon: 'users',
      label: 'People',
      act: () => {
        ui.close();
        ui.go({ name: 'people' });
      }
    },
    {
      icon: 'save',
      label: 'Back up every trip',
      act: () => {
        ui.close();
        void backUpEveryTrip();
      }
    },
    {
      icon: 'upload',
      label: 'Open a backup, a trip file or a trip code',
      act: () => {
        ui.close();
        onpickfile();
      }
    }
  ];
</script>

<ModalHead eyebrow="Every trip" title="Waymark" />
<ul class="menu-list">
  {#each items as item (item.label)}
    <li>
      <button type="button" class="menu-item" onclick={item.act}>
        <Icon name={item.icon} />
        <span>{item.label}</span>
      </button>
    </li>
  {/each}
</ul>
<hr class="hairline" />
<ThemeSetting />
<hr class="hairline" />
<p class="t-hint">
  A backup is one file with every trip in it. Keep it wherever you keep things; open it on a new phone to
  bring everything across.
</p>
