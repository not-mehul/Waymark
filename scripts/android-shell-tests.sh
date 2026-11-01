#!/usr/bin/env bash
# Runs on a booted emulator (see the shell-emulator job in
# .github/workflows/build.yml):
#   1. the upgrade test: the Kotlin app, then the Capacitor shell over it,
#   2. the cold start of each, which is also the shell's release smoke test.
#
#   scripts/android-shell-tests.sh <dir with the shell-apks artifact>
set -euo pipefail

DIR=${1:?usage: android-shell-tests.sh <apk dir>}
log() { echo "==> [$(date -u +%H:%M:%S)] $*"; }

# sys.boot_completed can flip before the package and activity services are
# usable, and a fresh image may still restart system_server once.
wait_for_system() {
  for _ in $(seq 1 90); do
    if timeout 20 adb shell pm path android >/dev/null 2>&1 && timeout 20 adb shell am get-current-user >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  echo "System services never became ready." >&2
  return 1
}

log "Waiting for system services"
timeout 300 adb wait-for-device
wait_for_system
sleep 10 # let a post-boot system_server restart happen before installing
wait_for_system

log "Upgrade test"
timeout 600 node "$(dirname "$0")/android-upgrade-test.mjs" "$DIR/kotlin/app-benchmarkRelease.apk" "$DIR/debug/app-debug.apk" || {
  echo "---- crash buffer ----" >&2
  adb logcat -d -b crash 2>/dev/null | tail -150 >&2 || true
  exit 1
}

log "Cold start"
"$(dirname "$0")/android-cold-start.sh" "$DIR/kotlin/app-benchmarkRelease.apk" "$DIR/release/app-release-ci.apk"
