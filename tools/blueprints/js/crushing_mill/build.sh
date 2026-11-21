#!/bin/sh
# Builds the Crushing Mill (Create only) and saves it as a candidate (build/rebuild/crushing_mill/).
# LIVE=1 saves the live drawing instead, once the critic says SHIP. CUT=<y> writes a cutaway .nbt only.
# MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
set -e
cd "$(dirname "$0")"
mkdir -p out
if [ -n "$CUT" ]; then
  node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name "crushing_mill_cut$CUT" --data-version 3955 > /dev/null
  exit 0
fi
CAND=--candidate; [ -n "$LIVE" ] && CAND=
node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name crushing_mill --data-version 3955 > /dev/null
python3 ../../rebuild.py crushing_mill crushing_mill $CAND --nbt out/crushing-mill.nbt
