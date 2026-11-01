import type { CapacitorConfig } from '@capacitor/cli';

// The Android project (android/) arrives with the native features in phase 5
// of the rewrite. Until then this only names the app.
const config: CapacitorConfig = {
  // The Kotlin app's id, so the new APK installs as an update over it.
  appId: 'com.waymark',
  appName: 'Waymark',
  webDir: 'dist',
  // WebView background before the page paints; matches the Dusk surface.
  backgroundColor: '#0f0d0b',
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
