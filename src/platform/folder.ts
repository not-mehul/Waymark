// ============================================================
// A folder the person picks for sync (android/…/files/FolderSyncPlugin.java), reached
// through the system picker's lasting permission. Android only.
// ============================================================

import { registerPlugin } from '@capacitor/core';

export interface SyncFolderIO {
  pick(): Promise<{ uri?: string; name?: string }>;
  release(options: { uri: string }): Promise<void>;
  /** `modified` is when the file last changed; `data` is left out when it is unchanged since `since`. */
  read(options: { uri: string; file: string; since?: number }): Promise<{ data?: string; modified?: number }>;
  write(options: { uri: string; file: string; data: string }): Promise<{ modified?: number }>;
}

export const FolderSync = registerPlugin<SyncFolderIO>('FolderSync');
