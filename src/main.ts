// Waymark (Svelte) — entry point. The theme goes on before anything mounts,
// so the first paint is already on the traveler's side of Dusk and Dawn.

import { mount } from 'svelte';
import App from './components/App.svelte';
import { installNativeBridge } from './platform/native';
import { settings } from './state/app';
import { applyTheme } from './state/settings.svelte';

installNativeBridge();
applyTheme(settings);
mount(App, { target: document.getElementById('app')! });
