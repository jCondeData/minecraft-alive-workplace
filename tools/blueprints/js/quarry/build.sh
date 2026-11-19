#!/bin/sh
# Builds the Quarry (the Headframe, one level) and saves it as a candidate (build/rebuild/quarry/).
# LIVE=1 saves the live drawing (structure/quarry.nbt) instead, once the critic says SHIP.
# MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
set -e
cd "$(dirname "$0")"
mkdir -p out
CAND=--candidate; [ -n "$LIVE" ] && CAND=
node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name quarry --data-version 3955 > /dev/null
python3 ../../rebuild.py quarry quarry $CAND --nbt out/quarry.nbt
