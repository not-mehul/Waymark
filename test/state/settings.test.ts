import { describe, expect, it } from 'vitest';
import { Settings, type KeyValueStore } from '../../src/state/settings.svelte';

function memory(entries: Record<string, string> = {}): KeyValueStore & { entries: Record<string, string> } {
  return {
    entries,
    getItem: (key) => entries[key] ?? null,
    setItem: (key, value) => void (entries[key] = value)
  };
}

describe('Settings', () => {
  it('follows the system until the traveler picks a side', () => {
    const settings = new Settings(memory());
    expect(settings.theme).toBe('SYSTEM');
    settings.systemDark = false;
    expect(settings.mode).toBe('light');
    settings.toggleTheme();
    expect(settings.theme).toBe('DUSK');
    expect(settings.mode).toBe('dark');
  });

  it('reads and writes the Kotlin app keys', () => {
    const store = memory({
      'waymark.theme': 'DAWN',
      'waymark.lead.flights': '60',
      'waymark.online.extras': 'true'
    });
    const settings = new Settings(store);
    expect(settings.mode).toBe('light');
    expect(settings.onlineExtras).toBe(true);
    expect(settings.reminders.get('FLIGHTS')).toBe(60);
    settings.setLead('STAYS', 0);
    expect(store.entries['waymark.lead.stays']).toBe('0');
    expect(settings.reminders.get('STAYS')).toBe(0);
  });

  it('ignores what it cannot read, and works with no storage at all', () => {
    const settings = new Settings(memory({ 'waymark.theme': 'PURPLE', 'waymark.lead.ground': 'soon' }));
    expect(settings.theme).toBe('SYSTEM');
    expect(settings.reminders.get('GROUND')).toBeGreaterThan(0);
    const bare = new Settings(null);
    bare.setOnlineExtras(true);
    expect(bare.onlineExtras).toBe(true);
  });
});
