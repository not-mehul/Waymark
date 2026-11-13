// Keeping trips safe off this phone's database: a dated backup each week in
// Documents/Waymark/Backups, and, when a folder is picked, sync through that
// folder with whatever keeps it the same across phones (Syncthing, Drive,
// Nextcloud). Both run on the phone only; in a browser there is the manual
// backup in the shelf menu.

import { FolderSync, type SyncFolderIO } from '../platform/folder';
import { callNative, isNativeShell } from '../platform/native';
import { fingerprint, mergeTrips } from '../share/sync-merge';
import { archiveOf, bundlesOf, decodeArchive, encodeArchive } from '../share/trip-archive';
import { settings, trips } from './app';

const HOUR = 3_600_000;
/** A backup a week, with an hour of slack so it doesn't drift later each week. */
export const BACKUP_EVERY = 7 * 24 * HOUR - HOUR;
/** The newest backups kept in the folder. */
export const BACKUPS_KEPT = 5;
export const SYNC_FILE = 'waymark-sync.json';

export function backupDue(last: number | null, now: number): boolean {
  return last === null || now - last >= BACKUP_EVERY;
}

/** "waymark-backup-2026-11-13-0935.json": sorts by time, and never collides within a day. */
export function datedBackupName(at: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `waymark-backup-${at.getFullYear()}-${pad(at.getMonth() + 1)}-${pad(at.getDate())}-${pad(at.getHours())}${pad(at.getMinutes())}.json`;
}

/** Writes every trip to Documents/Waymark/Backups, keeping the newest five. Returns where it went. */
export async function backupToDocuments(at = Date.now()): Promise<string> {
  const now = new Date(at); // eslint-disable-line svelte/prefer-svelte-reactivity -- a timestamp, not state
  const text = encodeArchive(archiveOf(await trips.bundles(), now.getTime()));
  const where = await callNative<string>('writeBackup', {
    name: datedBackupName(now),
    text,
    keep: BACKUPS_KEPT
  });
  settings.markBackedUp(now.getTime());
  return where;
}

let backingUp = false;

/** On launch and on coming back to the app: a backup when a week has gone by and there is a trip to keep. */
export async function autoBackupIfDue(): Promise<void> {
  if (!isNativeShell() || backingUp || !backupDue(settings.lastBackupAt, Date.now())) return;
  backingUp = true;
  try {
    if ((await trips.trips()).length > 0) await backupToDocuments();
  } catch (err) {
    console.warn('The weekly backup could not be written', err);
  } finally {
    backingUp = false;
  }
}

// — Sync through a folder ——————————————————————————————————————————————

export type SyncResult = 'off' | 'synced' | 'conflicts' | 'failed';

const encoder = new TextEncoder();
const decoder = new TextDecoder();

function toBase64(text: string): string {
  const bytes = encoder.encode(text);
  let binary = '';
  for (let i = 0; i < bytes.length; i += 0x8000)
    binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  return btoa(binary);
}

function fromBase64(data: string): string {
  const binary = atob(data);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return decoder.decode(bytes);
}

export class FolderSyncState {
  busy = $state(false);
  /** When the folder's file last changed, as this phone last saw it: an unchanged file isn't read again. */
  private seen: number | undefined;
  private running: Promise<SyncResult> | null = null;

  constructor(private readonly io: SyncFolderIO = FolderSync) {}

  get on(): boolean {
    return settings.syncFolder !== null;
  }

  /** Reads and merges the folder's copy, then writes this one back. One sync at a time. */
  now(): Promise<SyncResult> {
    this.running ??= this.run(true).finally(() => (this.running = null));
    return this.running;
  }

  /** Writes this phone's copy, when Waymark is left. */
  push(): Promise<SyncResult> {
    return this.running ?? this.run(false);
  }

  private async run(read: boolean): Promise<SyncResult> {
    const folder = settings.syncFolder;
    if (!folder || !isNativeShell()) return 'off';
    this.busy = true;
    try {
      let local = await trips.bundles();
      let conflicts = 0;
      if (read) {
        const theirs = await this.io.read({ uri: folder.uri, file: SYNC_FILE, since: this.seen });
        if (theirs.data) {
          const merge = mergeTrips(
            local,
            bundlesOf(decodeArchive(fromBase64(theirs.data))),
            settings.syncBase()
          );
          if (merge.incoming.length > 0) await trips.importBundles(merge.incoming);
          for (const id of merge.removed) await trips.deleteTrip(id);
          conflicts = merge.conflicts.length;
          local = merge.merged;
        }
      }
      const { modified } = await this.io.write({
        uri: folder.uri,
        file: SYNC_FILE,
        data: toBase64(encodeArchive(archiveOf(local)))
      });
      this.seen = modified || undefined;
      // The folder now holds exactly this phone's trips: that is what the next sync compares against.
      settings.markSynced(Date.now(), Object.fromEntries(local.map((b) => [b.trip.id, fingerprint(b)])));
      return conflicts > 0 ? 'conflicts' : 'synced';
    } catch (err) {
      console.error('Sync failed', err);
      return 'failed';
    } finally {
      this.busy = false;
    }
  }

  /** Opens the folder picker, then syncs straight away with the folder chosen. */
  async choose(): Promise<SyncResult> {
    const picked = await this.io.pick();
    if (!picked.uri) return 'off';
    const old = settings.syncFolder;
    if (old && old.uri !== picked.uri) await this.io.release({ uri: old.uri }).catch(() => undefined);
    this.seen = undefined;
    // A new folder starts from nothing known: everything in it is new, nothing is a deletion.
    settings.setSyncFolder(null);
    settings.setSyncFolder({ uri: picked.uri, name: picked.name ?? '' });
    return this.now();
  }

  /** Stops syncing. The file stays in the folder. */
  async stop(): Promise<void> {
    const old = settings.syncFolder;
    settings.setSyncFolder(null);
    this.seen = undefined;
    if (old) await this.io.release({ uri: old.uri }).catch(() => undefined);
  }
}

export const folderSync = new FolderSyncState();

/** Backups and sync on launch and on coming back; the sync file written on the way out. */
export function startKeeping(): void {
  if (!isNativeShell()) return;
  void autoBackupIfDue();
  if (folderSync.on) void folderSync.now();
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') {
      void autoBackupIfDue();
      if (folderSync.on) void folderSync.now();
    } else if (folderSync.on) void folderSync.push();
  });
}
