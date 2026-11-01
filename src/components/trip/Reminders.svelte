<script lang="ts">
  // How far ahead of each kind of booking to be told about it. "Off" is the
  // first choice rather than a separate switch: switching a category off is
  // choosing a lead time. They fire from the Android app (phase 5); here they
  // are only chosen.
  import {
    isOn,
    REMINDER_CATEGORIES,
    REMINDER_CATEGORY,
    type Lead,
    type ReminderCategory
  } from '../../domain/reminders';
  import { duration } from '../../domain/time-text';
  import { settings } from '../../state/app';
  import type { IconName } from '../../util/icons';
  import Icon from '../ui/Icon.svelte';
  import ModalHead from '../ui/ModalHead.svelte';

  const ICON: Record<ReminderCategory, IconName> = {
    FLIGHTS: 'plane',
    LEAVE: 'clock',
    CHECK_IN: 'ticket',
    GROUND: 'train',
    BOOKINGS: 'ticket',
    STAYS: 'bed'
  };
  const label = (lead: Lead) => (isOn(lead) ? duration(lead) : 'Off');
</script>

<ModalHead eyebrow={settings.reminders.anyOn ? 'Before it happens' : 'All off'} title="Reminders" />
<ul class="menu-list">
  {#each REMINDER_CATEGORIES as category (category)}
    {@const info = REMINDER_CATEGORY[category]}
    {@const lead = settings.reminders.get(category)}
    <li class="reminder-row">
      <div class="menu-item static" class:off={!isOn(lead)}>
        <Icon name={ICON[category]} />
        <div class="menu-text">
          <span class="t-body-small">{info.label}</span>
          <span class="t-hint">{info.detail}</span>
        </div>
        <span class="t-data-small lead">{label(lead)}</span>
      </div>
      <div class="chip-row" role="group" aria-label="{info.label}: how long before">
        {#each info.choices as choice (choice)}
          <button
            type="button"
            class="chip"
            aria-pressed={choice === lead}
            onclick={() => settings.setLead(category, choice)}>{label(choice)}</button
          >
        {/each}
      </div>
    </li>
  {/each}
</ul>
