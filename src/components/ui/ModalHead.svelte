<script lang="ts">
  interface Props {
    eyebrow?: string;
    title: string;
  }

  let { eyebrow, title }: Props = $props();
</script>

<div class="modal-head">
  {#if eyebrow}<div class="modal-eyebrow">{eyebrow}</div>{/if}
  <h2 class="modal-title" id="modal-title" tabindex="-1">{title}</h2>
</div>
