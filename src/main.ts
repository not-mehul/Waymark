// Waymark (Svelte) — entry point. Follows the system's Dusk or Dawn until
// settings arrive in phase 2, then mounts the app.

import { mount } from 'svelte';
import App from './components/App.svelte';

// Browser chrome colour per theme (matches --bg-grad-top in tokens.css).
const THEME_COLOR = { dark: '#14110e', light: '#f3ece0' } as const;

function followSystemTheme(): void {
  const query = window.matchMedia('(prefers-color-scheme: light)');
  const apply = () => {
    const mode = query.matches ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', mode);
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', THEME_COLOR[mode]);
  };
  apply();
  query.addEventListener('change', apply);
}

followSystemTheme();
mount(App, { target: document.getElementById('app')! });
