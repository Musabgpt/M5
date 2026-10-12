#!/data/data/com.termux/files/usr/bin/bash
# Invoked only after M5 has obtained user-authorized Termux RUN_COMMAND.
# Idempotent: never overwrites user project files or force-resets the M5 source.
set -euo pipefail
DEST="$HOME/m5"
PROJECTS="$HOME/m5-workspace"
BRANCH="feat/cursor-grade-workbench"
URL="https://github.com/Musabgpt/M5.git"

if [ ! -f "$DEST/server/index.mjs" ]; then
  echo "M5_BOOTSTRAP_INCOMPLETE: source directory missing" >&2
  exit 2
fi
command -v node >/dev/null || { echo "M5_NODE_MISSING" >&2; exit 2; }
mkdir -p "$PROJECTS"
cd "$DEST"

# Only fast-forward the owned M5 engine when the tracked source is unmodified.
# User projects under ~/m5-workspace are always outside the source checkout.
if git rev-parse --is-inside-work-tree >/dev/null 2>&1 &&
   [ "$(git branch --show-current)" = "$BRANCH" ] &&
   [ -z "$(git status --porcelain --untracked-files=no)" ]; then
  git fetch --quiet --depth=80 origin "$BRANCH" >/dev/null 2>&1 &&
     git merge --ff-only --quiet FETCH_HEAD >/dev/null 2>&1 ||
     echo "M5_UPDATE_SKIPPED: could not fast-forward safely" >&2
else
  echo "M5_UPDATE_SKIPPED: source checkout has local changes or different branch" >&2
fi

# Rebuild the editor after a source update. Never modify user project files.
if [ -f web/editor-src.js ]; then
  if npm install --no-audit --no-fund --silent >/dev/null 2>&1 &&
     npm run build --silent >/dev/null 2>&1; then
    test -s web/editor.bundle.js || { echo "M5_EDITOR_BUNDLE_EMPTY" >&2; exit 3; }
  else
    echo "M5_EDITOR_FALLBACK: using the basic text editor" >&2
  fi
fi

# Strictly validate a responding *M5* server, not merely an open TCP port.
check_engine() {
  node -e '
  const http=require("node:http");
  const req=http.get("http://127.0.0.1:8765/health",{timeout:1600},r=>{
    let text="";r.on("data",c=>text+=c.toString().slice(0,200));
    r.on("end",()=>{try{const v=JSON.parse(text);process.exit(r.statusCode===200&&v.name==="M5"&&v.ready===true?0:1)}
     catch{process.exit(1)}});
  }); req.on("timeout",()=>req.destroy()); req.on("error",()=>process.exit(1));
  ' >/dev/null 2>&1
}

if ! check_engine; then
  # Port occupied by foreign service? Do NOT overwrite or pair with it.
  if node -e 'const net=require("net");const s=net.connect(8765,"127.0.0.1");s.on("connect",()=>{s.destroy();process.exit(0)});s.on("error",()=>process.exit(1));setTimeout(()=>process.exit(1),1500)' >/dev/null 2>&1; then
    echo "M5_PORT_CONFLICT: localhost:8765 is used by a non-M5 service" >&2
    exit 4
  fi
  nohup node "$DEST/server/index.mjs" > "$HOME/m5-server.log" 2>&1 </dev/null &
  for i in 1 2 3 4 5 6 7 8 9 10 11 12; do
    if check_engine; then break; fi
    sleep 1
  done
fi
check_engine || { echo "M5_ENGINE_START_FAILED: inspect ~/m5-server.log" >&2; exit 5; }
KEY_FILE="$HOME/.m5/session-token"
test -s "$KEY_FILE" || { echo "M5_PAIRING_TOKEN_MISSING" >&2; exit 6; }
key="$(cat "$KEY_FILE")"
[[ "$key" =~ ^[a-f0-9]{64}$ ]] || { echo "M5_PAIRING_TOKEN_INVALID" >&2; exit 6; }

# Only the authenticated, verified startup emits this single pairing receipt.
printf 'M5_PAIR=%s\n' "$key"
