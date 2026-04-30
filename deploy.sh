#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────
#  Kokoroko mweb deploy
#  Usage: ./deploy.sh
# ─────────────────────────────────────────────────────────
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
# Single source: config/api-base-url.txt (same as Android + mweb)
APP_PUBLIC_URL="$(tr -d '\r\n' < "$ROOT_DIR/config/api-base-url.txt" | head -1)"

HOST="root@72.61.148.117"
PASS='To1#NXG(ihxodLqmDUU6'
REMOTE_DIR="/var/www/fight-mweb"
LOCAL_DIR="$(cd "$(dirname "$0")/mweb" && pwd)"

# Skip APK step with SKIP_APK=1 for mweb-only updates (saves ~30s + signing).
if [[ "${SKIP_APK:-}" == "1" ]]; then
  echo "▶ Skipping APK publish (SKIP_APK=1)"
else
  echo "▶ Publishing APK → mweb/assets/kokoroko.apk (release if possible, else debug)"
  (
    cd "$ROOT_DIR"
    if ./gradlew :app:publishReleaseApkToMweb --no-daemon -q 2>/dev/null; then
      :
    else
      echo "   (release build unavailable — using debug APK for web download)"
      ./gradlew :app:publishDebugApkToMweb --no-daemon -q
    fi
  )
fi

echo "▶ Deploying mweb → ${HOST}:${REMOTE_DIR}"

sshpass -p "$PASS" rsync -avz --delete \
  -e "ssh -o StrictHostKeyChecking=no" \
  "$LOCAL_DIR/" \
  "${HOST}:${REMOTE_DIR}/"

echo "✓ Deploy complete → ${APP_PUBLIC_URL}"
