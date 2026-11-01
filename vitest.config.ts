import { svelte } from '@sveltejs/vite-plugin-svelte';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  // Compiles the runes in src/**/*.svelte.ts; no component is rendered.
  plugins: [svelte()],
  test: {
    environment: 'node',
    include: ['test/**/*.test.ts'],
    setupFiles: ['test/setup.ts'],
    // The domain tests pin dates and zones; none of them may depend on where
    // the machine running them happens to be.
    env: { TZ: 'UTC' }
  }
});
