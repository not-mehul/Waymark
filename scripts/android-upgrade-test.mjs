// Upgrade test, run on a booted emulator (see .github/workflows/build.yml).
//
// Installs the Kotlin app, loads its worked example (a trip of twelve
// bookings and two travellers), and lets it write the handover file it keeps
// for the move. Then installs the Capacitor shell over it, as the update a
// phone would get, and checks the trip came across whole and the file is gone.
// Both APKs must be signed with the same key (CI builds both with the
// runner's debug key).
//
//   node scripts/android-upgrade-test.mjs <kotlin.apk> <shell-debug.apk>

import { _android as android } from '@playwright/test';

const [kotlinApk, shellApk] = process.argv.slice(2);
if (!kotlinApk || !shellApk) {
  console.error('usage: android-upgrade-test.mjs <kotlin.apk> <shell-debug.apk>');
  process.exit(2);
}

const PKG = 'com.waymark';
const TRIP = 'London & Paris';
const log = (msg) => console.log(`==> ${msg}`);
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

const [device] = await android.devices();
if (!device) throw new Error('No Android device or emulator connected');
device.setDefaultTimeout(120_000);

try {
  log('Installing the Kotlin app');
  await device.shell(`pm uninstall ${PKG}`);
  await device.installApk(kotlinApk);
  await device.shell(`am start -W -n ${PKG}/.MainActivity`);

  log('Loading its worked example');
  await device.tap({ text: 'Load the worked example' });
  await device.wait({ textContains: TRIP });
  // The Kotlin app rewrites the handover file a second after the last change.
  await sleep(8_000);
  await device.shell(`am force-stop ${PKG}`);

  log('Installing the shell over it');
  await device.installApk(shellApk); // -r: replace, keeping app data
  await device.shell(`am start -W -n ${PKG}/.MainActivity`);
  const webview = await device.webView({ pkg: PKG });
  const page = await webview.page();
  await page.waitForSelector('#shelf-title', { timeout: 60_000 });

  const failures = [];
  const listed = await page
    .getByText(TRIP)
    .first()
    .waitFor({ timeout: 30_000 })
    .then(
      () => true,
      () => false
    );
  if (!listed) failures.push(`"${TRIP}" is not on the shelf`);

  // Counted in IndexedDB itself, once the page has finished the handover
  // (it removes the file only after every trip reads back whole).
  let found = null;
  for (let i = 0; i < 30 && !(found?.handover === null && found?.trips > 0); i++) {
    await sleep(1_000);
    found = await page.evaluate(async () => {
      const count = (db, store) =>
        new Promise((resolve, reject) => {
          const request = db.transaction(store).objectStore(store).count();
          request.onsuccess = () => resolve(request.result);
          request.onerror = () => reject(request.error);
        });
      const db = await new Promise((resolve, reject) => {
        const open = indexedDB.open('waymark');
        open.onsuccess = () => resolve(open.result);
        open.onerror = () => reject(open.error);
      });
      const handover = await window.Capacitor.nativePromise('Waymark', 'readHandover', {});
      const result = {
        native: window.Capacitor.isNativePlatform(),
        trips: await count(db, 'trips'),
        segments: await count(db, 'segments'),
        travelers: await count(db, 'travelers'),
        handover: handover.value ?? null
      };
      db.close();
      return result;
    });
  }
  log(`Found ${JSON.stringify({ ...found, handover: found?.handover === null ? null : 'still there' })}`);
  if (!found?.native) failures.push('the page is not running inside Capacitor');
  if (found?.trips !== 1) failures.push(`${found?.trips} trips, expected 1`);
  if (found?.segments !== 12) failures.push(`${found?.segments} bookings, expected 12`);
  if (found?.travelers !== 2) failures.push(`${found?.travelers} travellers, expected 2`);
  if (found?.handover !== null) failures.push('the handover file is still there');

  if (failures.length) {
    console.error(`The upgrade lost something:\n  - ${failures.join('\n  - ')}`);
    process.exitCode = 1;
  } else {
    log('The upgrade brought the trip across whole, and removed the handover file');
  }
} finally {
  await device.close();
}
