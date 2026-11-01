// Client for the native Android shell. The Waymark plugin, and the features
// it carries (reminders, the widget, the share sheet, the camera), arrive in
// phase 5 of the rewrite; until then this is the part every screen needs: the
// Back gesture, the system-bar colours, and knowing whether there is a shell.
//
// Everything here is a no-op in a plain browser, so the same build runs in
// both places. Call installNativeBridge() once, before the app mounts.

import { Capacitor, registerPlugin, SystemBars, SystemBarsStyle } from '@capacitor/core';

/** Event payloads are JSON from the shell; handlers check what they use. */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type EventHandler = (data: any) => void;

/** The calls this module makes on the shell (injectable for tests). */
export interface NativeShell {
  call: (method: string, params: Record<string, unknown>) => Promise<Record<string, unknown>>;
  on: (event: string, handler: EventHandler) => void;
  setBarStyle: (mode: 'light' | 'dark') => void;
}

type PluginMethods = Record<string, (params: Record<string, unknown>) => Promise<Record<string, unknown>>>;
interface WaymarkPlugin {
  addListener: (event: string, handler: EventHandler) => Promise<unknown>;
}

function capacitorShell(): NativeShell | null {
  if (!Capacitor.isNativePlatform()) return null;
  const plugin = registerPlugin<WaymarkPlugin>('Waymark');
  const methods = plugin as unknown as PluginMethods;
  return {
    call: (method, params) => methods[method]!(params),
    on: (event, handler) => void plugin.addListener(event, handler),
    setBarStyle: (mode) =>
      void SystemBars.setStyle({
        style: mode === 'dark' ? SystemBarsStyle.Dark : SystemBarsStyle.Light
      }).catch((err: unknown) => console.warn('Could not set the system bar style', err))
  };
}

let shell: NativeShell | null = null;
/** Back handlers, innermost last (a modal over a screen over the shelf). */
const backStack: (() => void)[] = [];

export function isNativeShell(): boolean {
  return shell !== null;
}

/** Returns true when running inside the shell. */
export function installNativeBridge(native: NativeShell | null = capacitorShell()): boolean {
  if (shell) return true;
  if (!native) return false;
  shell = native;
  native.on('back', () => backStack.at(-1)?.());
  return true;
}

/** Call a native method and await its result. Rejects outside the shell. */
export async function callNative<T = unknown>(
  method: string,
  params: Record<string, unknown> = {}
): Promise<T> {
  if (!shell) throw new Error('Not running in the Android app');
  const result = await shell.call(method, params);
  return (result.value ?? null) as T;
}

/** Handle an event pushed by the shell. Events that arrived first are delivered now. */
export function onNativeEvent(name: string, handler: EventHandler): void {
  shell?.on(name, handler);
}

/** Keep the system-bar icon colours in step with Dusk and Dawn. */
export function setNativeTheme(mode: 'light' | 'dark'): void {
  shell?.setBarStyle(mode);
}

function setBackHandler(enabled: boolean): void {
  shell?.call('setBackHandler', { enabled }).catch((err: unknown) => console.warn('Back handler', err));
}

/**
 * Claim the Back gesture (and, in a browser, Escape is the dialog's own) for
 * `handler`. Handlers stack: Back goes to the most recent one, and the system
 * gets Back again once all are released. Returns the release function.
 */
export function pushBackHandler(handler: () => void): () => void {
  backStack.push(handler);
  setBackHandler(true);
  let released = false;
  return () => {
    if (released) return;
    released = true;
    const i = backStack.lastIndexOf(handler);
    if (i >= 0) backStack.splice(i, 1);
    setBackHandler(backStack.length > 0);
  };
}
