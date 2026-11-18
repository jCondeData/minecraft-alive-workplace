#!/bin/sh
# Builds the Watermill and saves it as a candidate (build/rebuild/watermill/).
# LIVE=1 saves the live drawing (structure/watermill.nbt) instead, once the critic says SHIP.
# MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
set -e
cd "$(dirname "$0")"
mkdir -p out
CAND=--candidate; [ -n "$LIVE" ] && CAND=
node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name watermill --data-version 3955 > /dev/null
python3 ../../rebuild.py watermill watermill $CAND --nbt out/watermill.nbt
