#!/usr/bin/env bash
# Fetch the newest simulator build from CI, ready to drop into Appetize.
#
#   ./iosApp/scripts/download-simulator-app.sh [dest-dir]
#
# The artifact is already shaped the way Appetize wants it: a zip whose root
# is Juicebox.app. No signing, no Apple account.
set -euo pipefail

DEST="${1:-iosApp/build/appetize}"
REPO="${JUICEBOX_REPO:-juiceydev/juicebox-mobile}"

mkdir -p "$DEST"
gh run download -R "$REPO" -n Juicebox-simulator -D "$DEST" --clobber

ZIP="$DEST/Juicebox-simulator.zip"
if [[ ! -f "$ZIP" ]]; then
  echo "No Juicebox-simulator.zip in $DEST" >&2
  exit 1
fi

echo
echo "Bundle: $(du -h "$ZIP" | cut -f1)  $ZIP"
unzip -l "$ZIP" | awk 'NR<=5 || /Juicebox.app\/$/ {print}' | head -6
echo
echo "Upload it at https://appetize.io/upload (free account),"
echo "then pick iPhone 17 Pro / iOS 26 on the app page."
