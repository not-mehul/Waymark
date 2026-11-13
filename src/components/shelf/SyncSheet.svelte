<script lang="ts">
  // Backups and sync: the weekly backup in Documents/Waymark/Backups, and a
  // folder that a sync app (Syncthing, Drive, Nextcloud) keeps the same
  // across phones. Waymark itself still uploads nothing.
  import { relative } from '../../domain/time-text';
  import { isNativeShell } from '../../platform/native';
  import { settings } from '../../state/app';
  import { backupToDocuments, folderSync, type SyncResult } from '../../state/keeping.svelte';
  import { ui } from '../../state/ui.svelte';
  import Icon from '../ui/Icon.svelte';
  import ModalHead from '../ui/ModalHead.svelte';

  const native = isNativeShell();
  let backing = $state(false);

  async function backUpNow() {
    backing = true;
    try {
      const where = await backupToDocuments();
      ui.notify(`Backed up to ${where}.`, 'success');
    } catch (err) {
      console.error('Backup', err);
      ui.notify('The backup could not be written to Documents.', 'error');
    } finally {
      backing = false;
    }
  }

  function report(result: SyncResult) {
    if (result === 'synced') ui.notify('Synced with the folder.', 'success');
    else if (result === 'conflicts')
      ui.notify('Synced. A trip changed on both phones kept this phone’s copy.', 'info');
    else if (result === 'failed') ui.notify('The folder could not be read or written.', 'error');
  }
</script>

<ModalHead eyebrow="Every trip" title="Backups and sync" />
{#if !native}
  <p class="t-body-small">
    In a browser, back up from the menu: the file goes wherever downloads go. The Android app also backs up on
    its own each week and can sync through a folder.
  </p>
{:else}
  <div class="panel facts">
    <div>
      <span class="t-field-label">Weekly backup</span>
      <span class="t-body-small">
        {settings.lastBackupAt ? `Last backed up ${relative(settings.lastBackupAt)}` : 'Not backed up yet'}
      </span>
      <span class="t-hint"
        >A dated file in Documents/Waymark/Backups, the newest five kept. It stays if Waymark is uninstalled.</span
      >
    </div>
    <button type="button" class="btn btn-secondary" disabled={backing} onclick={backUpNow}>
      <Icon name="save" />
      {backing ? 'Backing up…' : 'Back up now'}
    </button>
  </div>

  <div class="panel facts">
    <div>
      <span class="t-field-label">Sync through a folder</span>
      {#if settings.syncFolder}
        <span class="t-body-small">{settings.syncFolder.name || 'The folder you picked'}</span>
        <span class="t-hint">
          {settings.lastSyncAt ? `Last synced ${relative(settings.lastSyncAt)}` : 'Not synced yet'}. Waymark
          syncs when it opens and when you leave it.
        </span>
      {:else}
        <span class="t-hint">
          Pick a folder that Syncthing, Drive or Nextcloud keeps the same on your other phone. Waymark keeps
          one file there, waymark-sync.json, and merges it trip by trip.
        </span>
      {/if}
    </div>
    <div class="btn-row">
      {#if settings.syncFolder}
        <button type="button" class="btn btn-muted" onclick={() => folderSync.stop()}>Stop syncing</button>
        <button
          type="button"
          class="btn btn-secondary"
          disabled={folderSync.busy}
          onclick={async () => report(await folderSync.now())}
        >
          <Icon name="refresh" />
          {folderSync.busy ? 'Syncing…' : 'Sync now'}
        </button>
      {:else}
        <button
          type="button"
          class="btn btn-secondary"
          onclick={async () => report(await folderSync.choose())}
        >
          <Icon name="refresh" /> Choose a folder
        </button>
      {/if}
    </div>
  </div>
{/if}
