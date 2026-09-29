#!/bin/bash
# Renders blueprints drawn by tools/blueprints/*.py to PNGs, from a few angles, on a grass lawn.
#   tools/blueprints/render/preview.sh [views] build_function...
#   tools/blueprints/render/preview.sh front,back starter_cottage "village_house('plains', kitchen)"
# views: front, back, left, right, top, low, side (default front,back). PNGs land in build/blueprint-renders.
# First run: (cd tools/blueprints/render && npm install) — Chromium comes from Playwright (PLAYWRIGHT_BROWSERS_PATH).
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../../.." && pwd)
OUT="$ROOT/build/blueprint-renders"
VIEWS=front,back
if [[ "$1" != *"("* && "$1" == *[a-z],* || "$1" =~ ^(front|back|left|right|top|low|side)$ ]]; then VIEWS=$1; shift; fi
mkdir -p "$OUT/nbt"
FILES=()
for f in "$@"; do
  NAME=$(echo "$f" | tr -c 'a-zA-Z0-9_\n' '_' | sed 's/_*$//')
  (cd "$HERE/.." && python3 -c "
from generate import *
b = ${f}() if '(' not in '''$f''' else $f
b.save('$OUT/nbt', '$NAME')
" >/dev/null)
  FILES+=("$OUT/nbt/$NAME.nbt")
done
cd "$HERE" && node shoot.mjs "$OUT" "${FILES[@]}" --views "$VIEWS"
