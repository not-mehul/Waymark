// Turning a picked file into a ticket or document kept with a trip, and
// opening one again: a picture in Waymark's own viewer, anything else (a PDF
// e-ticket) in the app the phone reads it with.

import { MAX_DOCUMENT_BYTES } from '../db/documents';
import type { TripDocument } from '../domain/model';
import { callNative, isNativeShell } from '../platform/native';
import { documents } from './app';
import { ui } from './ui.svelte';

/** A phone photo is shrunk to this on its longest side: plenty for a barcode, a fraction of the bytes. */
const MAX_PICTURE_SIDE = 2400;

export function base64OfBytes(bytes: Uint8Array): string {
  let binary = '';
  for (let i = 0; i < bytes.length; i += 0x8000)
    binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  return btoa(binary);
}

export function bytesOfBase64(data: string): Uint8Array {
  const binary = atob(data);
  const out = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) out[i] = binary.charCodeAt(i);
  return out;
}

async function shrunkPicture(file: Blob): Promise<Blob> {
  if (typeof createImageBitmap !== 'function' || typeof OffscreenCanvas !== 'function') return file;
  try {
    const bitmap = await createImageBitmap(file);
    try {
      const scale = Math.min(1, MAX_PICTURE_SIDE / Math.max(bitmap.width, bitmap.height));
      if (scale === 1 && file.size < 1_500_000) return file;
      const canvas = new OffscreenCanvas(Math.round(bitmap.width * scale), Math.round(bitmap.height * scale));
      canvas.getContext('2d')!.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
      // PNG keeps a screenshot's barcode crisp; a photo is smaller as JPEG.
      const type = file.type === 'image/png' ? 'image/png' : 'image/jpeg';
      const out = await canvas.convertToBlob({ type, quality: 0.88 });
      return out.size < file.size ? out : file;
    } finally {
      bitmap.close();
    }
  } catch {
    return file;
  }
}

/** Keeps [file] with a trip (and a booking, when given). Null, with a toast, when it can't be kept. */
export async function keepFile(
  file: File,
  tripId: string,
  segmentId: string | null,
  code: string | null = null
): Promise<TripDocument | null> {
  const picture = file.type.startsWith('image/');
  const blob = picture ? await shrunkPicture(file) : file;
  if (blob.size > MAX_DOCUMENT_BYTES) {
    ui.notify('That file is too large to keep. Twelve megabytes is the most.', 'error');
    return null;
  }
  const mime = blob.type || file.type || 'application/octet-stream';
  const name = file.name || (picture ? 'Picture' : 'Document');
  const dataBase64 = base64OfBytes(new Uint8Array(await blob.arrayBuffer()));
  return documents.add({ tripId, segmentId, name, mime, dataBase64, code });
}

export const dataUrl = (d: Pick<TripDocument, 'mime' | 'dataBase64'>) =>
  `data:${d.mime};base64,${d.dataBase64}`;

/** Opens a document that isn't a picture in the app that reads it: a PDF viewer, mostly. */
export async function openElsewhere(d: TripDocument): Promise<void> {
  if (isNativeShell()) {
    const opened = await callNative<boolean>('openDocument', {
      name: d.name,
      mime: d.mime,
      data: d.dataBase64
    }).catch(() => false);
    if (!opened) ui.notify('No app on this phone opens that kind of file.', 'error');
    return;
  }
  const url = URL.createObjectURL(new Blob([bytesOfBase64(d.dataBase64) as BlobPart], { type: d.mime }));
  window.open(url, '_blank', 'noopener');
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

/** Full brightness and the screen kept on while a pass is shown; off again after. */
export function setBright(on: boolean): void {
  if (isNativeShell()) void callNative('setBright', { on }).catch(() => undefined);
}

/** Removes a document, with Undo in the toast. */
export async function removeDocument(d: TripDocument): Promise<void> {
  await documents.remove(d.id);
  ui.notify(`${d.name} removed.`, 'info', { label: 'Undo', run: () => void documents.restore(d) });
}
