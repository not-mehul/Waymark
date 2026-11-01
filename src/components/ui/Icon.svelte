<script lang="ts">
  import { icons, type IconName } from '../../util/icons';

  let { name }: { name: IconName } = $props();
</script>

<!-- Trusted, inlined Lucide markup (src/util/icons.ts), never external data. -->
<!-- eslint-disable-next-line svelte/no-at-html-tags -->
{@html icons[name]()}
