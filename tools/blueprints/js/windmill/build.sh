#!/bin/sh
# Builds the Windmill (Create only) and saves it as a candidate (build/rebuild/windmill/).
# LIVE=1 saves the live drawing (structure/windmill.nbt) instead, once the critic says SHIP.
# MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
set -e
cd "$(dirname "$0")"
mkdir -p out
CAND=--candidate; [ -n "$LIVE" ] && CAND=
node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name windmill --data-version 3955 > /dev/null
python3 ../../rebuild.py windmill windmill $CAND --nbt out/windmill.nbt
