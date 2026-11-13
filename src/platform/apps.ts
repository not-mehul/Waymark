// Handing something to a sibling app (style.md §14.1): its link when it is
// installed, otherwise the share sheet with plain text it can still read.

import { callNative, isNativeShell } from './native';

export type Handoff = 'opened' | 'shared' | 'copied' | 'failed';

export async function handOff(url: string, fallbackText: string): Promise<Handoff> {
  if (isNativeShell()) {
    try {
      if (await callNative<boolean>('canOpenUrl', { url })) {
        if (await callNative<boolean>('openUrl', { url })) return 'opened';
      }
    } catch (err) {
      console.warn('Opening a sibling app', err);
    }
  }
  if (typeof navigator.share === 'function') {
    try {
      await navigator.share({ text: fallbackText });
      return 'shared';
    } catch (err) {
      if (err instanceof DOMException && err.name === 'AbortError') return 'failed';
    }
  }
  try {
    await navigator.clipboard.writeText(fallbackText);
    return 'copied';
  } catch {
    return 'failed';
  }
}
