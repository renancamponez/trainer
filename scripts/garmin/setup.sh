#!/bin/bash
# One-time Garmin sign-in for the readiness sync (runs on GitHub Actions afterwards,
# so your Mac never needs to be on).   bash scripts/garmin/setup.sh
# Copies the session to your clipboard for the GARMIN_TOKENS GitHub secret.
set -e
VENV="$HOME/.config/sub130/venv"
if [ ! -x "$VENV/bin/python" ]; then
  mkdir -p "$(dirname "$VENV")"
  python3 -m venv "$VENV"
  "$VENV/bin/pip" install -q --upgrade pip garth pymongo
fi
"$VENV/bin/python" "$(cd "$(dirname "$0")" && pwd)/garmin_sync.py" --login
