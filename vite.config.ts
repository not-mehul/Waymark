import { readFileSync } from 'node:fs';
import { svelte } from '@sveltejs/vite-plugin-svelte';
import { defineConfig, type Plugin } from 'vite';
import { viteSingleFile } from 'vite-plugin-singlefile';

/**
 * The station directory and the gazetteer are 2 MB of text, too much to
 * inline into the page, so they ship beside it as `catalog/<name>` and are
 * fetched once at start (src/catalog/load.ts). They live in data/catalog;
 * test/helpers.ts reads the same folder.
 */
const CATALOG_SOURCE = 'data/catalog';
const CATALOG_FILES = ['airports.txt', 'cities.txt'];

function catalog(): Plugin {
  const read = (name: string) => readFileSync(new URL(`${CATALOG_SOURCE}/${name}`, import.meta.url));
  return {
    name: 'waymark-catalog',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const name = req.url?.split('?')[0]?.replace(/^\/catalog\//, '');
        if (!name || !CATALOG_FILES.includes(name)) return next();
        res.setHeader('Content-Type', 'text/plain; charset=utf-8');
        res.end(read(name));
      });
    },
    generateBundle() {
      for (const name of CATALOG_FILES) {
        this.emitFile({ type: 'asset', fileName: `catalog/${name}`, source: read(name) });
      }
    }
  };
}

// One self-contained index.html, as in BookShelf: the Android app gets the
// same file from `cap sync`, and a service worker can precache just that.
const { version } = JSON.parse(readFileSync(new URL('package.json', import.meta.url), 'utf8')) as {
  version: string;
};

export default defineConfig({
  define: { __APP_VERSION__: JSON.stringify(version) },
  plugins: [svelte(), viteSingleFile(), catalog()],
  build: {
    // Keep in sync with android.minWebViewVersion in capacitor.config.ts.
    target: ['chrome111', 'safari16']
  }
});
