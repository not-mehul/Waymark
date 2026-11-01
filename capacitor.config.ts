import type { CapacitorConfig } from '@capacitor/cli';

// The Android shell (android/): Capacitor's WebView host for the page, and
// the Waymark plugin for what has to run with the app closed.
const config: CapacitorConfig = {
  // Waymark's id since its first release, which the Kotlin app had too, so
  // this app installed as an update over it.
  appId: 'com.waymark',
  appName: 'Waymark',
  webDir: 'dist',
  // WebView background before the page paints; matches the Dusk surface.
  backgroundColor: '#0f0d0b',
  server: {
    // Android keys a WebView's IndexedDB by origin, so this is where every
    // trip lives. Named outright, so a change of Capacitor's default can never
    // open an empty shelf.
    hostname: 'localhost',
    androidScheme: 'https'
  },
  android: {
    allowMixedContent: false,
    // Keep in sync with build.target in vite.config.ts.
    minWebViewVersion: 111
  },
  plugins: {
    SystemBars: {
      insetsHandling: 'css',
      initialViewportFitValueHint: 'cover',
      style: 'DEFAULT'
    }
  }
};

export default config;
