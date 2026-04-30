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

echo "▶ Deploying mweb → ${HOST}:${REMOTE_DIR}"

sshpass -p "$PASS" rsync -avz --delete \
  -e "ssh -o StrictHostKeyChecking=no" \
  "$LOCAL_DIR/" \
  "${HOST}:${REMOTE_DIR}/"

echo "✓ Deploy complete → ${APP_PUBLIC_URL}"
