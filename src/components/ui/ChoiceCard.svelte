<script lang="ts">
  import type { IconName } from '../../util/icons';
  import Icon from './Icon.svelte';

  interface Props {
    title: string;
    detail: string;
    icon: IconName;
    selected: boolean;
    onclick: () => void;
  }

  let { title, detail, icon, selected, onclick }: Props = $props();
</script>

<button type="button" class="choice-card" aria-pressed={selected} {onclick}>
  <Icon name={icon} />
  <span class="menu-text">
    <span class="t-body-small">{title}</span>
    <span class="t-hint">{detail}</span>
  </span>
  <Icon name="chevronRight" />
</button>
