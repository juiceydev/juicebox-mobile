#!/usr/bin/env bash
# Capture screenshots of the iOS app on a booted simulator.
#
#   ./iosApp/scripts/capture-screenshots.sh <Juicebox.app> <output-dir>
#
# Requires macOS with Xcode. The app is launched once per tab through
# SIMCTL_CHILD_JUICEBOX_START_TAB and JUICEBOX_LIQUID_GLASS, so the captures
# do not depend on tapping through the UI.
set -euo pipefail

APP="${1:-iosApp/build/Build/Products/Debug-iphonesimulator/Juicebox.app}"
OUT="${2:-screenshots}"
BUNDLE_ID="dev.juicey.juicebox"

if [[ ! -d "$APP" ]]; then
  echo "No app bundle at $APP" >&2
  exit 1
fi

mkdir -p "$OUT"
OUT="$(cd "$OUT" && pwd)"

# Newest iOS runtime and an iPhone that actually exists in it.
read -r RUNTIME DEVICE < <(python3 - <<'PY'
import json, subprocess

runtimes = json.loads(subprocess.run(
    ["xcrun", "simctl", "list", "runtimes", "-j"],
    capture_output=True, text=True, check=True).stdout)["runtimes"]

ios = [r for r in runtimes if r.get("platform") == "iOS" and r.get("isAvailable")]
ios.sort(key=lambda r: [int(p) for p in r["version"].split(".")], reverse=True)
runtime = ios[0]

devices = json.loads(subprocess.run(
    ["xcrun", "simctl", "list", "devicetypes", "-j"],
    capture_output=True, text=True, check=True).stdout)["devicetypes"]

preferred = ["iPhone 17 Pro", "iPhone 16 Pro", "iPhone 16", "iPhone 15 Pro"]
by_name = {d["name"]: d["identifier"] for d in devices}
device = next(by_name[name] for name in preferred if name in by_name)
print(runtime["identifier"], device)
PY
)

echo "Runtime: $RUNTIME"
echo "Device:  $DEVICE"

UDID="$(xcrun simctl create "Juicebox-Shots-$$" "$DEVICE" "$RUNTIME")"
cleanup() {
  xcrun simctl shutdown "$UDID" >/dev/null 2>&1 || true
  xcrun simctl delete "$UDID" >/dev/null 2>&1 || true
}
trap cleanup EXIT

xcrun simctl boot "$UDID"
xcrun simctl bootstatus "$UDID" -b >/dev/null

# A tidy status bar, so the captures look like product shots rather than CI.
xcrun simctl status_bar "$UDID" override \
  --time "9:41" \
  --batteryState charged --batteryLevel 100 \
  --wifiBars 3 --cellularBars 4

xcrun simctl install "$UDID" "$APP"

shoot() {
  local appearance="$1" tab="$2" glass="$3"
  local suffix=""
  [[ "$glass" == "0" ]] && suffix="-no-glass"
  local name="$appearance-$tab$suffix.png"

  xcrun simctl terminate "$UDID" "$BUNDLE_ID" >/dev/null 2>&1 || true
  xcrun simctl ui "$UDID" appearance "$appearance" >/dev/null

  SIMCTL_CHILD_JUICEBOX_START_TAB="$tab" \
  SIMCTL_CHILD_JUICEBOX_LIQUID_GLASS="$glass" \
    xcrun simctl launch "$UDID" "$BUNDLE_ID" >/dev/null

  # Give SwiftUI a beat to settle, plus the first library fetch.
  sleep 4
  xcrun simctl io "$UDID" screenshot --type=png "$OUT/$name" >/dev/null 2>&1
  echo "  $name"
}

for appearance in light dark; do
  for tab in upload files settings; do
    shoot "$appearance" "$tab" 1
  done
done

# The Liquid Glass toggle itself: same two screens with glass off.
for tab in upload settings; do
  shoot light "$tab" 0
done

xcrun simctl terminate "$UDID" "$BUNDLE_ID" >/dev/null 2>&1 || true

echo "Wrote $(ls -1 "$OUT" | wc -l | tr -d ' ') screenshots to $OUT"
