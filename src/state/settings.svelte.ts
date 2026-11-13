// The traveler's settings, kept in localStorage: the theme has to be known
// before the first paint, which rules out anything asynchronous. These are the
// Kotlin app's DataStore keys, with a prefix so they stay apart from anything
// else on the origin.

import {
  REMINDER_CATEGORIES,
  ReminderPreferences,
  type Lead,
  type ReminderCategory
} from '../domain/reminders';
import { setNativeTheme } from '../platform/native';

export type ThemeChoice = 'SYSTEM' | 'DUSK' | 'DAWN';
export type ThemeMode = 'dark' | 'light';

const PREFIX = 'waymark.';

/** Storage that may be missing or throw (a private window, blocked site data). */
export interface KeyValueStore {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
}

function browserStore(): KeyValueStore | null {
  try {
    return typeof localStorage === 'undefined' ? null : localStorage;
  } catch {
    return null;
  }
}

export class Settings {
  theme = $state<ThemeChoice>('SYSTEM');
  onlineExtras = $state(false);
  notificationsAsked = $state(false);
  reminders = $state(new ReminderPreferences());
  /** When the last backup was written to Documents/Waymark/Backups (epoch millis), for the weekly one. */
  lastBackupAt = $state<number | null>(null);
  /** The folder a sync app keeps the same across phones, while sync is on. */
  syncFolder = $state<{ uri: string; name: string } | null>(null);
  lastSyncAt = $state<number | null>(null);
  /** What the system asks for, while the theme follows it. */
  systemDark = $state(true);

  /** The side the page is actually showing. */
  readonly mode: ThemeMode = $derived(
    this.theme === 'DUSK' ? 'dark' : this.theme === 'DAWN' ? 'light' : this.systemDark ? 'dark' : 'light'
  );

  constructor(private readonly store: KeyValueStore | null = browserStore()) {
    const theme = this.read('theme');
    if (theme === 'DUSK' || theme === 'DAWN') this.theme = theme;
    this.onlineExtras = this.read('online.extras') === 'true';
    this.notificationsAsked = this.read('notifications.asked') === 'true';
    this.lastBackupAt = numberOr(this.read('backup.last'));
    this.lastSyncAt = numberOr(this.read('sync.last'));
    try {
      const folder = JSON.parse(this.read('sync.folder') ?? 'null') as {
        uri?: unknown;
        name?: unknown;
      } | null;
      if (folder && typeof folder.uri === 'string') {
        this.syncFolder = { uri: folder.uri, name: typeof folder.name === 'string' ? folder.name : '' };
      }
    } catch {
      // A damaged value: sync is off until a folder is picked again.
    }
    const leads: Partial<Record<ReminderCategory, Lead>> = {};
    for (const category of REMINDER_CATEGORIES) {
      const lead = Number(this.read(`lead.${category.toLowerCase()}`) ?? NaN);
      if (Number.isInteger(lead) && lead >= 0) leads[category] = lead;
    }
    this.reminders = new ReminderPreferences(leads);
  }

  /** The toggle is binary: from SYSTEM it moves to whichever side is not showing. */
  toggleTheme(): void {
    this.setTheme(this.mode === 'dark' ? 'DAWN' : 'DUSK');
  }

  setTheme(theme: ThemeChoice): void {
    this.theme = theme;
    this.write('theme', theme);
  }

  setOnlineExtras(on: boolean): void {
    this.onlineExtras = on;
    this.write('online.extras', String(on));
  }

  markNotificationsAsked(): void {
    this.notificationsAsked = true;
    this.write('notifications.asked', 'true');
  }

  setLead(category: ReminderCategory, lead: Lead): void {
    this.reminders = this.reminders.with(category, lead);
    this.write(`lead.${category.toLowerCase()}`, String(lead));
  }

  markBackedUp(at: number): void {
    this.lastBackupAt = at;
    this.write('backup.last', String(at));
  }

  setSyncFolder(folder: { uri: string; name: string } | null): void {
    this.syncFolder = folder;
    this.write('sync.folder', JSON.stringify(folder));
    if (!folder) {
      this.lastSyncAt = null;
      this.write('sync.last', '');
      this.write('sync.base', '{}');
    }
  }

  markSynced(at: number, base: Record<string, string>): void {
    this.lastSyncAt = at;
    this.write('sync.last', String(at));
    this.write('sync.base', JSON.stringify(base));
  }

  /** Each trip's fingerprint as of the last sync (src/share/sync-merge.ts). */
  syncBase(): Record<string, string> {
    try {
      const base = JSON.parse(this.read('sync.base') ?? '{}') as unknown;
      return base && typeof base === 'object' && !Array.isArray(base) ? (base as Record<string, string>) : {};
    } catch {
      return {};
    }
  }

  private read(key: string): string | null {
    try {
      return this.store?.getItem(PREFIX + key) ?? null;
    } catch {
      return null;
    }
  }

  private write(key: string, value: string): void {
    try {
      this.store?.setItem(PREFIX + key, value);
    } catch (err) {
      console.warn('Could not save a setting', err);
    }
  }
}

function numberOr(text: string | null): number | null {
  const n = Number(text ?? '');
  return text && Number.isFinite(n) && n > 0 ? n : null;
}

/**
 * Put the page on the chosen side now, and keep it there: following the
 * system while the theme is SYSTEM. Call once, before the app mounts.
 */
export function applyTheme(settings: Settings, win: Window = window): void {
  const query = win.matchMedia('(prefers-color-scheme: dark)');
  settings.systemDark = query.matches;
  query.addEventListener('change', () => (settings.systemDark = query.matches));
  const paint = () => {
    const mode = settings.mode;
    win.document.documentElement.setAttribute('data-theme', mode);
    // Browser chrome takes the solid page colour from the tokens, as in the other apps (style.md §14).
    const surface = win
      .getComputedStyle(win.document.documentElement)
      .getPropertyValue('--bg-grad-mid')
      .trim();
    if (surface) win.document.querySelector('meta[name="theme-color"]')?.setAttribute('content', surface);
    setNativeTheme(mode);
  };
  paint();
  $effect.root(() => {
    $effect(paint);
  });
}
