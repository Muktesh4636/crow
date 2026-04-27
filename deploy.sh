#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────
#  Kokoroko mweb deploy
#  Usage: ./deploy.sh
# ─────────────────────────────────────────────────────────
set -euo pipefail

HOST="root@72.61.148.117"
PASS='To1#NXG(ihxodLqmDUU6'
REMOTE_DIR="/var/www/fight-mweb"
LOCAL_DIR="$(cd "$(dirname "$0")/mweb" && pwd)"

echo "▶ Deploying mweb → ${HOST}:${REMOTE_DIR}"

sshpass -p "$PASS" rsync -avz --delete \
  -e "ssh -o StrictHostKeyChecking=no" \
  "$LOCAL_DIR/" \
  "${HOST}:${REMOTE_DIR}/"

echo "✓ Deploy complete → https://fight.pravoo.in"
