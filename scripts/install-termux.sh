#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
# Run inside official Termux, never as root.
if [ -z "$PREFIX" ] || [[ "$PREFIX" != *com.termux* ]]; then
  echo "ERROR: run this script inside Termux" >&2
  exit 2
fi
pkg install -y git nodejs-lts
DEST="$HOME/m5"
if [ ! -d "$DEST/.git" ]; then
  git clone --depth 1 --branch feat/cursor-grade-workbench https://github.com/Musabgpt/M5.git "$DEST"
else
  echo "Existing M5 checkout preserved. Update manually after reviewing changes."
fi
mkdir -p "$HOME/m5-workspace"
cd "$DEST"
if npm install --no-audit --no-fund && npm run build; then
  echo "CodeMirror editor installed for offline use"
else
  echo "CodeMirror unavailable; M5 retains its basic fallback editor"
fi
echo "Run: npm start"
echo "M5 binds only to 127.0.0.1:8765 and prints a local pairing URL."
