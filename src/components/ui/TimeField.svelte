<script lang="ts">
  // A clock time, through the platform's own time picker: on Android that is
  // the system clock face, which every phone user already knows.
  import { localTime, type LocalTime } from '../../domain/time';

  interface Props {
    label: string;
    id: string;
    /** Null shows the field empty. */
    value: LocalTime | null;
    onchange: (time: LocalTime) => void;
  }

  let { label, id, value, onchange }: Props = $props();
  const pad = (n: number) => String(n).padStart(2, '0');
</script>

<div class="field">
  <label class="t-field-label" for={id}>{label}</label>
  <input
    {id}
    type="time"
    class="input input-mono"
    value={value ? `${pad(value.hour)}:${pad(value.minute)}` : ''}
    onchange={(ev) => {
      const [h, m] = ev.currentTarget.value.split(':').map(Number);
      if (Number.isInteger(h) && Number.isInteger(m)) onchange(localTime(h!, m!));
    }}
  />
</div>
