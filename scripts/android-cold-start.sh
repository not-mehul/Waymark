#!/usr/bin/env bash
# Cold start of the Kotlin app and of the Capacitor shell, on the same
# emulator, the same way, one after the other (see .github/workflows/build.yml).
#
# Each app is installed fresh, opened once (so the Kotlin app's profile
# installer runs, and the shell's WebView is set up), compiled with
# speed-profile as an installed app would be, then cold-started ten times:
#
#   - "first frame" is Android's time to initial display (am start -W). For the
#     Kotlin app it is also the shelf, since it holds the splash until then.
#   - "shelf drawn" is Android's "Fully drawn", which the shell reports once
#     the page has drawn the shelf. It is the number to set against the
#     Kotlin app's first frame.
#
# Also the release smoke test: the shell must report the shelf drawn, and
# neither app may crash.
#
#   scripts/android-cold-start.sh <kotlin.apk> <shell-release.apk>
set -euo pipefail

KOTLIN=${1:?usage: android-cold-start.sh <kotlin.apk> <shell-release.apk>}
SHELL_APK=${2:?usage: android-cold-start.sh <kotlin.apk> <shell-release.apk>}
PKG=com.waymark
RUNS=10
log() { echo "==> [$(date -u +%H:%M:%S)] $*"; }

# "+1s234ms" or "+987ms" → milliseconds.
millis() { echo $(( $(sed -E 's/^\+//; s/^([0-9]+)s([0-9]+)ms$/\1*1000+10#\2/; s/ms$//' <<<"$1") )); }
median() { sort -n | awk '{ a[NR] = $1 } END { if (NR % 2) print a[(NR + 1) / 2]; else print int((a[NR / 2] + a[NR / 2 + 1]) / 2) }'; }

install() {
  adb uninstall "$PKG" >/dev/null 2>&1 || true
  for attempt in 1 2 3 4 5; do
    timeout 180 adb install "$1" && return 0
    echo "Install attempt $attempt failed; retrying…" >&2
    sleep 5
  done
  return 1
}

# measure <apk> <label> <wait for fully drawn: yes|no> → appends to $RESULTS
measure() {
  local apk=$1 label=$2 drawn=$3 first=() full=()
  log "$label: installing"
  install "$apk"
  adb shell am start -W -n "$PKG/.MainActivity" >/dev/null
  sleep 8
  adb shell am force-stop "$PKG"
  adb shell cmd package compile -f -m speed-profile "$PKG" >/dev/null
  for i in $(seq 1 "$RUNS"); do
    adb shell am force-stop "$PKG"
    sleep 2
    adb logcat -c
    local out total
    out=$(adb shell am start -W -n "$PKG/.MainActivity")
    total=$(sed -nE 's/^TotalTime: ([0-9]+).*/\1/p' <<<"$out" | tr -d '\r')
    [[ -n "$total" ]] && first+=("$total")
    if [[ $drawn == yes ]]; then
      local line=""
      for _ in $(seq 1 30); do
        line=$(adb logcat -d -s ActivityTaskManager:I | grep -oE "Fully drawn $PKG/\.MainActivity: \+[0-9]+(s[0-9]+)?ms" | tail -1 || true)
        [[ -n "$line" ]] && break
        sleep 0.5
      done
      if [[ -z "$line" ]]; then
        echo "$label never reported the shelf drawn on run $i." >&2
        adb logcat -d | tail -80 >&2
        exit 1
      fi
      full+=("$(millis "${line##*: }")")
    fi
    sleep 3
  done
  if adb logcat -d -b crash | grep -q "$PKG"; then
    echo "$label crashed." >&2
    adb logcat -d -b crash | tail -60 >&2
    exit 1
  fi
  local first_median full_median="—"
  first_median="$(printf '%s\n' "${first[@]}" | median) ms"
  [[ ${#full[@]} -gt 0 ]] && full_median="$(printf '%s\n' "${full[@]}" | median) ms"
  log "$label: first frame ${first[*]} → $first_median; shelf drawn ${full[*]:-—} → $full_median"
  RESULTS+=("| $label | $first_median | $full_median |")
}

RESULTS=()
measure "$KOTLIN" "Kotlin app" no
measure "$SHELL_APK" "Svelte in Capacitor" yes

{
  echo "### Cold start, median of $RUNS on this emulator"
  echo
  echo "| App | First frame | Shelf drawn |"
  echo "|---|---|---|"
  printf '%s\n' "${RESULTS[@]}"
  echo
  echo "The Kotlin app draws its shelf in its first frame, so its first frame is the number to compare with the shell's shelf drawn."
} | tee -a "${GITHUB_STEP_SUMMARY:-/dev/null}"
