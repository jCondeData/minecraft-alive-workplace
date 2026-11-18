#!/bin/sh
# Builds the Glassblower's Workshop and saves it as a candidate (build/rebuild/glassblowers_workshop/).
# LIVE=1 saves the live drawing (structure/glassblowers_workshop.nbt) instead, once the critic says SHIP.
# MC_BUILDER = the minecraft-builder skill's folder (with node_modules).
set -e
cd "$(dirname "$0")"
mkdir -p out
CAND=--candidate; [ -n "$LIVE" ] && CAND=
node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name glassblowers_workshop --data-version 3955 > /dev/null
python3 ../../rebuild.py glassblowers_workshop glassblowers_workshop $CAND --nbt out/glassblowers-workshop.nbt
